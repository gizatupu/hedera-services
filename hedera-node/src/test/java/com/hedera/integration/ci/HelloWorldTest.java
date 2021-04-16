package com.hedera.integration.ci;

import com.hedera.services.test.TestHapiClient;
import com.hedera.services.test.extensions.HederaTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

@HederaTest
class HelloWorldTest {
	@Inject
	private TestHapiClient client;

	@Test
	@DisplayName("Balances change on CryptoTransfer")
	void anything() {
		client.verify();
	}
}
