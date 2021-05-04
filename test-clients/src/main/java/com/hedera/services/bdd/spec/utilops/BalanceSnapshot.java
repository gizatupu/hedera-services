package com.hedera.services.bdd.spec.utilops;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountBalance;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.Optional;
import java.util.function.Function;

public class BalanceSnapshot extends UtilOp {
	private static final Logger log = LogManager.getLogger(BalanceSnapshot.class);

	private long retryDelayMs = 0L;
	private int maxAttempts = 1;
	private String account;
	private String snapshot;
	private Optional<Function<HapiApiSpec, String>> snapshotFn = Optional.empty();
	private Optional<String> payer = Optional.empty();

	public BalanceSnapshot(String account, String snapshot) {
		this.account = account;
		this.snapshot = snapshot;
	}

	public BalanceSnapshot(String account, Function<HapiApiSpec, String> fn) {
		this.account = account;
		this.snapshotFn = Optional.of(fn);
	}

	public BalanceSnapshot payingWith(String account) {
		payer = Optional.of(account);
		return this;
	}

	public BalanceSnapshot withRetries(int n, long retryDelayMs)	{
		maxAttempts = n + 1;
		this.retryDelayMs = retryDelayMs;
		return this;
	}

	@Override
	protected boolean submitOp(HapiApiSpec spec) {
		snapshot = snapshotFn.map(fn -> fn.apply(spec)).orElse(snapshot);

		Optional<Throwable> error = Optional.empty();
		HapiGetAccountBalance delegate = null;
		while (maxAttempts-- > 0) {
			delegate = QueryVerbs.getAccountBalance(account);
			payer.ifPresent(delegate::payingWith);
			error = delegate.execFor(spec);
			if (error.isPresent()) {
				log.info("Got {}, will retry balance snapshot for '{}' after {}ms...",
						delegate.getResponse().getCryptogetAccountBalance().getHeader().getNodeTransactionPrecheckCode(),
						account,
						retryDelayMs);
				delegate = null;
				try {
					Thread.sleep(retryDelayMs);
				} catch (InterruptedException ignore) {
					/* No-op */
				}
			} else {
				break;
			}
		}
		if (delegate == null) {
			log.error("Failed to take balance snapshot for '{}'!", account, error.get());
			return false;
		}
		long balance = delegate.getResponse().getCryptogetAccountBalance().getBalance();
		log.info("Snapshot '{}' of {} balance is {} tinybars", snapshot, account, balance);

		spec.registry().saveBalanceSnapshot(snapshot, balance);
		return false;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("snapshot", snapshot)
				.add("account", account)
				.toString();
	}
}
