package com.hedera.services.state.submerkle;

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

import com.google.protobuf.ByteString;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.serdes.DomainSerdesTest;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.MAX_ASSESSED_CUSTOM_FEES_CHANGES;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.MAX_INVOLVED_TOKENS;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.UNKNOWN_SUBMITTING_MEMBER;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

class ExpirableTxnRecordTest {
	long expiry = 1_234_567L;
	long submittingMember = 1L;

	DataInputStream din;

	DomainSerdes serdes;

	byte[] pretendHash = "not-really-a-hash".getBytes();

	TokenID tokenA = IdUtils.asToken("1.2.3");
	TokenID tokenB = IdUtils.asToken("1.2.4");
	AccountID sponsor = IdUtils.asAccount("1.2.5");
	AccountID beneficiary = IdUtils.asAccount("1.2.6");
	AccountID magician = IdUtils.asAccount("1.2.7");

	EntityId feeCollector = new EntityId(1,2,8);
	EntityId token = new EntityId(1,2,9);
	long units = 123L;

	TokenTransferList aTokenTransfers = TokenTransferList.newBuilder()
			.setToken(tokenA)
			.addAllTransfers(
					withAdjustments(sponsor, -1L, beneficiary, 1L, magician, 1000L).getAccountAmountsList())
			.build();
	TokenTransferList bTokenTransfers = TokenTransferList.newBuilder()
			.setToken(tokenB)
			.addAllTransfers(
					withAdjustments(sponsor, -1L, beneficiary, 1L, magician, 1000L).getAccountAmountsList())
			.build();
	ScheduleID scheduleID = IdUtils.asSchedule("5.6.7");
	AssessedCustomFee balanceChange = new AssessedCustomFee(feeCollector, token, units);

	ExpirableTxnRecord subject;

	@BeforeEach
	void setup() {
		subject = subjectRecordWithTokenTransfersAndScheduleRefCustomFees();

		din = mock(DataInputStream.class);

		serdes = mock(DomainSerdes.class);

		ExpirableTxnRecord.serdes = serdes;
	}

	private ExpirableTxnRecord subjectRecord() {
		var s = ExpirableTxnRecord.fromGprc(
				DomainSerdesTest.recordOne().asGrpc().toBuilder()
						.setTransactionHash(ByteString.copyFrom(pretendHash))
						.setContractCreateResult(DomainSerdesTest.recordTwo().getContractCallResult().toGrpc())
						.build());
		s.setExpiry(expiry);
		s.setSubmittingMember(submittingMember);
		return s;
	}

	private ExpirableTxnRecord subjectRecordWithTokenTransfers() {
		var s = ExpirableTxnRecord.fromGprc(
				DomainSerdesTest.recordOne().asGrpc().toBuilder()
						.setTransactionHash(ByteString.copyFrom(pretendHash))
						.setContractCreateResult(DomainSerdesTest.recordTwo().getContractCallResult().toGrpc())
						.addAllTokenTransferLists(List.of(aTokenTransfers, bTokenTransfers))
						.build());
		s.setExpiry(expiry);
		s.setSubmittingMember(submittingMember);
		return s;
	}

	private ExpirableTxnRecord subjectRecordWithTokenTransfersAndScheduleRefCustomFees() {
		var s = ExpirableTxnRecord.fromGprc(
				DomainSerdesTest.recordOne().asGrpc().toBuilder()
						.setTransactionHash(ByteString.copyFrom(pretendHash))
						.setContractCreateResult(DomainSerdesTest.recordTwo().getContractCallResult().toGrpc())
						.addAllTokenTransferLists(List.of(aTokenTransfers, bTokenTransfers))
						.setScheduleRef(scheduleID)
						.addAssessedCustomFees(balanceChange.toGrpc())
						.build());
		s.setExpiry(expiry);
		s.setSubmittingMember(submittingMember);
		return s;
	}


	@Test
	void hashableMethodsWork() {
		// given:
		Hash pretend = mock(Hash.class);

		// when:
		subject.setHash(pretend);

		// then:
		assertEquals(pretend, subject.getHash());
	}

	@Test
	void fastCopyableWorks() {
		// expect;
		assertTrue(subject.isImmutable());
		assertSame(subject, subject.copy());
		assertDoesNotThrow(subject::release);
	}

	@Test
	void v070DeserializeWorks() throws IOException {
		// setup:
		subject = subjectRecord();
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);

		given(serdes.readNullableSerializable(fin))
				.willReturn(subject.getReceipt())
				.willReturn(subject.getTxnId())
				.willReturn(subject.getHbarAdjustments())
				.willReturn(subject.getContractCallResult())
				.willReturn(subject.getContractCreateResult());
		given(fin.readByteArray(ExpirableTxnRecord.MAX_TXN_HASH_BYTES))
				.willReturn(subject.getTxnHash());
		given(serdes.readNullableInstant(fin))
				.willReturn(subject.getConsensusTimestamp());
		given(fin.readLong()).willReturn(subject.getFee())
				.willReturn(subject.getExpiry())
				.willReturn(subject.getSubmittingMember());
		given(serdes.readNullableString(fin, ExpirableTxnRecord.MAX_MEMO_BYTES))
				.willReturn(subject.getMemo());
		// and:
		var deserializedRecord = new ExpirableTxnRecord();

