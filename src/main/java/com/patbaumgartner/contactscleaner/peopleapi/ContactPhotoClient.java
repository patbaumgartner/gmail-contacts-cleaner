package com.patbaumgartner.contactscleaner.peopleapi;

import com.patbaumgartner.contactscleaner.account.GoogleAccount;

/** Prefers an available Google profile image over a contact-specific photo. */
public interface ContactPhotoClient {

	/**
	 * Removes contact-specific photos only when Google supplies a non-default profile
	 * photo for the same person.
	 * @param account account whose OAuth credentials authorize the updates
	 * @return the outcome of the photo preference operation
	 */
	GoogleProfilePhotoResult preferGoogleProfilePhotos(GoogleAccount account);

}