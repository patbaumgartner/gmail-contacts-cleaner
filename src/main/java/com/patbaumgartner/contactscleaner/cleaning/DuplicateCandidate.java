package com.patbaumgartner.contactscleaner.cleaning;

/**
 * A pair of contacts that appear to describe the same person. Report-only: the cleaner
 * never merges or deletes them — the candidates are surfaced in the run summary so the
 * user can merge them deliberately in the Google Contacts UI.
 *
 * @param firstContact display name of the first contact
 * @param secondContact display name of the second contact
 * @param reason human-readable reason for the match (e.g. shared phone number)
 */
public record DuplicateCandidate(String firstContact, String secondContact, String reason) {
}
