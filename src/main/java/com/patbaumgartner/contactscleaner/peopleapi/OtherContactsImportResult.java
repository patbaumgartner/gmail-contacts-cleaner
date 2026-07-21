package com.patbaumgartner.contactscleaner.peopleapi;

/**
 * Summary of an Other contacts import operation.
 *
 * @param discovered number of Other contacts listed by the People API
 * @param promoted number copied to My Contacts
 */
public record OtherContactsImportResult(int discovered, int promoted) {

	public static final OtherContactsImportResult EMPTY = new OtherContactsImportResult(0, 0);

}