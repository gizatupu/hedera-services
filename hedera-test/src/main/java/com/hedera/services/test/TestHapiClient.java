package com.hedera.services.test;

import com.hedera.services.test.spec.HapiApiSpec;
import com.hedera.services.test.spec.HapiSpecOperation;
import org.junit.jupiter.api.Assertions;

import static com.hedera.services.test.spec.HapiApiSpec.SpecStatus.PASSED;

public class TestHapiClient {
	public void verify(HapiSpecOperation... ops) {
		var spec = HapiApiSpec.defaultHapiSpec("anonymous").given(ops).when().then();
		spec.run();
		Assertions.assertEquals(PASSED, spec.getStatus());
	}
}
