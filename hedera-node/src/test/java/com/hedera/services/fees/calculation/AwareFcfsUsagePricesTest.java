package com.hedera.services.fees.calculation;

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
import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.bootstrap.JsonToProtoSerdeTest;
import com.hedera.services.files.HederaFs;
import com.hedera.services.files.interceptors.MockFileNumbers;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.mocks.MockAppender;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Instant;

import static com.hedera.services.fees.calculation.AwareFcfsUsagePrices.DEFAULT_USAGE_PRICES;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UNRECOGNIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

class AwareFcfsUsagePricesTest {
	FileID schedules = IdUtils.asFile("0.0.111");
	long currentExpiry = 1_234_567;
	long nextExpiry = currentExpiry + 1_000;
	FeeComponents currResourceUsagePrices = FeeComponents.newBuilder()
			.setMin(currentExpiry)
			.setMax(currentExpiry)
			.setBpr(1_000_000L)
			.setBpt(2_000_000L)
			.setRbh(3_000_000L)
			.setSbh(4_000_000L)
			.build();
	FeeComponents nextResourceUsagePrices = FeeComponents.newBuilder()
			.setMin(nextExpiry)
			.setMax(nextExpiry)
			.setBpr(1_000_000L)
			.setBpt(2_000_000L)
			.setRbh(3_000_000L)
			.setSbh(4_000_000L)
			.build();
	FeeData currUsagePrices = FeeData.newBuilder()
			.setNetworkdata(currResourceUsagePrices)
			.setNodedata(currResourceUsagePrices)
			.setServicedata(currResourceUsagePrices)
			.build();
	FeeData nextUsagePrices = FeeData.newBuilder()
			.setNetworkdata(nextResourceUsagePrices)
			.setNodedata(nextResourceUsagePrices)
			.setServicedata(nextResourceUsagePrices)
			.build();

	FeeData nextCryptoTransferUsagePrices = nextUsagePrices;
	FeeData currentCryptoTransferUsagePrices = currUsagePrices;
	FeeSchedule nextFeeSchedule, currentFeeSchedule;
	CurrentAndNextFeeSchedule feeSchedules;

	AwareFcfsUsagePrices subject;

