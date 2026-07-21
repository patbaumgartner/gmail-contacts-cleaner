package com.patbaumgartner.contactscleaner.peopleapi;

/**
 * Raised when the People API cannot list or promote Other contacts.
 */
public class OtherContactsException extends RuntimeException {

	public OtherContactsException(String message) {
		super(message);
	}

	public OtherContactsException(String message, Throwable cause) {
		super(message, cause);
	}

}