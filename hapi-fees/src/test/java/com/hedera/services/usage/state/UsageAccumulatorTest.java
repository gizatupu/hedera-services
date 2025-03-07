package com.hedera.services.usage.state;

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
import com.hedera.services.usage.BaseTransactionMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ACCOUNT_AMT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RECEIPT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_BODY_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_RECORD_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.HRS_DIVISOR;
import static com.hederahashgraph.fee.FeeBuilder.INT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.RECEIPT_STORAGE_TIME_SEC;
import static org.junit.jupiter.api.Assertions.*;

class UsageAccumulatorTest {
	final private int memoBytes = 100;
	final private int numTransfers = 2;
	final private SigUsage sigUsage = new SigUsage(2, 101, 1);

	final private long baseBpr = INT_SIZE;
	final private long baseVpt = sigUsage.numSigs();
	final private long baseBpt = BASIC_TX_BODY_SIZE + memoBytes + sigUsage.sigsSize();
	final private long baseRbs = RECEIPT_STORAGE_TIME_SEC *
			(BASIC_TX_RECORD_SIZE + memoBytes + BASIC_ACCOUNT_AMT_SIZE * numTransfers);
	final private long baseNetworkRbs = RECEIPT_STORAGE_TIME_SEC *
			BASIC_RECEIPT_SIZE;

	private UsageAccumulator subject = new UsageAccumulator();

	@BeforeEach
	void setUp() {
	}

	@Test
	void understandsNetworkPartitioning() {
		// when:
		subject.addBpt(1);
		subject.addVpt(4);
		subject.addNetworkRbs(8 * HRS_DIVISOR);

		// then:
		assertEquals(1, subject.getUniversalBpt());
		assertEquals(4, subject.getNetworkVpt());
		assertEquals(8, subject.getNetworkRbh());
	}

	@Test
	void understandsNodePartitioning() {
		// when:
		subject.resetForTransaction(new BaseTransactionMeta(memoBytes, numTransfers), sigUsage);
		subject.addBpt(1);
		subject.addBpr(2);
		subject.addSbpr(3);

		// then:
		assertEquals(baseBpt + 1, subject.getUniversalBpt());
		assertEquals(baseBpr + 2, subject.getNodeBpr());
		assertEquals(3, subject.getNodeSbpr());
		assertEquals(sigUsage.numPayerKeys(), subject.getNodeVpt());
	}

	@Test
	void understandsServicePartitioning() {
		// when:
		subject.addRbs(6 * HRS_DIVISOR);
		subject.addSbs(7 * HRS_DIVISOR);

		// then:
		assertEquals(6, subject.getServiceRbh());
		assertEquals(7, subject.getServiceSbh());
	}


	@Test
	void resetWorksForTxn() {
		// given:
		subject.addSbpr(3);
		subject.addGas(5);
		subject.addSbs(7);

		// when:
		subject.resetForTransaction(new BaseTransactionMeta(memoBytes, numTransfers), sigUsage);

		// then:
		assertEquals(baseBpr, subject.getBpr());
		assertEquals(baseVpt, subject.getVpt());
		assertEquals(baseBpt, subject.getBpt()); assertEquals(baseRbs, subject.getRbs());
		assertEquals(baseNetworkRbs, subject.getNetworkRbs());
		assertEquals(sigUsage.numPayerKeys(), subject.getNumPayerKeys());
		// and:
		assertEquals(0, subject.getSbpr());
		assertEquals(0, subject.getGas());
		assertEquals(0, subject.getSbs()); }

	@Test
	void addersWork() {
		// given:
		subject.addBpt(1);
		subject.addBpr(2);
		subject.addSbpr(3);
		subject.addVpt(4);
		subject.addGas(5);
		subject.addRbs(6);
		subject.addSbs(7);
		subject.addNetworkRbs(8);

		// expect:
		assertEquals(1, subject.getBpt());
		assertEquals(2, subject.getBpr());
		assertEquals(3, subject.getSbpr());
		assertEquals(4, subject.getVpt());
		assertEquals(5, subject.getGas());
		assertEquals(6, subject.getRbs());
		assertEquals(7, subject.getSbs());
		assertEquals(8, subject.getNetworkRbs());
	}

	@Test
	void toStringWorks() {
		final var desired = "UsageAccumulator{universalBpt=1, networkVpt=4, networkRbh=1, nodeBpr=2, nodeSbpr=3, " +
				"nodeVpt=0, serviceSbh=1, serviceRbh=1}";

		// given:
		subject.addBpt(1);
		subject.addBpr(2);
		subject.addSbpr(3);
		subject.addVpt(4);
		subject.addGas(5);
		subject.addRbs(6);
		subject.addSbs(7);
		subject.addNetworkRbs(8);

		// expect:
		assertEquals(desired, subject.toString());
	}
}
