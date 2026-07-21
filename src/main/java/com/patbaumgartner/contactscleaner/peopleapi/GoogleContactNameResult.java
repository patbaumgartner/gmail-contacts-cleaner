package com.patbaumgartner.contactscleaner.peopleapi;

/** Outcome of an optional direct Google People API contact-name repair pass. */
public record GoogleContactNameResult(int scanned, int updated, int skipped, int failed) {

	public static final GoogleContactNameResult EMPTY = new GoogleContactNameResult(0, 0, 0, 0);

}