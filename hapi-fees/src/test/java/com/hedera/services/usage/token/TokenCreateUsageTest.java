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

import com.hedera.services.test.IdUtils;
import com.hedera.services.test.KeyUtils;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.test.UsageUtils.A_USAGES_MATRIX;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

public class TokenCreateUsageTest {
	private long maxLifetime = 100 * 365 * 24 * 60 * 60L;

	private Key kycKey = KeyUtils.A_COMPLEX_KEY;
	private Key adminKey = KeyUtils.A_THRESHOLD_KEY;
	private Key freezeKey = KeyUtils.A_KEY_LIST;
	private Key supplyKey = KeyUtils.B_COMPLEX_KEY;
	private Key wipeKey = KeyUtils.C_COMPLEX_KEY;
	private long expiry = 2_345_678L;
	private long autoRenewPeriod = 1_234_567L;
	private long now = expiry - autoRenewPeriod;
	private String symbol = "ABCDEFGH";
	private String name = "WhyWhyWHy";
	private String memo = "Cellar door";
	private int numSigs = 3, sigSize = 100, numPayerKeys = 1;
	private SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);
	private AccountID autoRenewAccount = IdUtils.asAccount("0.0.75231");

	private TokenCreateTransactionBody op;
	private TransactionBody txn;

	private EstimatorFactory factory;
	private TxnUsageEstimator base;
	private TokenCreateUsage subject;

	@BeforeEach
	void setUp() throws Exception {
		base = mock(TxnUsageEstimator.class);
		given(base.get()).willReturn(A_USAGES_MATRIX);

		factory = mock(EstimatorFactory.class);
		given(factory.get(any(), any(), any())).willReturn(base);

		TxnUsage.estimatorFactory = factory;
	}

	@Test
	void createsExpectedDeltaForExpiryBased() {
		// setup:
		var expectedBytes = baseSize();

		givenExpiryBasedOp();
		// and:
		subject = TokenCreateUsage.newEstimate(txn, sigUsage);

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(expectedBytes);
		verify(base).addRbs(expectedBytes * autoRenewPeriod);
		verify(base).addNetworkRbs(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	@Test
	void createsExpectedCappedDeltaForExpiryBased() {
		// setup:
		var expectedBytes = baseSize();

		givenExpiryBasedOp(expiry + 2 * maxLifetime);
		// and:
		subject = TokenCreateUsage.newEstimate(txn, sigUsage);

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(expectedBytes);
		verify(base).addRbs(expectedBytes * maxLifetime);
		verify(base).addNetworkRbs(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	@Test
	void createsExpectedDeltaForAutoRenewBased() {
		// setup:
		var expectedBytes = baseSize() + BASIC_ENTITY_ID_SIZE;

		givenAutoRenewBasedOp();
		// and:
		subject = TokenCreateUsage.newEstimate(txn, sigUsage);

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(expectedBytes);
		verify(base).addRbs(expectedBytes * autoRenewPeriod);
		verify(base).addRbs(
				TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(1, 1) *
				USAGE_PROPERTIES.legacyReceiptStorageSecs());
		verify(base).addNetworkRbs(BASIC_ENTITY_ID_SIZE * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	private long baseSize() {
		return TOKEN_ENTITY_SIZES.totalBytesInTokenReprGiven(symbol, name)
				+ FeeBuilder.getAccountKeyStorageSize(kycKey)
				+ FeeBuilder.getAccountKeyStorageSize(adminKey)
				+ FeeBuilder.getAccountKeyStorageSize(wipeKey)
				+ FeeBuilder.getAccountKeyStorageSize(freezeKey)
				+ FeeBuilder.getAccountKeyStorageSize(supplyKey)
				+ memo.length();
	}

	private void givenExpiryBasedOp() {
		givenExpiryBasedOp(expiry);
	}

	private void givenExpiryBasedOp(long newExpiry) {
		op = TokenCreateTransactionBody.newBuilder()
				.setExpiry(Timestamp.newBuilder().setSeconds(newExpiry))
				.setSymbol(symbol)
				.setMemo(memo)
				.setName(name)
				.setKycKey(kycKey)
				.setAdminKey(adminKey)
				.setFreezeKey(freezeKey)
				.setSupplyKey(supplyKey)
				.setWipeKey(wipeKey)
				.build();
		setTxn();
	}

	private void givenAutoRenewBasedOp() {
		op = TokenCreateTransactionBody.newBuilder()
				.setAutoRenewAccount(autoRenewAccount)
				.setMemo(memo)
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
				.setSymbol(symbol)
				.setName(name)
				.setKycKey(kycKey)
				.setAdminKey(adminKey)
				.setFreezeKey(freezeKey)
				.setSupplyKey(supplyKey)
				.setWipeKey(wipeKey)
				.build();
		setTxn();
	}

	private void setTxn() {
		txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenCreation(op)
				.build();
	}
}
