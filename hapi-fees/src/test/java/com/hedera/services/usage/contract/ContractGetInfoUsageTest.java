package com.hedera.services.usage.contract;

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

import com.hedera.services.test.KeyUtils;
import com.hederahashgraph.api.proto.java.ContractGetInfoQuery;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import org.junit.jupiter.api.Test;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.usage.contract.entities.ContractEntitySizes.CONTRACT_ENTITY_SIZES;
import static com.hedera.services.usage.crypto.entities.CryptoEntitySizes.CRYPTO_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_QUERY_HEADER;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_QUERY_RES_HEADER;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractGetInfoUsageTest {
	Query query = Query.newBuilder().setContractGetInfo(ContractGetInfoQuery.getDefaultInstance()).build();

	int numTokenAssocs = 3;
	Key key = KeyUtils.A_CONTRACT_KEY;
	String memo = "Hey there!";

	ContractGetInfoUsage subject;

	@Test
	public void getsExpectedUsage() {
		// setup:
		long expectedTb = BASIC_QUERY_HEADER + BASIC_ENTITY_ID_SIZE;
		long expectedRb = BASIC_QUERY_RES_HEADER + numTokenAssocs * CRYPTO_ENTITY_SIZES.bytesInTokenAssocRepr()
				+ CONTRACT_ENTITY_SIZES.fixedBytesInContractRepr()
				+ getAccountKeyStorageSize(key)
				+ memo.length();
		// and:
		var usage = FeeComponents.newBuilder()
				.setBpt(expectedTb)
				.setBpr(expectedRb)
				.build();
		var expected = ESTIMATOR_UTILS.withDefaultQueryPartitioning(usage);

		// given:
		subject = ContractGetInfoUsage.newEstimate(query);

		// when:
		var actual = subject.givenCurrentKey(key)
				.givenCurrentMemo(memo)
				.givenCurrentTokenAssocs(numTokenAssocs)
				.get();

		// then:
		assertEquals(expected, actual);
	}
}
