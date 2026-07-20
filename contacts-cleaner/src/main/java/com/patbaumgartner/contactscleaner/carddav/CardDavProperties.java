package com.patbaumgartner.contactscleaner.carddav;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the Google CardDAV endpoint, bound from
 * {@code contacts-cleaner.carddav}.
 *
 * <p>
 * The defaults target Google's production CardDAV service and normally do not need to be
 * changed. The base URL is overridable so that integration tests can point the client at
 * a local mock server.
 *
 * @param baseUrl the CardDAV server base URL (default: {@code https://www.google.com})
 * @param connectTimeout TCP connect timeout
 * @param readTimeout socket read timeout — CardDAV address book reports can be large, so
 * this is deliberately generous
 * @param requestDelay pause between successive write requests, to stay well below
 * Google's rate limits
 */
@Validated
@ConfigurationProperties(prefix = "contacts-cleaner.carddav")
public record CardDavProperties(@DefaultValue("https://www.google.com") @NotBlank String baseUrl,
		@DefaultValue("10s") @NotNull Duration connectTimeout, @DefaultValue("60s") @NotNull Duration readTimeout,
		@DefaultValue("200ms") @NotNull Duration requestDelay) {

	/**
	 * Returns the address book collection path for the given principal (account e-mail).
	 * Google exposes exactly one address book per account under {@code lists/default}.
	 * @param email the account e-mail address
	 * @return the absolute path of the default address book collection
	 */
	public String addressBookPath(String email) {
		return "/carddav/v1/principals/%s/lists/default/".formatted(email);
	}
}
