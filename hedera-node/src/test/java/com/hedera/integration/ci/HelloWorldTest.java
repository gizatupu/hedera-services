package com.hedera.integration.ci;

import com.hedera.services.test.TestHapiClient;
import com.hedera.services.test.extensions.HederaTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static com.hedera.services.test.spec.infrastructure.WellKnownEntities.GENESIS;
import static com.hedera.services.test.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.test.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.test.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.test.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;

@HederaTest
class HelloWorldTest {
	@Inject
	private TestHapiClient client;

	@Test
	@DisplayName("Balances change on CryptoTransfer")
	void balancesChange() {
		client.verify(
				cryptoCreate("sender").balance(1L),
				cryptoCreate("receiver").balance(0L),

				cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1)).payingWith(GENESIS),

				getAccountBalance("sender").hasTinyBars(0L),
				getAccountBalance("receiver").hasTinyBars(1L)
		);
	}
}
