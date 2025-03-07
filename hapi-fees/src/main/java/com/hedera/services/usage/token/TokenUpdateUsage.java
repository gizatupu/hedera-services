package com.hedera.services.usage.token;

/*-
 * ‌
 * Hedera Services API Fees
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FeeBuilder;

import java.util.Optional;

import static com.hedera.services.usage.EstimatorUtils.MAX_ENTITY_LIFETIME;
import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;

public class TokenUpdateUsage extends TokenTxnUsage<TokenUpdateUsage> {
	private int currentMemoLen;
	private int currentNameLen;
	private int currentSymbolLen;
	private long currentExpiry;
	private long currentMutableRb = 0;
	private boolean currentlyUsingAutoRenew = false;

	private TokenUpdateUsage(TransactionBody tokenUpdateOp, TxnUsageEstimator usageEstimator) {
		super(tokenUpdateOp, usageEstimator);
	}

	public static TokenUpdateUsage newEstimate(TransactionBody tokenUpdateOp, SigUsage sigUsage) {
		return new TokenUpdateUsage(tokenUpdateOp, estimatorFactory.get(sigUsage, tokenUpdateOp, ESTIMATOR_UTILS));
	}

	@Override
	TokenUpdateUsage self() {
		return this;
	}

	public TokenUpdateUsage givenCurrentAdminKey(Optional<Key> adminKey) {
		adminKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateCurrentRb);
		return this;
	}

	public TokenUpdateUsage givenCurrentWipeKey(Optional<Key> wipeKey) {
		wipeKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateCurrentRb);
		return this;
	}

	public TokenUpdateUsage givenCurrentSupplyKey(Optional<Key> supplyKey) {
		supplyKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateCurrentRb);
		return this;
	}

	public TokenUpdateUsage givenCurrentFreezeKey(Optional<Key> freezeKey) {
		freezeKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateCurrentRb);
		return this;
	}

	public TokenUpdateUsage givenCurrentKycKey(Optional<Key> kycKey) {
		kycKey.map(FeeBuilder::getAccountKeyStorageSize).ifPresent(this::updateCurrentRb);
		return this;
	}

	public TokenUpdateUsage givenCurrentMemo(String memo) {
		currentMemoLen = memo.length();
		updateCurrentRb(currentMemoLen);
		return this;
	}

	public TokenUpdateUsage givenCurrentName(String name) {
		currentNameLen = name.length();
		updateCurrentRb(currentNameLen);
		return this;
	}

	public TokenUpdateUsage givenCurrentSymbol(String symbol) {
		currentSymbolLen = symbol.length();
		updateCurrentRb(currentSymbolLen);
		return this;
	}

	public TokenUpdateUsage givenCurrentlyUsingAutoRenewAccount() {
		currentlyUsingAutoRenew = true;
		updateCurrentRb(BASIC_ENTITY_ID_SIZE);
		return this;
	}

	public TokenUpdateUsage givenCurrentExpiry(long expiry) {
		this.currentExpiry = expiry;
		return this;
	}

	public FeeData get() {
		var op = this.op.getTokenUpdate();

		long newMutableRb = 0;
		newMutableRb += keySizeIfPresent(
				op, TokenUpdateTransactionBody::hasKycKey, TokenUpdateTransactionBody::getKycKey);
		newMutableRb += keySizeIfPresent(
				op, TokenUpdateTransactionBody::hasWipeKey, TokenUpdateTransactionBody::getWipeKey);
		newMutableRb += keySizeIfPresent(
				op, TokenUpdateTransactionBody::hasAdminKey, TokenUpdateTransactionBody::getAdminKey);
		newMutableRb += keySizeIfPresent(
				op, TokenUpdateTransactionBody::hasSupplyKey, TokenUpdateTransactionBody::getSupplyKey);
		newMutableRb += keySizeIfPresent(
				op, TokenUpdateTransactionBody::hasFreezeKey, TokenUpdateTransactionBody::getFreezeKey);
		if (!removesAutoRenewAccount(op) && (currentlyUsingAutoRenew || op.hasAutoRenewAccount())) {
			newMutableRb += BASIC_ENTITY_ID_SIZE;
		}
		newMutableRb += op.hasMemo() ? op.getMemo().getValue().length() : currentMemoLen;
		newMutableRb += (op.getName().length() > 0) ? op.getName().length() : currentNameLen;
		newMutableRb += (op.getSymbol().length() > 0) ? op.getSymbol().length() : currentSymbolLen;
		long newLifetime = ESTIMATOR_UTILS.relativeLifetime(
				this.op,
				Math.max(op.getExpiry().getSeconds(), currentExpiry));
		newLifetime = Math.min(newLifetime, MAX_ENTITY_LIFETIME);
		long rbsDelta = Math.max(0, newLifetime * (newMutableRb - currentMutableRb));
		if (rbsDelta > 0) {
			usageEstimator.addRbs(rbsDelta);
		}

		long txnBytes = newMutableRb + BASIC_ENTITY_ID_SIZE + noRbImpactBytes(op);
		usageEstimator.addBpt(txnBytes);
		if (op.hasTreasury()) {
			addTokenTransfersRecordRb(1, 2);
		}

		return usageEstimator.get();
	}

	private int noRbImpactBytes(TokenUpdateTransactionBody op) {
		return ((op.getExpiry().getSeconds() > 0) ? AMOUNT_REPR_BYTES : 0) +
				((op.getAutoRenewPeriod().getSeconds() > 0) ? AMOUNT_REPR_BYTES : 0) +
				(op.hasTreasury() ? BASIC_ENTITY_ID_SIZE : 0) +
				(op.hasAutoRenewAccount() ? BASIC_ENTITY_ID_SIZE : 0);
	}

	private boolean removesAutoRenewAccount(TokenUpdateTransactionBody op) {
		return op.hasAutoRenewAccount() && op.getAutoRenewAccount().equals(AccountID.getDefaultInstance());
	}

	private void updateCurrentRb(long amount) {
		currentMutableRb += amount;
	}

}
