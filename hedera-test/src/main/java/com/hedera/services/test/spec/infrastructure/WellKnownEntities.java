package com.hedera.services.test.spec.infrastructure;

import com.hedera.services.test.spec.HapiSpecSetup;

public class WellKnownEntities {
	public static final String SYSTEM_ADMIN = HapiSpecSetup.getDefaultInstance().strongControlName();
	public static final String FREEZE_ADMIN = HapiSpecSetup.getDefaultInstance().freezeAdminName();
	public static final String FUNDING = HapiSpecSetup.getDefaultInstance().fundingAccountName();
	public static final String GENESIS = HapiSpecSetup.getDefaultInstance().genesisAccountName();
	public static final String DEFAULT_PAYER = HapiSpecSetup.getDefaultInstance().defaultPayerName();

	public static final String ADDRESS_BOOK_CONTROL = HapiSpecSetup.getDefaultInstance().addressBookControlName();
	public static final String FEE_SCHEDULE_CONTROL = HapiSpecSetup.getDefaultInstance().feeScheduleControlName();
	public static final String EXCHANGE_RATE_CONTROL = HapiSpecSetup.getDefaultInstance().exchangeRatesControlName();
	public static final String SYSTEM_DELETE_ADMIN = HapiSpecSetup.getDefaultInstance().systemDeleteAdminName();
	public static final String SYSTEM_UNDELETE_ADMIN = HapiSpecSetup.getDefaultInstance().systemUndeleteAdminName();

	public static final String NODE_DETAILS = HapiSpecSetup.getDefaultInstance().nodeDetailsName();
	public static final String ADDRESS_BOOK = HapiSpecSetup.getDefaultInstance().addressBookName();
	public static final String EXCHANGE_RATES = HapiSpecSetup.getDefaultInstance().exchangeRatesName();
	public static final String FEE_SCHEDULE = HapiSpecSetup.getDefaultInstance().feeScheduleName();
	public static final String APP_PROPERTIES = HapiSpecSetup.getDefaultInstance().appPropertiesFile();
	public static final String API_PERMISSIONS = HapiSpecSetup.getDefaultInstance().apiPermissionsFile();
	public static final String UPDATE_ZIP_FILE = HapiSpecSetup.getDefaultInstance().updateFeatureName();
	public static final String THROTTLE_DEFS = HapiSpecSetup.getDefaultInstance().throttleDefinitionsName();
}
