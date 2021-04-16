package com.hedera.services.test.spec.utils;

import com.hedera.services.test.spec.HapiSpecOperation;

import java.util.List;
import java.util.stream.Stream;

public class OpsHelper {
	public static HapiSpecOperation[] flattened(Object... ops) {
		return Stream
				.of(ops)
				.map(op -> (op instanceof HapiSpecOperation)
						? new HapiSpecOperation[] { (HapiSpecOperation)op }
						: ((op instanceof List) ? ((List)op).toArray(new Object[0]) : (HapiSpecOperation[])op))
				.flatMap(Stream::of)
				.toArray(HapiSpecOperation[]::new);
	}
}
