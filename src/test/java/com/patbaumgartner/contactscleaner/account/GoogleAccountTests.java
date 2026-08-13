package com.patbaumgartner.contactscleaner.account;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleAccountTests {

	private static final GoogleAccount ACCOUNT = GoogleAccount
		.builder("personal", "jane.doe@gmail.com", "abcd efgh ijkl mnop")
		.dryRun(true)
		.importOtherContacts(true)
		.oauth("client-id", "client-secret", "refresh-token")
		.preferGoogleProfilePhotos(true)
		.repairGoogleContactDisplayNames(true)
		.build();

	@Test
	void builderDefaultsToAnEnabledLiveAccountWithoutPeopleApiFeatures() {
		GoogleAccount minimal = GoogleAccount.builder("personal", "jane.doe@gmail.com", "pw").build();

		assertThat(minimal.enabled()).isTrue();
		assertThat(minimal.dryRun()).isFalse();
		assertThat(minimal.importOtherContacts()).isFalse();
		assertThat(minimal.preferGoogleProfilePhotos()).isFalse();
		assertThat(minimal.repairGoogleContactDisplayNames()).isFalse();
		assertThat(minimal.hasOtherContactsImportCredentials()).isFalse();
	}

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
		assertThat(GoogleAccount.builder("personal", "jane.doe@gmail.com", "pw")
			.oauth("client-id", "  ", "refresh-token")
			.build()
			.hasOtherContactsImportCredentials()).isFalse();
		assertThat(GoogleAccount.builder("personal", "jane.doe@gmail.com", "pw")
			.oauth(null, null, null)
			.build()
			.hasOtherContactsImportCredentials()).isFalse();
	}

	@Test
	void enabledAccountsFiltersDisabledEntries() {
		GoogleAccount disabled = GoogleAccount.builder("work", "jane@example.test", "pw").enabled(false).build();

		assertThat(new AccountsProperties(List.of(ACCOUNT, disabled)).enabledAccounts()).containsExactly(ACCOUNT);
		assertThat(new AccountsProperties(null).enabledAccounts()).isEmpty();
	}

}
