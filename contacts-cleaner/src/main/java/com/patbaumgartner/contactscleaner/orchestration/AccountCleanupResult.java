package com.patbaumgartner.contactscleaner.orchestration;

/**
 * Result of a cleanup run for a single account.
 *
 * @param accountName the configured account label
 * @param successful whether the run completed without errors
 * @param totalContacts number of contacts fetched from the address book
 * @param updatedContacts number of contacts that were modified and written back
 * @param deletedContacts number of empty contacts that were deleted
 * @param dryRun whether this was a dry run (changes computed but not applied)
 * @param durationMs wall-clock duration of the run in milliseconds
 * @param message human-readable status message
 */
public record AccountCleanupResult(String accountName, boolean successful, int totalContacts, int updatedContacts,
		int deletedContacts, boolean dryRun, long durationMs, String message) {

	static AccountCleanupResult failure(String accountName, long durationMs, String message) {
		return new AccountCleanupResult(accountName, false, 0, 0, 0, false, durationMs, message);
	}
}
