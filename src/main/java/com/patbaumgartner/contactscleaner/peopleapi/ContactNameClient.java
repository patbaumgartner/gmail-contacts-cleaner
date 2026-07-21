package com.patbaumgartner.contactscleaner.peopleapi;

import com.patbaumgartner.contactscleaner.account.GoogleAccount;

/** Repairs safely identifiable comma-form names stored in Google contacts. */
public interface ContactNameClient {

	/**
	 * Updates only writable contact-source names that safely match {@code Last, First}.
	 * @param account account whose OAuth credentials authorize the updates
	 * @return the outcome of the name repair operation
	 */
	GoogleContactNameResult repairCommaFormattedContactNames(GoogleAccount account);

}