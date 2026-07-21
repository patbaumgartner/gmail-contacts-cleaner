package com.patbaumgartner.contactscleaner.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * A single Google account whose contacts should be cleaned.
 *
 * @param name human-readable label used in log messages and reports (e.g.
 * {@code "personal"} or {@code "work"})
 * @param email the Google account e-mail address (also the CardDAV principal)
 * @param appPassword a Google <a href="https://myaccount.google.com/apppasswords">app
 * password</a>; never the real account password
 * @param enabled whether this account participates in cleanup runs
 * @param dryRun when {@code true}, all changes are computed and logged but nothing is
 * written back to Google — recommended for the first run
 * @param importOtherContacts whether Other contacts should be promoted before CardDAV
 * cleanup
 * @param oauthClientId OAuth client ID used only for Other contacts import
 * @param oauthClientSecret OAuth client secret used only for Other contacts import
 * @param oauthRefreshToken OAuth refresh token used only for Other contacts import
 */
public record GoogleAccount(@NotBlank String name, @NotBlank @Email String email, @NotBlank String appPassword,
		@DefaultValue("true") boolean enabled, @DefaultValue("false") boolean dryRun,
		@DefaultValue("false") boolean importOtherContacts, String oauthClientId, String oauthClientSecret,
		String oauthRefreshToken) {

	@ConstructorBinding
	public GoogleAccount {
	}

	public GoogleAccount(String name, String email, String appPassword, boolean enabled, boolean dryRun) {
		this(name, email, appPassword, enabled, dryRun, false, "", "", "");
	}

	/**
	 * Whether this account has all credentials needed to import Other contacts.
	 * @return {@code true} if the OAuth client and refresh token are configured
	 */
	public boolean hasOtherContactsImportCredentials() {
		return isNotBlank(oauthClientId) && isNotBlank(oauthClientSecret) && isNotBlank(oauthRefreshToken);
	}

	private static boolean isNotBlank(String value) {
		return value != null && !value.isBlank();
	}

	/**
	 * Returns a redacted representation. The app password must never leak into logs,
	 * error messages or heap dumps rendered as text.
	 */
	@Override
	public String toString() {
		return "GoogleAccount[name=%s, email=%s, appPassword=****, enabled=%s, dryRun=%s, importOtherContacts=%s, "
				+ "oauthClientId=****, oauthClientSecret=****, oauthRefreshToken=****]".formatted(name, email, enabled,
						dryRun, importOtherContacts);
	}
}
