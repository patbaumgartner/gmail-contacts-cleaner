package com.patbaumgartner.contactscleaner.orchestration;

import java.util.List;

import com.patbaumgartner.contactscleaner.cleaning.DuplicateCandidate;

/**
 * Result of a cleanup run for a single account.
 *
 * @param accountName the configured account label
 * @param successful whether the run completed without errors
 * @param totalContacts number of contacts fetched from the address book
 * @param updatedContacts number of contacts that were modified and written back
 * @param deletedContacts number of empty contacts that were deleted
 * @param duplicateCandidates likely duplicate contact pairs found (report-only, never
 * acted upon automatically)
 * @param changes the individual contact modifications (or dry-run simulations), rendered
 * in the HTML report
 * @param dryRun whether this was a dry run (changes computed but not applied)
 * @param durationMs wall-clock duration of the run in milliseconds
 * @param message human-readable status message
 */
public record AccountCleanupResult(String accountName, boolean successful, int totalContacts, int updatedContacts,
		int deletedContacts, List<DuplicateCandidate> duplicateCandidates, List<ContactChange> changes, boolean dryRun,
		long durationMs, String message) {

	public AccountCleanupResult {
		duplicateCandidates = (duplicateCandidates != null) ? List.copyOf(duplicateCandidates) : List.of();
		changes = (changes != null) ? List.copyOf(changes) : List.of();
	}

	static AccountCleanupResult failure(String accountName, long durationMs, String message) {
		return new AccountCleanupResult(accountName, false, 0, 0, 0, List.of(), List.of(), false, durationMs, message);
	}
}
