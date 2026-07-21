package com.patbaumgartner.contactscleaner.peopleapi;

/** Outcome of preferring Google-provided profile photos for an account. */
public record GoogleProfilePhotoResult(int scanned, int removed, int skipped, int failed) {

	public static final GoogleProfilePhotoResult EMPTY = new GoogleProfilePhotoResult(0, 0, 0, 0);

}