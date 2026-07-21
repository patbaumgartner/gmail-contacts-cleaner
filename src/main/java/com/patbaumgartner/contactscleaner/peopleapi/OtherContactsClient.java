package com.patbaumgartner.contactscleaner.peopleapi;

import com.patbaumgartner.contactscleaner.account.GoogleAccount;

/**
 * Promotes a Google account's Other contacts into its My Contacts group.
 */
public interface OtherContactsClient {

	/**
	 * Lists all Other contacts and promotes them sequentially to My Contacts.
	 * @param account account whose OAuth credentials authorize the import
	 * @return the import outcome
	 */
	OtherContactsImportResult importOtherContacts(GoogleAccount account);

}