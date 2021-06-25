package com.hedera.services.legacy.regression;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.legacy.core.TestHelper;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.Calendar;
import java.util.Properties;
import java.util.TimeZone;

public class Utilities {

	private static final Logger log = LogManager.getLogger(Utilities.class);
	protected static String DEFAULT_NODE_ACCOUNT_ID_STR = "0.0.3";

	/**
	 * get configured Node Account from application.properties
	 *
	 * @return nodeAccountId
	 */
	public static long getDefaultNodeAccount() {
		Properties properties = TestHelper.getApplicationProperties();
		String nodeAccIDStr = properties
				.getProperty("defaultListeningNodeAccount", DEFAULT_NODE_ACCOUNT_ID_STR);
		long nodeAccount = 3;
		try {
			nodeAccount = Long.parseLong(nodeAccIDStr.substring(nodeAccIDStr.lastIndexOf('.') + 1));
		} catch (NumberFormatException e) {
			log.error("incorrect format of defaultListeningNodeAccount, using default nodeAccountId=3 ",
					e);
		}
		return nodeAccount;
	}

	/**
	 * get UTC Hour and Minutes from utcMillis
	 *
	 * @param utcMillis
	 * 		UTC milliseconds given
	 * @return generated UTC hour and minutes
	 */
	public static int[] getUTCHourMinFromMillis(final long utcMillis) {
		int[] hourMin = new int[2];
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		cal.setTimeInMillis(utcMillis);
		hourMin[0] = cal.get(Calendar.HOUR_OF_DAY);
		hourMin[1] = cal.get(Calendar.MINUTE);
		return hourMin;
	}

}
