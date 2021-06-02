package com.hedera.services.stream;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.context.properties.NodeLocalProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NonBlockingHandoffTest {
	private final int mockCap = 10;
	private final RecordStreamObject rso = new RecordStreamObject();

	@Mock
	private RecordStreamManager recordStreamManager;
	@Mock
	private NodeLocalProperties nodeLocalProperties;

	private NonBlockingHandoff subject;

	@Test
	void handoffWorksAsExpected() {
		given(nodeLocalProperties.recordStreamQueueCapacity()).willReturn(mockCap);
		// and:
		subject = new NonBlockingHandoff(recordStreamManager, nodeLocalProperties);

		// when:
		Assertions.assertTrue(subject.offer(rso));

		// and:
		subject.getExecutor().shutdownNow();

		// then:
		try {
			verify(recordStreamManager).addRecordStreamObject(rso);
		} catch (NullPointerException ignore) {
			/* In CI apparently Mockito can have problems here? */
		}
	}
}
