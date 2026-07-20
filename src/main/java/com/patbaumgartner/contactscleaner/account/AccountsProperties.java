package com.patbaumgartner.contactscleaner.account;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Multi-account configuration bound from {@code contacts-cleaner.accounts}.
 *
 * <p>
 * Example {@code application.yml}:
 * <pre>{@code contacts-cleaner: accounts: - name: personal email: jane.doe@gmail.com app-password: "abcd efgh ijkl mnop" - name: work email: jane@example.com app-password: "qrst uvwx yzab cdef"
 *       dry-run: true
 * }</pre>
 *
 * @param accounts the configured Google accounts; may be empty, in which case a cleanup
 * run is a no-op
 */
@Validated
@ConfigurationProperties(prefix = "contacts-cleaner")
public record AccountsProperties(List<@Valid GoogleAccount> accounts) {

	public AccountsProperties {
		accounts = (accounts != null) ? List.copyOf(accounts) : List.of();
	}

	/**
	 * Returns only the accounts that are enabled for cleanup runs.
	 * @return enabled accounts, never {@code null}
	 */
	public List<GoogleAccount> enabledAccounts() {
		return accounts.stream().filter(GoogleAccount::enabled).toList();
	}
}
