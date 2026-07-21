package com.patbaumgartner.contactscleaner.orchestration;

import java.util.List;

import com.patbaumgartner.contactscleaner.cleaning.DuplicateCandidate;
import com.patbaumgartner.contactscleaner.peopleapi.GoogleContactNameResult;
import com.patbaumgartner.contactscleaner.peopleapi.GoogleProfilePhotoResult;
import com.patbaumgartner.contactscleaner.peopleapi.OtherContactsImportResult;

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
 * @param otherContactsImport result of the optional Other Contacts import
 * @param googleProfilePhotos result of the optional profile photo preference operation
 * @param googleContactNames result of the optional direct Google contact-name repair
 * operation
 * @param dryRun whether this was a dry run (changes computed but not applied)
 * @param durationMs wall-clock duration of the run in milliseconds
 * @param message human-readable status message
 */
public record AccountCleanupResult(String accountName, boolean successful, int totalContacts, int updatedContacts,
		int deletedContacts, List<DuplicateCandidate> duplicateCandidates, List<ContactChange> changes,
		OtherContactsImportResult otherContactsImport, GoogleProfilePhotoResult googleProfilePhotos, boolean dryRun,
		GoogleContactNameResult googleContactNames, long durationMs, String message) {

	public AccountCleanupResult {
		duplicateCandidates = (duplicateCandidates != null) ? List.copyOf(duplicateCandidates) : List.of();
		changes = (changes != null) ? List.copyOf(changes) : List.of();
		otherContactsImport = (otherContactsImport != null) ? otherContactsImport : OtherContactsImportResult.EMPTY;
		googleProfilePhotos = (googleProfilePhotos != null) ? googleProfilePhotos : GoogleProfilePhotoResult.EMPTY;
		googleContactNames = (googleContactNames != null) ? googleContactNames : GoogleContactNameResult.EMPTY;
	}

	public AccountCleanupResult(String accountName, boolean successful, int totalContacts, int updatedContacts,
			int deletedContacts, List<DuplicateCandidate> duplicateCandidates, List<ContactChange> changes,
			OtherContactsImportResult otherContactsImport, GoogleProfilePhotoResult googleProfilePhotos, boolean dryRun,
			long durationMs, String message) {
		this(accountName, successful, totalContacts, updatedContacts, deletedContacts, duplicateCandidates, changes,
				otherContactsImport, googleProfilePhotos, dryRun, GoogleContactNameResult.EMPTY, durationMs, message);
	}

	static AccountCleanupResult failure(String accountName, OtherContactsImportResult otherContactsImport,
			GoogleProfilePhotoResult googleProfilePhotos, GoogleContactNameResult googleContactNames, long durationMs,
			String message) {
		return new AccountCleanupResult(accountName, false, 0, 0, 0, List.of(), List.of(), otherContactsImport,
				googleProfilePhotos, false, googleContactNames, durationMs, message);
	}
}
