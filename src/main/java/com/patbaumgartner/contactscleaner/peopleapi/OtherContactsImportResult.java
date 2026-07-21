package com.patbaumgartner.contactscleaner.peopleapi;

/**
 * Summary of an Other contacts import operation.
 *
 * @param discovered number of Other contacts listed by the People API
 * @param promoted number copied to My Contacts
 * @param skipped number already represented in My Contacts
 * @param failed number not copied because a People API request failed
 */
public record OtherContactsImportResult(int discovered, int promoted, int skipped, int failed) {

	public static final OtherContactsImportResult EMPTY = new OtherContactsImportResult(0, 0, 0, 0);

	public OtherContactsImportResult(int discovered, int promoted) {
		this(discovered, promoted, 0, 0);
	}

}