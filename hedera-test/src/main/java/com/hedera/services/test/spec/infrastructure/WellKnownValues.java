package com.hedera.services.test.spec.infrastructure;

import com.hedera.services.test.spec.HapiSpecSetup;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;

public class WellKnownValues {
	public static final HapiSpecSetup DEFAULT_PROPS = HapiSpecSetup.getDefaultInstance();

	public static final int DEFAULT_MEMO_LENGTH = 25;
	public static final int DEFAULT_PERF_THREADS = 50;
	public static final int DEFAULT_COLLISION_AVOIDANCE_FACTOR = 2;
	public static final int DEFAULT_DURATION_CREATE_TOKEN_ASSOCIATION = 60;
	public static final int DEFAULT_DURATION_TOKEN_TRANSFER = 60;

	public static final long ONE_HBAR = 100_000_000L;
	public static final long ADEQUATE_FUNDS = 10_000_000_000L;
	public static final long ONE_HUNDRED_HBARS = 100 * ONE_HBAR;
	public static final long ONE_MILLION_HBARS = 1_000_000L * ONE_HBAR;

	public static final long THREE_MONTHS_IN_SECONDS = 7776000L;

	public static final Key EMPTY_KEY = Key.newBuilder().setKeyList(KeyList.newBuilder().build()).build();

	public static final String TOKEN_TREASURY = "treasury";
}
