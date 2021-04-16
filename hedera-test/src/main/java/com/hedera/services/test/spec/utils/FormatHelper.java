package com.hedera.services.test.spec.utils;

public class FormatHelper {
	public static String sdec(double d, int numDecimals) {
		var fmt = String.format(".0%df", numDecimals);
		return String.format("%" + fmt, d);
	}
}
