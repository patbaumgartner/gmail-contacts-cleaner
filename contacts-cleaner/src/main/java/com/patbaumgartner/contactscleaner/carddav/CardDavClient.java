package com.patbaumgartner.contactscleaner.carddav;

import java.util.List;

import com.patbaumgartner.contactscleaner.account.GoogleAccount;

/**
 * Client abstraction over the Google CardDAV address book of a single account.
 *
 * <p>
 * All operations authenticate with HTTP Basic auth using the account's app password —
 * exactly the mechanism iOS/macOS contact sync uses. No OAuth consent screen or Google
 * Cloud project is required.
 */
public interface CardDavClient {

	/**
	 * Fetches every contact of the account's default address book, including etags and
	 * raw vCard payloads.
	 * @param account the account whose address book should be read
	 * @return all vCard resources, never {@code null}
	 * @throws CardDavException if the request fails
	 */
	List<AddressBookEntry> fetchAllContacts(GoogleAccount account);

	/**
	 * Replaces a contact with the given vCard payload. The update is conditional on the
	 * entry's etag ({@code If-Match}), so a concurrently modified contact is never
	 * silently overwritten.
	 * @param account the owning account
	 * @param entry the entry being updated (href and etag are used)
	 * @param vcard the new vCard payload
	 * @throws CardDavException if the request fails
	 */
	void updateContact(GoogleAccount account, AddressBookEntry entry, String vcard);

	/**
	 * Deletes a contact, conditional on its etag ({@code If-Match}).
	 * @param account the owning account
	 * @param entry the entry to delete
	 * @throws CardDavException if the request fails
	 */
	void deleteContact(GoogleAccount account, AddressBookEntry entry);

}
