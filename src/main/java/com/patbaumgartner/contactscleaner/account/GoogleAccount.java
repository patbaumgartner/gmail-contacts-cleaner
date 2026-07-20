package com.patbaumgartner.contactscleaner.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.bind.DefaultValue;

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
 */
public record GoogleAccount(@NotBlank String name, @NotBlank @Email String email, @NotBlank String appPassword,
		@DefaultValue("true") boolean enabled, @DefaultValue("false") boolean dryRun) {

	/**
	 * Returns a redacted representation. The app password must never leak into logs,
	 * error messages or heap dumps rendered as text.
	 */
	@Override
	public String toString() {
		return "GoogleAccount[name=%s, email=%s, appPassword=****, enabled=%s, dryRun=%s]".formatted(name, email,
				enabled, dryRun);
	}
}