		// when:
		deserializedRecord.deserialize(fin, ExpirableTxnRecord.RELEASE_070_VERSION);

		// then:
		assertEquals(subject, deserializedRecord);
	}

	@Test
	void v080DeserializeWorks() throws IOException {
		// setup:
		subject = subjectRecordWithTokenTransfers();
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);

		given(serdes.readNullableSerializable(fin))
				.willReturn(subject.getReceipt())
				.willReturn(subject.getTxnId())
				.willReturn(subject.getHbarAdjustments())
				.willReturn(subject.getContractCallResult())
				.willReturn(subject.getContractCreateResult());
		given(fin.readSerializableList(MAX_INVOLVED_TOKENS))
				.willReturn(List.of(
						subject.getTokens().get(0),
						subject.getTokens().get(1)))
				.willReturn(List.of(
						subject.getTokenAdjustments().get(0),
						subject.getTokenAdjustments().get(1)));
		given(fin.readByteArray(ExpirableTxnRecord.MAX_TXN_HASH_BYTES))
				.willReturn(subject.getTxnHash());
		given(serdes.readNullableInstant(fin))
				.willReturn(subject.getConsensusTimestamp());
		given(fin.readLong()).willReturn(subject.getFee())
				.willReturn(subject.getExpiry())
				.willReturn(subject.getSubmittingMember());
		given(serdes.readNullableString(fin, ExpirableTxnRecord.MAX_MEMO_BYTES))
				.willReturn(subject.getMemo());
		// and:
		var deserializedRecord = new ExpirableTxnRecord();

		// when:
		deserializedRecord.deserialize(fin, ExpirableTxnRecord.RELEASE_080_VERSION);

		// then:
		assertEquals(subject, deserializedRecord);
	}

	@Test
	void v0120DeserializeWorks() throws IOException {
		// setup:
		subject = subjectRecordWithTokenTransfers();
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);

		given(serdes.readNullableSerializable(fin))
				.willReturn(subject.getReceipt())
				.willReturn(subject.getTxnId())
				.willReturn(subject.getHbarAdjustments())
				.willReturn(subject.getContractCallResult())
				.willReturn(subject.getContractCreateResult())
				.willReturn(subject.getScheduleRef());
		given(fin.readSerializableList(MAX_INVOLVED_TOKENS))
				.willReturn(List.of(
						subject.getTokens().get(0),
						subject.getTokens().get(1)))
				.willReturn(List.of(
						subject.getTokenAdjustments().get(0),
						subject.getTokenAdjustments().get(1)));
		given(fin.readByteArray(ExpirableTxnRecord.MAX_TXN_HASH_BYTES))
				.willReturn(subject.getTxnHash());
		given(serdes.readNullableInstant(fin))
				.willReturn(subject.getConsensusTimestamp());
		given(fin.readLong()).willReturn(subject.getFee())
				.willReturn(subject.getExpiry())
				.willReturn(subject.getSubmittingMember());
		given(serdes.readNullableString(fin, ExpirableTxnRecord.MAX_MEMO_BYTES))
				.willReturn(subject.getMemo());
		// and:
		var deserializedRecord = new ExpirableTxnRecord();

		// when:
		deserializedRecord.deserialize(fin, ExpirableTxnRecord.RELEASE_0120_VERSION);

		// then:
		assertEquals(subject, deserializedRecord);
	}

	@Test
	void v0160DeserializeWorks() throws IOException {
		// setup:
		subject = subjectRecordWithTokenTransfersAndScheduleRefCustomFees();
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);

		given(serdes.readNullableSerializable(fin))
				.willReturn(subject.getReceipt())
				.willReturn(subject.getTxnId())
				.willReturn(subject.getHbarAdjustments())
				.willReturn(subject.getContractCallResult())
				.willReturn(subject.getContractCreateResult())
				.willReturn(subject.getScheduleRef());
		given(fin.readSerializableList(MAX_INVOLVED_TOKENS))
				.willReturn(List.of(
						subject.getTokens().get(0),
						subject.getTokens().get(1)))
				.willReturn(List.of(
						subject.getTokenAdjustments().get(0),
						subject.getTokenAdjustments().get(1)));
		given(fin.readByteArray(ExpirableTxnRecord.MAX_TXN_HASH_BYTES))
				.willReturn(subject.getTxnHash());
		given(serdes.readNullableInstant(fin))
				.willReturn(subject.getConsensusTimestamp());
		given(fin.readLong()).willReturn(subject.getFee())
				.willReturn(subject.getExpiry())
				.willReturn(subject.getSubmittingMember());
		given(serdes.readNullableString(fin, ExpirableTxnRecord.MAX_MEMO_BYTES))
				.willReturn(subject.getMemo());
		given(fin.readSerializableList(MAX_ASSESSED_CUSTOM_FEES_CHANGES))
				.willReturn(List.of(subject.getCustomFeesCharged().get(0)));
		// and:
		var deserializedRecord = new ExpirableTxnRecord();

		// when:
		deserializedRecord.deserialize(fin, ExpirableTxnRecord.RELEASE_0160_VERSION);

		// then:
		assertEquals(subject, deserializedRecord);
	}

	@Test
	void serializeWorks() throws IOException {
		// setup:
		SerializableDataOutputStream fout = mock(SerializableDataOutputStream.class);
		// and:
		InOrder inOrder = Mockito.inOrder(serdes, fout);

		// when:
		subject.serialize(fout);

		// then:
		inOrder.verify(serdes).writeNullableSerializable(subject.getReceipt(), fout);
		inOrder.verify(fout).writeByteArray(subject.getTxnHash());
		inOrder.verify(serdes).writeNullableSerializable(subject.getTxnId(), fout);
		inOrder.verify(serdes).writeNullableInstant(subject.getConsensusTimestamp(), fout);
		inOrder.verify(serdes).writeNullableString(subject.getMemo(), fout);
		inOrder.verify(fout).writeLong(subject.getFee());
		inOrder.verify(serdes).writeNullableSerializable(subject.getHbarAdjustments(), fout);
		inOrder.verify(serdes).writeNullableSerializable(subject.getContractCallResult(), fout);
		inOrder.verify(serdes).writeNullableSerializable(subject.getContractCreateResult(), fout);
		inOrder.verify(fout).writeLong(subject.getExpiry());
		inOrder.verify(fout).writeLong(subject.getSubmittingMember());
		inOrder.verify(fout).writeSerializableList(
				subject.getTokens(), true, true);
		inOrder.verify(fout).writeSerializableList(
				subject.getTokenAdjustments(), true, true);
		inOrder.verify(serdes).writeNullableSerializable(EntityId.fromGrpcScheduleId(scheduleID), fout);
		inOrder.verify(fout).writeSerializableList(subject.getCustomFeesCharged(), true, true);
	}

	@Test
	void serializableDetWorks() {
		// expect;
		assertEquals(ExpirableTxnRecord.MERKLE_VERSION, subject.getVersion());
		assertEquals(ExpirableTxnRecord.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	void grpcInterconversionWorks() {
		// given:
		subject.setExpiry(0L);
		subject.setSubmittingMember(UNKNOWN_SUBMITTING_MEMBER);

		// expect:
		assertEquals(subject, ExpirableTxnRecord.fromGprc(subject.asGrpc()));
	}

	@Test
	void objectContractWorks() {
		// given:
		var one = subject;
		var two = DomainSerdesTest.recordOne();
		var three = subjectRecordWithTokenTransfersAndScheduleRefCustomFees();

		// when:
		assertNotEquals(one, null);
		assertNotEquals(one, new Object());
		assertEquals(one, three);
		assertNotEquals(one, two);
		// and:
		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(one.hashCode(), three.hashCode());
	}

	@Test
	void toStringWorks() {
		// setup:
		final var desired = "ExpirableTxnRecord{receipt=TxnReceipt{status=INVALID_ACCOUNT_ID, " +
				"accountCreated=EntityId{shard=0, realm=0, num=3}, newTotalTokenSupply=0}, " +
				"txnHash=6e6f742d7265616c6c792d612d68617368, txnId=TxnId{payer=EntityId{shard=0, realm=0, num=0}, " +
				"validStart=RichInstant{seconds=9999999999, nanos=0}, scheduled=false}, " +
				"consensusTimestamp=RichInstant{seconds=9999999999, nanos=0}, expiry=1234567, " +
				"submittingMember=1, memo=Alpha bravo charlie, contractCreation=SolidityFnResult{gasUsed=55, bloom=," +
				" result=, error=null, contractId=EntityId{shard=4, realm=3, num=2}, createdContractIds=[], " +
				"logs=[SolidityLog{data=4e6f6e73656e736963616c21, bloom=, contractId=null, topics=[]}]}, " +
				"hbarAdjustments=CurrencyAdjustments{readable=[0.0.2 -> -4, 0.0.1001 <- +2, 0.0.1002 <- +2]}, " +
				"scheduleRef=EntityId{shard=5, realm=6, num=7}, " +
				"tokenAdjustments=1.2.3(CurrencyAdjustments{readable=[1.2.5 -> -1, 1.2.6 <- +1, 1.2.7 <- +1000]}), " +
				"1.2.4(CurrencyAdjustments{readable=[1.2.5 -> -1, 1.2.6 <- +1, 1.2.7 <- +1000]}), " +
				"customFeesCharged=(AssessedCustomFee{token=EntityId{shard=1, realm=2, num=9}, " +
				"account=EntityId{shard=1, realm=2, num=8}, units=123})}";

		// expect:
		assertEquals(desired, subject.toString());
	}

	@AfterEach
	void cleanup() {
		ExpirableTxnRecord.serdes = new DomainSerdes();
	}
}
