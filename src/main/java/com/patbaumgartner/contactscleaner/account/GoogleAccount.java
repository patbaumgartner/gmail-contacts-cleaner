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
 * @param preferGoogleProfilePhotos whether to prefer a Google profile photo over a
 * contact-specific photo
 * @param repairGoogleContactDisplayNames whether to repair safe comma-form contact names
 * through the Google People API
 */
public record GoogleAccount(@NotBlank String name, @NotBlank @Email String email, @NotBlank String appPassword,
		@DefaultValue("true") boolean enabled, @DefaultValue("false") boolean dryRun,
		@DefaultValue("false") boolean importOtherContacts, String oauthClientId, String oauthClientSecret,
		String oauthRefreshToken, @DefaultValue("false") boolean preferGoogleProfilePhotos,
		@DefaultValue("false") boolean repairGoogleContactDisplayNames) {

	@ConstructorBinding
	public GoogleAccount {
	}

	/**
	 * Starts a builder for the three mandatory credentials; every other component keeps
	 * the default the Spring binder would apply.
	 * @param name human-readable label used in logs and reports
	 * @param email the Google account e-mail address
	 * @param appPassword a Google app password
	 * @return a new builder
	 */
	public static Builder builder(String name, String email, String appPassword) {
		return new Builder(name, email, appPassword);
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
		return ("GoogleAccount[name=%s, email=%s, appPassword=****, enabled=%s, dryRun=%s, importOtherContacts=%s, "
				+ "preferGoogleProfilePhotos=%s, repairGoogleContactDisplayNames=%s, oauthClientId=****, "
				+ "oauthClientSecret=****, oauthRefreshToken=****]")
			.formatted(name, email, enabled, dryRun, importOtherContacts, preferGoogleProfilePhotos,
					repairGoogleContactDisplayNames);
	}

	/** Named construction; the five boolean components read alike at a call site. */
	public static final class Builder {

		private final String name;

		private final String email;

		private final String appPassword;

		private boolean enabled = true;

		private boolean dryRun;

		private boolean importOtherContacts;

		private String oauthClientId = "";

		private String oauthClientSecret = "";

		private String oauthRefreshToken = "";

		private boolean preferGoogleProfilePhotos;

		private boolean repairGoogleContactDisplayNames;

		private Builder(String name, String email, String appPassword) {
			this.name = name;
			this.email = email;
			this.appPassword = appPassword;
		}

		public Builder enabled(boolean value) {
			this.enabled = value;
			return this;
		}

		public Builder dryRun(boolean value) {
			this.dryRun = value;
			return this;
		}

		public Builder importOtherContacts(boolean value) {
			this.importOtherContacts = value;
			return this;
		}

		/**
		 * Sets the OAuth credentials, which only ever authorize anything together.
		 * @param clientId OAuth client ID
		 * @param clientSecret OAuth client secret
		 * @param refreshToken offline OAuth refresh token
		 * @return this builder
		 */
		public Builder oauth(String clientId, String clientSecret, String refreshToken) {
			this.oauthClientId = clientId;
			this.oauthClientSecret = clientSecret;
			this.oauthRefreshToken = refreshToken;
			return this;
		}

		public Builder preferGoogleProfilePhotos(boolean value) {
			this.preferGoogleProfilePhotos = value;
			return this;
		}

		public Builder repairGoogleContactDisplayNames(boolean value) {
			this.repairGoogleContactDisplayNames = value;
			return this;
		}

		/**
		 * Builds the immutable account.
		 * @return the configured account
		 */
		public GoogleAccount build() {
			return new GoogleAccount(this.name, this.email, this.appPassword, this.enabled, this.dryRun,
					this.importOtherContacts, this.oauthClientId, this.oauthClientSecret, this.oauthRefreshToken,
					this.preferGoogleProfilePhotos, this.repairGoogleContactDisplayNames);
		}

	}
}
