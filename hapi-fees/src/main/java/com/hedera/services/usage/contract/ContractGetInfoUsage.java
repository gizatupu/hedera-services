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

import com.hedera.services.usage.QueryUsage;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;

import java.nio.charset.Charset;

import static com.hedera.services.usage.contract.entities.ContractEntitySizes.CONTRACT_ENTITY_SIZES;
import static com.hedera.services.usage.crypto.entities.CryptoEntitySizes.CRYPTO_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getAccountKeyStorageSize;

public class ContractGetInfoUsage extends QueryUsage {
	private ContractGetInfoUsage(Query query) {
		super(query.getContractGetInfo().getHeader().getResponseType());
		updateTb(BASIC_ENTITY_ID_SIZE);
		updateRb(CONTRACT_ENTITY_SIZES.fixedBytesInContractRepr());
	}

	public static ContractGetInfoUsage newEstimate(Query query) {
		return new ContractGetInfoUsage(query);
	}

	public ContractGetInfoUsage givenCurrentKey(Key key) {
		updateRb(getAccountKeyStorageSize(key));
		return this;
	}

	public ContractGetInfoUsage givenCurrentMemo(String memo) {
		updateRb(memo.getBytes(Charset.forName("UTF-8")).length);
		return this;
	}

	public ContractGetInfoUsage givenCurrentTokenAssocs(int count) {
		updateRb(count * CRYPTO_ENTITY_SIZES.bytesInTokenAssocRepr());
		return this;
	}
}
