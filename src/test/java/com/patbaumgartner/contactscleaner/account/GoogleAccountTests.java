package com.patbaumgartner.contactscleaner.account;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleAccountTests {

	private static final GoogleAccount ACCOUNT = new GoogleAccount("personal", "jane.doe@gmail.com",
			"abcd efgh ijkl mnop", true, true, true, "client-id", "client-secret", "refresh-token", true, true);

	@Test
	void toStringRendersTheAccountIdentityInsteadOfFormatPlaceholders() {
		assertThat(ACCOUNT).hasToString("GoogleAccount[name=personal, email=jane.doe@gmail.com, appPassword=****, "
				+ "enabled=true, dryRun=true, importOtherContacts=true, preferGoogleProfilePhotos=true, "
				+ "repairGoogleContactDisplayNames=true, oauthClientId=****, oauthClientSecret=****, "
				+ "oauthRefreshToken=****]");
	}

	@Test
	void toStringNeverLeaksSecrets() {
		assertThat(ACCOUNT.toString()).doesNotContain("abcd efgh ijkl mnop")
			.doesNotContain("client-id")
			.doesNotContain("client-secret")
			.doesNotContain("refresh-token");
	}

	@Test
	void requiresAllThreeOAuthValuesForPeopleApiAccess() {
		assertThat(ACCOUNT.hasOtherContactsImportCredentials()).isTrue();
		assertThat(new GoogleAccount("personal", "jane.doe@gmail.com", "pw", true, false, true, "client-id", "  ",
				"refresh-token", false, false)
			.hasOtherContactsImportCredentials()).isFalse();
		assertThat(new GoogleAccount("personal", "jane.doe@gmail.com", "pw", true, false, true, null, null, null, false,
				false)
			.hasOtherContactsImportCredentials()).isFalse();
	}

	@Test
	void enabledAccountsFiltersDisabledEntries() {
		GoogleAccount disabled = new GoogleAccount("work", "jane@example.test", "pw", false, false, false, "", "", "",
				false, false);

		assertThat(new AccountsProperties(java.util.List.of(ACCOUNT, disabled)).enabledAccounts())
			.containsExactly(ACCOUNT);
		assertThat(new AccountsProperties(null).enabledAccounts()).isEmpty();
	}

}