	TransactionBody cryptoTransferTxn = TransactionBody.newBuilder()
			.setTransactionID(TransactionID.newBuilder()
					.setTransactionValidStart(Timestamp.newBuilder().setSeconds(nextExpiry - 1)))
			.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()
					.setTransfers(TxnUtils.withAdjustments(
							IdUtils.asAccount("1.2.3"), 1,
							IdUtils.asAccount("2.2.3"), 1,
							IdUtils.asAccount("3.2.3"), -2))
			).build();

	HederaFs hfs;
	TransactionContext txnCtx;
	PlatformTxnAccessor accessor;

	@BeforeEach
	private void setup() {
		nextFeeSchedule = FeeSchedule.newBuilder()
				.setExpiryTime(TimestampSeconds.newBuilder().setSeconds(nextExpiry))
				.addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
						.setHederaFunctionality(CryptoTransfer)
						.setFeeData(nextCryptoTransferUsagePrices))
				.build();
		currentFeeSchedule = FeeSchedule.newBuilder()
				.setExpiryTime(TimestampSeconds.newBuilder().setSeconds(currentExpiry))
				.addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
						.setHederaFunctionality(CryptoTransfer)
						.setFeeData(currentCryptoTransferUsagePrices))
				.build();
		feeSchedules = CurrentAndNextFeeSchedule.newBuilder()
				.setCurrentFeeSchedule(currentFeeSchedule)
				.setNextFeeSchedule(nextFeeSchedule)
				.build();

		hfs = mock(HederaFs.class);
		given(hfs.exists(schedules)).willReturn(true);
		given(hfs.cat(schedules)).willReturn(feeSchedules.toByteArray());

		accessor = mock(PlatformTxnAccessor.class);
		given(accessor.getTxn()).willReturn(cryptoTransferTxn);
		given(accessor.getTxnId()).willReturn(cryptoTransferTxn.getTransactionID());
		given(accessor.getFunction()).willReturn(CryptoTransfer);
		txnCtx = mock(TransactionContext.class);
		given(txnCtx.accessor()).willReturn(accessor);

		subject = new AwareFcfsUsagePrices(hfs, new MockFileNumbers(), txnCtx);
	}

	@Test
	void returnsExpectedPriceSequence() {
		// given:
		subject.loadPriceSchedules();

		// when:
		var actual = subject.activePricingSequence(CryptoTransfer);

		// then:
		assertEquals(
				Triple.of(
						currentCryptoTransferUsagePrices,
						Instant.ofEpochSecond(currentExpiry),
						nextCryptoTransferUsagePrices),
				actual);
	}

	@Test
	void getsActivePrices() throws Exception {
		// given:
		subject.loadPriceSchedules();

		// when:
		FeeData actual = subject.activePrices();

		// then:
		assertEquals(nextUsagePrices, actual);
	}

	@Test
	void getsDefaultPricesIfActiveTxnInvalid() throws Exception {
		// given:
		subject.loadPriceSchedules();
		// and:
		given(accessor.getTxn()).willReturn(TransactionBody.getDefaultInstance());
		given(accessor.getFunction()).willReturn(UNRECOGNIZED);

		// when:
		FeeData actual = subject.activePrices();

		// then:
		assertEquals(DEFAULT_USAGE_PRICES, actual);
	}


	@Test
	void getsTransferUsagePricesAtCurrent() throws Exception {
		// given:
		subject.loadPriceSchedules();
		Timestamp at = Timestamp.newBuilder()
				.setSeconds(currentExpiry - 1)
				.build();

		// when:
		FeeData actual = subject.pricesGiven(CryptoTransfer, at);

		// then:
		assertEquals(currentCryptoTransferUsagePrices, actual);
	}

	@Test
	void returnsDefaultUsagePricesForUnsupported() throws Exception {
		// setup:
		MockAppender mockAppender = new MockAppender();
		var log = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(AwareFcfsUsagePrices.class);
		log.addAppender(mockAppender);
		Level levelForReset = log.getLevel();
		log.setLevel(Level.DEBUG);

		// given:
		subject.loadPriceSchedules();
		Timestamp at = Timestamp.newBuilder()
				.setSeconds(currentExpiry - 1)
				.build();

		// when:
		FeeData actual = subject.pricesGiven(UNRECOGNIZED, at);

		// then:
		assertEquals(DEFAULT_USAGE_PRICES, actual);
		assertEquals(1, mockAppender.size());
		assertEquals("DEBUG - Default usage price will be used, no specific usage prices available for function UNRECOGNIZED @ 1970-01-15T06:56:06Z!",
				mockAppender.get(0));

		// tearDown:
		log.setLevel(levelForReset);
		log.removeAppender(mockAppender);
		mockAppender.clear();
	}

	@Test
	void getsTransferUsagePricesPastCurrentBeforeNextExpiry() throws Exception {
		// given:
		subject.loadPriceSchedules();
		Timestamp at = Timestamp.newBuilder()
				.setSeconds(nextExpiry - 1)
				.build();

		// when:
		FeeData actual = subject.pricesGiven(CryptoTransfer, at);

		// then:
		assertEquals(nextCryptoTransferUsagePrices, actual);
	}

	@Test
	void loadsGoodScheduleUneventfully() throws Exception {
		// setup:
		byte[] bytes = Files.toByteArray(new File(JsonToProtoSerdeTest.R4_FEE_SCHEDULE_REPR_PATH));
		CurrentAndNextFeeSchedule expectedFeeSchedules = CurrentAndNextFeeSchedule.parseFrom(bytes);

		given(hfs.exists(schedules)).willReturn(true);
		given(hfs.cat(schedules)).willReturn(bytes);

		// when:
		subject.loadPriceSchedules();

		// then:
		assertEquals(expectedFeeSchedules, subject.feeSchedules);
	}

	@Test
	void throwsNfseOnMissingScheduleInFcfs() {
		given(hfs.exists(schedules)).willReturn(false);

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.loadPriceSchedules());
	}

	@Test
	void throwsNfseOnBadScheduleInFcfs() {
		given(hfs.exists(schedules)).willReturn(true);
		given(hfs.cat(any())).willReturn("NONSENSE".getBytes());

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.loadPriceSchedules());
	}

	@Test
	void usesDefaultPricesForUnexpectedFailure() {
		given(accessor.getFunction()).willThrow(IllegalStateException.class);

		// when:
		var prices = subject.activePrices();

		// then:
		assertEquals(DEFAULT_USAGE_PRICES, prices);
	}
}
