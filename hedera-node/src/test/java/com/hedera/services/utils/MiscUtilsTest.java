package com.hedera.services.utils;

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

import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import com.hedera.services.exceptions.UnknownHederaFunctionality;
import com.hedera.services.grpc.controllers.ConsensusController;
import com.hedera.services.grpc.controllers.ContractController;
import com.hedera.services.grpc.controllers.CryptoController;
import com.hedera.services.grpc.controllers.FileController;
import com.hedera.services.grpc.controllers.FreezeController;
import com.hedera.services.keys.LegacyEd25519KeyReader;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.stats.ServicesStatsConfig;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusGetTopicInfoQuery;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractGetBytecodeQuery;
import com.hederahashgraph.api.proto.java.ContractGetInfoQuery;
import com.hederahashgraph.api.proto.java.ContractGetRecordsQuery;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceQuery;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.CryptoGetInfoQuery;
import com.hederahashgraph.api.proto.java.CryptoGetLiveHashQuery;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileGetContentsQuery;
import com.hederahashgraph.api.proto.java.FileGetInfoQuery;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.GetByKeyQuery;
import com.hederahashgraph.api.proto.java.GetBySolidityIDQuery;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.NetworkGetVersionInfoQuery;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleGetInfoQuery;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenGetInfoQuery;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetFastRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptQuery;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.api.proto.java.UncheckedSubmitBody;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.security.KeyPair;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.hedera.services.state.submerkle.ExpirableTxnRecord.fromGprc;
import static com.hedera.services.utils.MiscUtils.SCHEDULE_CREATE_METRIC;
import static com.hedera.services.utils.MiscUtils.SCHEDULE_DELETE_METRIC;
import static com.hedera.services.utils.MiscUtils.SCHEDULE_SIGN_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_ASSOCIATE_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_BURN_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_CREATE_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_DELETE_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_DISSOCIATE_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_FREEZE_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_GRANT_KYC_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_MINT_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_REVOKE_KYC_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_UNFREEZE_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_UPDATE_METRIC;
import static com.hedera.services.utils.MiscUtils.TOKEN_WIPE_ACCOUNT_METRIC;
import static com.hedera.services.utils.MiscUtils.activeHeaderFrom;
import static com.hedera.services.utils.MiscUtils.asOrdinary;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hedera.services.utils.MiscUtils.baseStatNameOf;
import static com.hedera.services.utils.MiscUtils.canonicalDiffRepr;
import static com.hedera.services.utils.MiscUtils.canonicalRepr;
import static com.hedera.services.utils.MiscUtils.functionOf;
import static com.hedera.services.utils.MiscUtils.functionalityOfQuery;
import static com.hedera.services.utils.MiscUtils.getTxnStat;
import static com.hedera.services.utils.MiscUtils.lookupInCustomStore;
import static com.hedera.services.utils.MiscUtils.readableProperty;
import static com.hedera.services.utils.MiscUtils.readableTransferList;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusDeleteTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetBytecode;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetRecords;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAddLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountRecords;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetContents;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetByKey;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetBySolidityID;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetVersionInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemUndelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetReceipt;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetRecord;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UncheckedSubmit;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class MiscUtilsTest {
	@Test
	void retrievesExpectedStatNames() {
		// expect:
		assertEquals(ContractController.CALL_CONTRACT_METRIC, MiscUtils.baseStatNameOf(ContractCall));
		assertEquals(GetByKey.toString(), baseStatNameOf(GetByKey));
	}

	@Test
	void getsNodeAccounts() {
		var address = mock(Address.class);
		given(address.getMemo()).willReturn("0.0.3");

		var book = mock(AddressBook.class);
		given(book.getSize()).willReturn(1);
		given(book.getAddress(0)).willReturn(address);

		// when:
		var accounts = MiscUtils.getNodeAccounts(book);

		// then:
		assertEquals(Set.of(IdUtils.asAccount("0.0.3")), accounts);
	}

	@Test
	void asFcKeyUncheckedTranslatesExceptions() {
		// expect:
		assertThrows(IllegalArgumentException.class,
				() -> MiscUtils.asFcKeyUnchecked(Key.getDefaultInstance()));
	}

	@Test
	void asFcKeyReturnsEmptyOnUnparseableKey() {
		// expect:
		assertTrue(asUsableFcKey(Key.getDefaultInstance()).isEmpty());
	}

	@Test
	void asFcKeyReturnsEmptyOnEmptyKey() {
		// expect:
		assertTrue(asUsableFcKey(Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build()).isEmpty());
	}

	@Test
	void asFcKeyReturnsEmptyOnInvalidKey() {
		// expect:
		assertTrue(asUsableFcKey(Key.newBuilder().setEd25519(ByteString.copyFrom("1".getBytes())).build()).isEmpty());
	}

	@Test
	void asFcKeyReturnsExpected() {
		// given:
		var key = Key.newBuilder().setEd25519(ByteString.copyFrom(
				"01234567890123456789012345678901".getBytes())).build();

		// expect:
		assertTrue(JKey.equalUpToDecodability(
				asUsableFcKey(key).get(),
				MiscUtils.asFcKeyUnchecked(key)));
	}

	@Test
	void asFcKeyUncheckedWorks() {
		// setup:
		byte[] fakePrivateKey = "not-really-a-key!".getBytes();
		// and:
		Key matchingKey = Key.newBuilder().setEd25519(ByteString.copyFrom(fakePrivateKey)).build();

		// given:
		var expected = new JEd25519Key(fakePrivateKey);

		// expect:
		assertTrue(JKey.equalUpToDecodability(expected, MiscUtils.asFcKeyUnchecked(matchingKey)));
	}

	@Test
	void translatesDecoderException() {
		// setup:
		String tmpLoc = "src/test/resources/PretendKeystore.txt";

		// when:
		assertThrows(IllegalArgumentException.class, () -> lookupInCustomStore(new LegacyEd25519KeyReader() {
			@Override
			public String hexedABytesFrom(String b64EncodedKeyPairLoc, String keyPairId) {
				return "This is not actually hex!";
			}
		}, tmpLoc, "START_ACCOUNT"));
	}

	@Test
	void recoversKeypair() throws Exception {
		// setup:
		String tmpLoc = "src/test/resources/PretendKeystore.txt";

		// given:
		KeyPair kp = new KeyPairGenerator().generateKeyPair();
		byte[] expected = ((EdDSAPublicKey)kp.getPublic()).getAbyte();
		// and:
		writeB64EncodedKeyPair(new File(tmpLoc), kp);

		// when:
		var masterKey = lookupInCustomStore(new LegacyEd25519KeyReader(), tmpLoc, "START_ACCOUNT");

		// then:
		assertArrayEquals(expected, masterKey.getEd25519());
		(new File(tmpLoc)).delete();
	}

	@Test
	void getsCanonicalDiff() {
		AccountID a = asAccount("1.2.3");
		AccountID b = asAccount("2.3.4");
		AccountID c = asAccount("3.4.5");

		// given:
		List<AccountAmount> canonicalA = List.of(
				aa(a, 300),
				aa(b, -500),
				aa(c, 200));
		// and:
		List<AccountAmount> canonicalB = List.of(
				aa(a, 150),
				aa(b, 50),
				aa(c, -200));

		// when:
		List<AccountAmount> canonicalDiff = canonicalDiffRepr(canonicalA, canonicalB);

		// then:
		assertThat(
				canonicalDiff,
				contains(aa(a, 150), aa(b, -550), aa(c, 400)));
	}

	@Test
	void getsCanonicalRepr() {
		AccountID a = asAccount("1.2.3");
		AccountID b = asAccount("2.3.4");
		AccountID c = asAccount("3.4.5");

		// given:
		List<AccountAmount> adhocRepr = List.of(
				aa(a, 500),
				aa(c, 100),
				aa(a, -500),
				aa(b, -500),
				aa(c, 400));

		// when:
		List<AccountAmount> canonicalRepr = canonicalRepr(adhocRepr);

		// then:
		assertThat(
				canonicalRepr,
				contains(aa(b, -500), aa(c, 500)));
	}

	private AccountAmount aa(AccountID who, long what) {
		return AccountAmount.newBuilder().setAccountID(who).setAmount(what).build();
	}

	@Test
	void prettyPrintsTransferList() {
		// given:
		TransferList transfers = withAdjustments(
				asAccount("0.1.2"), 500L,
				asAccount("1.0.2"), -250L,
				asAccount("1.2.0"), Long.MIN_VALUE);

		// when:
		String s = readableTransferList(transfers);

		assertEquals("[0.1.2 <- +500, 1.0.2 -> -250, 1.2.0 -> -9223372036854775808]", s);
	}

	@Test
	void prettyPrintsJTransactionRecordFcll() {
		// given:
		LinkedList<ExpirableTxnRecord> records = new LinkedList<>();
		records.add(fromGprc(
				TransactionRecord.newBuilder()
						.setReceipt(TransactionReceipt.newBuilder().setStatus(SUCCESS))
						.build()));
		records.add(fromGprc(
				TransactionRecord.newBuilder()
						.setReceipt(TransactionReceipt.newBuilder().setStatus(INVALID_ACCOUNT_ID))
						.build()));

		// expect:
		Assertions.assertDoesNotThrow(() -> readableProperty(records));
	}

	@Test
	void throwsOnUnexpectedFunctionality() {
		// expect:
		assertThrows(UnknownHederaFunctionality.class, () -> {
			functionOf(TransactionBody.getDefaultInstance());
		});
	}

	@Test
	void convertsMetadata() {
		// setup:
		long fee = 123L;
		String memo = "Hi there!";
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setTransactionFee(fee)
				.setMemo(memo)
				.build();

		// when:
		var ordinaryTxn = asOrdinary(scheduledTxn);

		// then:
		assertEquals(memo, ordinaryTxn.getMemo());
		assertEquals(fee, ordinaryTxn.getTransactionFee());
	}

	@Test
	void convertsContractCall() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setContractCall(ContractCallTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinaryTxn = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinaryTxn.hasContractCall());
	}

	@Test
	void convertsContractCreateInstance() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setContractCreateInstance(ContractCreateTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasContractCreateInstance());
	}

	@Test
	void convertsContractUpdateInstance() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setContractUpdateInstance(ContractUpdateTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasContractUpdateInstance());
	}

	@Test
	void convertsContractDeleteInstance() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setContractDeleteInstance(ContractDeleteTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasContractDeleteInstance());
	}

	@Test
	void convertsCryptoCreateAccount() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasCryptoCreateAccount());
	}

	@Test
	void convertsCryptoDelete() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setCryptoDelete(CryptoDeleteTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasCryptoDelete());
	}

	@Test
	void convertsCryptoTransfer() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasCryptoTransfer());
	}

	@Test
	void convertsCryptoUpdateAccount() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setCryptoUpdateAccount(CryptoUpdateTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasCryptoUpdateAccount());
	}

	@Test
	void convertsFileAppend() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setFileAppend(FileAppendTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasFileAppend());
	}

	@Test
	void convertsFileCreate() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setFileCreate(FileCreateTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasFileCreate());
	}

	@Test
	void convertsFileDelete() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setFileDelete(FileDeleteTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasFileDelete());
	}

	@Test
	void convertsFileUpdate() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setFileUpdate(FileUpdateTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasFileUpdate());
	}

	@Test
	void convertsSystemDelete() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setSystemDelete(SystemDeleteTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasSystemDelete());
	}

	@Test
	void convertsSystemUndelete() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setSystemUndelete(SystemUndeleteTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasSystemUndelete());
	}

	@Test
	void convertsFreeze() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setFreeze(FreezeTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasFreeze());
	}

	@Test
	void convertsConsensusCreateTopic() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setConsensusCreateTopic(ConsensusCreateTopicTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasConsensusCreateTopic());
	}

	@Test
	void convertsConsensusUpdateTopic() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setConsensusUpdateTopic(ConsensusUpdateTopicTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasConsensusUpdateTopic());
	}

	@Test
	void convertsConsensusDeleteTopic() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setConsensusDeleteTopic(ConsensusDeleteTopicTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasConsensusDeleteTopic());
	}

	@Test
	void convertsConsensusSubmitMessage() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setConsensusSubmitMessage(ConsensusSubmitMessageTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasConsensusSubmitMessage());
	}

	@Test
	void convertsTokenCreation() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setTokenCreation(TokenCreateTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasTokenCreation());
	}

	@Test
	void convertsTokenFreeze() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setTokenFreeze(TokenFreezeAccountTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasTokenFreeze());
	}

	@Test
	void convertsTokenUnfreeze() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setTokenUnfreeze(TokenUnfreezeAccountTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasTokenUnfreeze());
	}

	@Test
	void convertsTokenGrantKyc() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setTokenGrantKyc(TokenGrantKycTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasTokenGrantKyc());
	}

	@Test
	void convertsTokenRevokeKyc() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setTokenRevokeKyc(TokenRevokeKycTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasTokenRevokeKyc());
	}

	@Test
	void convertsTokenDeletion() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setTokenDeletion(TokenDeleteTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasTokenDeletion());
	}

	@Test
	void convertsTokenUpdate() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setTokenUpdate(TokenUpdateTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasTokenUpdate());
	}

	@Test
	void convertsTokenMint() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setTokenMint(TokenMintTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasTokenMint());
	}

	@Test
	void convertsTokenBurn() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setTokenBurn(TokenBurnTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasTokenBurn());
	}

	@Test
	void convertsTokenWipe() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setTokenWipe(TokenWipeAccountTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasTokenWipe());
	}

	@Test
	void convertsTokenAssociate() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setTokenAssociate(TokenAssociateTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasTokenAssociate());
	}

	@Test
	void convertsTokenDissociate() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setTokenDissociate(TokenDissociateTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasTokenDissociate());
	}

	@Test
	void convertsScheduleDelete() {
		// setup:
		var scheduledTxn = SchedulableTransactionBody.newBuilder()
				.setScheduleDelete(ScheduleDeleteTransactionBody.getDefaultInstance())
				.build();

		// when:
		var ordinary = asOrdinary(scheduledTxn);

		// then:
		assertTrue(ordinary.hasScheduleDelete());
	}

	@Test
	void getExpectedTxnStat() {
		Map<String, BodySetter<? extends GeneratedMessageV3>> setters = new HashMap<>() {{
			put(CryptoController.CRYPTO_CREATE_METRIC, new BodySetter<>(CryptoCreateTransactionBody.class));
			put(CryptoController.CRYPTO_UPDATE_METRIC, new BodySetter<>(CryptoUpdateTransactionBody.class));
			put(CryptoController.CRYPTO_TRANSFER_METRIC, new BodySetter<>(CryptoTransferTransactionBody.class));
			put(CryptoController.CRYPTO_DELETE_METRIC, new BodySetter<>(CryptoDeleteTransactionBody.class));
			put(ContractController.CREATE_CONTRACT_METRIC, new BodySetter<>(ContractCreateTransactionBody.class));
			put(ContractController.CALL_CONTRACT_METRIC, new BodySetter<>(ContractCallTransactionBody.class));
			put(ContractController.UPDATE_CONTRACT_METRIC, new BodySetter<>(ContractUpdateTransactionBody.class));
			put(ContractController.DELETE_CONTRACT_METRIC, new BodySetter<>(ContractDeleteTransactionBody.class));
			put(CryptoController.ADD_LIVE_HASH_METRIC, new BodySetter<>(CryptoAddLiveHashTransactionBody.class));
			put(CryptoController.DELETE_LIVE_HASH_METRIC, new BodySetter<>(CryptoDeleteLiveHashTransactionBody.class));
			put(FileController.CREATE_FILE_METRIC, new BodySetter<>(FileCreateTransactionBody.class));
			put(FileController.FILE_APPEND_METRIC, new BodySetter<>(FileAppendTransactionBody.class));
			put(FileController.UPDATE_FILE_METRIC, new BodySetter<>(FileUpdateTransactionBody.class));
			put(FileController.DELETE_FILE_METRIC, new BodySetter<>(FileDeleteTransactionBody.class));
			put(FreezeController.FREEZE_METRIC, new BodySetter<>(FreezeTransactionBody.class));
			put(ServicesStatsConfig.SYSTEM_DELETE_METRIC, new BodySetter<>(SystemDeleteTransactionBody.class));
			put(ServicesStatsConfig.SYSTEM_UNDELETE_METRIC, new BodySetter<>(SystemUndeleteTransactionBody.class));
			put(ConsensusController.CREATE_TOPIC_METRIC, new BodySetter<>(ConsensusCreateTopicTransactionBody.class));
			put(ConsensusController.UPDATE_TOPIC_METRIC, new BodySetter<>(ConsensusUpdateTopicTransactionBody.class));
			put(ConsensusController.DELETE_TOPIC_METRIC, new BodySetter<>(ConsensusDeleteTopicTransactionBody.class));
			put(ConsensusController.SUBMIT_MESSAGE_METRIC, new BodySetter<>(ConsensusSubmitMessageTransactionBody.class));
			put(TOKEN_CREATE_METRIC, new BodySetter<>(TokenCreateTransactionBody.class));
			put(TOKEN_FREEZE_METRIC, new BodySetter<>(TokenFreezeAccountTransactionBody.class));
			put(TOKEN_UNFREEZE_METRIC, new BodySetter<>(TokenUnfreezeAccountTransactionBody.class));
			put(TOKEN_GRANT_KYC_METRIC, new BodySetter<>(TokenGrantKycTransactionBody.class));
			put(TOKEN_REVOKE_KYC_METRIC, new BodySetter<>(TokenRevokeKycTransactionBody.class));
			put(TOKEN_DELETE_METRIC, new BodySetter<>(TokenDeleteTransactionBody.class));
			put(TOKEN_UPDATE_METRIC, new BodySetter<>(TokenUpdateTransactionBody.class));
			put(TOKEN_MINT_METRIC, new BodySetter<>(TokenMintTransactionBody.class));
			put(TOKEN_BURN_METRIC, new BodySetter<>(TokenBurnTransactionBody.class));
			put(TOKEN_WIPE_ACCOUNT_METRIC, new BodySetter<>(TokenWipeAccountTransactionBody.class));
			put(TOKEN_ASSOCIATE_METRIC, new BodySetter<>(TokenAssociateTransactionBody.class));
			put(TOKEN_DISSOCIATE_METRIC, new BodySetter<>(TokenDissociateTransactionBody.class));
			put(SCHEDULE_CREATE_METRIC, new BodySetter<>(ScheduleCreateTransactionBody.class));
			put(SCHEDULE_SIGN_METRIC, new BodySetter<>(ScheduleSignTransactionBody.class));
			put(SCHEDULE_DELETE_METRIC, new BodySetter<>(ScheduleDeleteTransactionBody.class));
		}};

		// expect:
		setters.forEach((stat, setter) -> {
			TransactionBody.Builder txn = TransactionBody.newBuilder();
			setter.setDefaultInstanceOnTxn(txn);
			assertEquals(stat, getTxnStat(txn.build()));
		});
		// and:
		assertEquals("NotImplemented", getTxnStat(TransactionBody.getDefaultInstance()));
	}

	@Test
	void recognizesMissingQueryCase() {
		// expect:
		assertTrue(functionalityOfQuery(Query.getDefaultInstance()).isEmpty());
	}

	@Test
	void getsExpectedQueryFunctionality() {
		// setup:
		Map<HederaFunctionality, BodySetter<? extends GeneratedMessageV3>> setters = new HashMap<>() {{
			put(GetVersionInfo, new BodySetter<>(NetworkGetVersionInfoQuery.class));
			put(GetByKey, new BodySetter<>(GetByKeyQuery.class));
			put(ConsensusGetTopicInfo, new BodySetter<>(ConsensusGetTopicInfoQuery.class));
			put(GetBySolidityID, new BodySetter<>(GetBySolidityIDQuery.class));
			put(ContractCallLocal, new BodySetter<>(ContractCallLocalQuery.class));
			put(ContractGetInfo, new BodySetter<>(ContractGetInfoQuery.class));
			put(ContractGetBytecode, new BodySetter<>(ContractGetBytecodeQuery.class));
			put(ContractGetRecords, new BodySetter<>(ContractGetRecordsQuery.class));
			put(CryptoGetAccountBalance, new BodySetter<>(CryptoGetAccountBalanceQuery.class));
			put(CryptoGetAccountRecords, new BodySetter<>(CryptoGetAccountRecordsQuery.class));
			put(CryptoGetInfo, new BodySetter<>(CryptoGetInfoQuery.class));
			put(CryptoGetLiveHash, new BodySetter<>(CryptoGetLiveHashQuery.class));
			put(FileGetContents, new BodySetter<>(FileGetContentsQuery.class));
			put(FileGetInfo, new BodySetter<>(FileGetInfoQuery.class));
			put(TransactionGetReceipt, new BodySetter<>(TransactionGetReceiptQuery.class));
			put(TransactionGetRecord, new BodySetter<>(TransactionGetRecordQuery.class));
			put(TokenGetInfo, new BodySetter<>(TokenGetInfoQuery.class));
			put(ScheduleGetInfo, new BodySetter<>(ScheduleGetInfoQuery.class));
		}};

		// expect:
		setters.forEach((function, setter) -> {
			Query.Builder query = Query.newBuilder();
			setter.setDefaultInstanceOnQuery(query);
			assertEquals(function, functionalityOfQuery(query.build()).get());
		});
	}

	@Test
	void worksForGetTokenInfo() {
		var op = TokenGetInfoQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setTokenGetInfo(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void worksForGetScheduleInfo() {
		var op = ScheduleGetInfoQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setScheduleGetInfo(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void worksForGetVersionInfo() {
		var op = NetworkGetVersionInfoQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setNetworkGetVersionInfo(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void worksForGetTopicInfo() {
		var op = ConsensusGetTopicInfoQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setConsensusGetTopicInfo(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void worksForGetSolidityId() {
		var op = GetBySolidityIDQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setGetBySolidityID(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void worksForGetContractCallLocal() {
		var op = ContractCallLocalQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setContractCallLocal(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void worksForGetContractInfo() {
		var op = ContractGetInfoQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setContractGetInfo(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void worksForGetContractBytecode() {
		var op = ContractGetBytecodeQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setContractGetBytecode(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void worksForGetContractRecords() {
		var op = ContractGetRecordsQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setContractGetRecords(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void worksForGetCryptoBalance() {
		var op = CryptoGetAccountBalanceQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setCryptogetAccountBalance(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void worksForGetCryptoRecords() {
		var op = CryptoGetAccountRecordsQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setCryptoGetAccountRecords(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void worksForGetCryptoInfo() {
		var op = CryptoGetInfoQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setCryptoGetInfo(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void worksForGetLiveHash() {
		var op = CryptoGetLiveHashQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setCryptoGetLiveHash(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void worksForGetFileContents() {
		var op = FileGetContentsQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setFileGetContents(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void worksForGetFileinfo() {
		var op = FileGetInfoQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setFileGetInfo(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void worksForReceipt() {
		var op = TransactionGetReceiptQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setTransactionGetReceipt(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void worksForRecord() {
		var op = TransactionGetRecordQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setTransactionGetRecord(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void worksForEmpty() {
		assertTrue(activeHeaderFrom(Query.getDefaultInstance()).isEmpty());
	}

	@Test
	void worksForFastRecord() {
		var op = TransactionGetFastRecordQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder().setResponseType(ANSWER_ONLY));
		var query = Query.newBuilder()
				.setTransactionGetFastRecord(op)
				.build();
		assertEquals(ANSWER_ONLY, activeHeaderFrom(query).get().getResponseType());
	}

	@Test
	void getsExpectedTxnFunctionality() {
		// setup:
		Map<HederaFunctionality, BodySetter<? extends GeneratedMessageV3>> setters = new HashMap<>() {{
			put(SystemDelete, new BodySetter<>(SystemDeleteTransactionBody.class));
			put(SystemUndelete, new BodySetter<>(SystemUndeleteTransactionBody.class));
			put(ContractCall, new BodySetter<>(ContractCallTransactionBody.class));
			put(ContractCreate, new BodySetter<>(ContractCreateTransactionBody.class));
			put(ContractUpdate, new BodySetter<>(ContractUpdateTransactionBody.class));
			put(CryptoAddLiveHash, new BodySetter<>(CryptoAddLiveHashTransactionBody.class));
			put(CryptoCreate, new BodySetter<>(CryptoCreateTransactionBody.class));
			put(CryptoDelete, new BodySetter<>(CryptoDeleteTransactionBody.class));
			put(CryptoDeleteLiveHash, new BodySetter<>(CryptoDeleteLiveHashTransactionBody.class));
			put(CryptoTransfer, new BodySetter<>(CryptoTransferTransactionBody.class));
			put(CryptoUpdate, new BodySetter<>(CryptoUpdateTransactionBody.class));
			put(FileAppend, new BodySetter<>(FileAppendTransactionBody.class));
			put(FileCreate, new BodySetter<>(FileCreateTransactionBody.class));
			put(FileDelete, new BodySetter<>(FileDeleteTransactionBody.class));
			put(FileUpdate, new BodySetter<>(FileUpdateTransactionBody.class));
			put(ContractDelete, new BodySetter<>(ContractDeleteTransactionBody.class));
			put(TokenCreate, new BodySetter<>(TokenCreateTransactionBody.class));
			put(TokenFreezeAccount, new BodySetter<>(TokenFreezeAccountTransactionBody.class));
			put(TokenUnfreezeAccount, new BodySetter<>(TokenUnfreezeAccountTransactionBody.class));
			put(TokenGrantKycToAccount, new BodySetter<>(TokenGrantKycTransactionBody.class));
			put(TokenRevokeKycFromAccount, new BodySetter<>(TokenRevokeKycTransactionBody.class));
			put(TokenDelete, new BodySetter<>(TokenDeleteTransactionBody.class));
			put(TokenUpdate, new BodySetter<>(TokenUpdateTransactionBody.class));
			put(TokenMint, new BodySetter<>(TokenMintTransactionBody.class));
			put(TokenBurn, new BodySetter<>(TokenBurnTransactionBody.class));
			put(TokenAccountWipe, new BodySetter<>(TokenWipeAccountTransactionBody.class));
			put(TokenAssociateToAccount, new BodySetter<>(TokenAssociateTransactionBody.class));
			put(TokenDissociateFromAccount, new BodySetter<>(TokenDissociateTransactionBody.class));
			put(ScheduleCreate, new BodySetter<>(ScheduleCreateTransactionBody.class));
			put(ScheduleSign, new BodySetter<>(ScheduleSignTransactionBody.class));
			put(ScheduleDelete, new BodySetter<>(ScheduleDeleteTransactionBody.class));
			put(Freeze, new BodySetter<>(FreezeTransactionBody.class));
			put(ConsensusCreateTopic, new BodySetter<>(ConsensusCreateTopicTransactionBody.class));
			put(ConsensusUpdateTopic, new BodySetter<>(ConsensusUpdateTopicTransactionBody.class));
			put(ConsensusDeleteTopic, new BodySetter<>(ConsensusDeleteTopicTransactionBody.class));
			put(ConsensusSubmitMessage, new BodySetter<>(ConsensusSubmitMessageTransactionBody.class));
			put(UncheckedSubmit, new BodySetter<>(UncheckedSubmitBody.class));
		}};

		// expect:
		setters.forEach((function, setter) -> {
			TransactionBody.Builder txn = TransactionBody.newBuilder();
			setter.setDefaultInstanceOnTxn(txn);
			try {
				assertEquals(function, functionOf(txn.build()));
			} catch (UnknownHederaFunctionality uhf) {
				throw new IllegalStateException(uhf);
			}
		});
	}

	@Test
	void hashCorrectly() throws IllegalArgumentException {
		byte[] testBytes = "test bytes".getBytes();
		byte[] expectedHash = com.swirlds.common.CommonUtils.unhex(
				"2ddb907ecf9a8c086521063d6d310d46259437770587b3dbe2814ab17962a4e124a825fdd02cb167ac9fffdd4a5e8120"
		);
		Transaction transaction = mock(Transaction.class);
		PlatformTxnAccessor accessor = mock(PlatformTxnAccessor.class);
		given(transaction.toByteArray()).willReturn(testBytes);
		given(accessor.getSignedTxnWrapper()).willReturn(transaction);

		assertArrayEquals(expectedHash, CommonUtils.noThrowSha384HashOf(testBytes));
		assertArrayEquals(expectedHash, CommonUtils.sha384HashOf(testBytes).toByteArray());
	}

	@Test
	void asTimestampTest() {
		final Instant instant = Instant.now();
		final Timestamp timestamp = MiscUtils.asTimestamp(instant);
		assertEquals(instant, MiscUtils.timestampToInstant(timestamp));
	}

	public static class BodySetter<T> {
		private final Class<T> type;

		public BodySetter(Class<T> type) {
			this.type = type;
		}

		void setDefaultInstanceOnQuery(Query.Builder query) {
			try {
				Method setter = Stream.of(Query.Builder.class.getDeclaredMethods())
						.filter(m -> m.getName().startsWith("set") && m.getParameterTypes()[0].equals(type))
						.findFirst()
						.get();
				Method defaultGetter = type.getMethod("getDefaultInstance");
				T defaultInstance = (T)defaultGetter.invoke(null);
				setter.invoke(query, defaultInstance);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}

		void setDefaultInstanceOnTxn(TransactionBody.Builder txn) {
			try {
				Method setter = Stream.of(TransactionBody.Builder.class.getDeclaredMethods())
						.filter(m -> m.getName().startsWith("set") && m.getParameterTypes()[0].equals(type))
						.findFirst()
						.get();
				Method defaultGetter = type.getMethod("getDefaultInstance");
				T defaultInstance = (T)defaultGetter.invoke(null);
				setter.invoke(txn, defaultInstance);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}
	}

	private static void writeB64EncodedKeyPair(File file, KeyPair keyPair) throws IOException {
		var hexPublicKey = com.swirlds.common.CommonUtils.hex(keyPair.getPublic().getEncoded());
		var hexPrivateKey = com.swirlds.common.CommonUtils.hex(keyPair.getPrivate().getEncoded());
		var keyPairObj = new KeyPairObj(hexPublicKey, hexPrivateKey);
		var keys = new AccountKeyListObj(asAccount("0.0.2"), List.of(keyPairObj));

		var baos = new ByteArrayOutputStream();
		var oos = new ObjectOutputStream(baos);
		oos.writeObject(Map.of("START_ACCOUNT", List.of(keys)));
		oos.close();

		var byteSink = Files.asByteSink(file);
		byteSink.write(CommonUtils.base64encode(baos.toByteArray()).getBytes());
	}
}
