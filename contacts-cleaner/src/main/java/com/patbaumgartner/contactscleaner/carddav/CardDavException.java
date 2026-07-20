package com.patbaumgartner.contactscleaner.carddav;

/**
 * Raised when a CardDAV request fails — network problems, authentication failures
 * (wrong app password), or unexpected server responses.
 */
public class CardDavException extends RuntimeException {

	public CardDavException(String message) {
		super(message);
	}

	public CardDavException(String message, Throwable cause) {
		super(message, cause);
	}

}
