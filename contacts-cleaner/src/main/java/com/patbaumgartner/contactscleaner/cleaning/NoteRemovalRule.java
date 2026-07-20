package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Note;

/**
 * Removes the free-text notes of a contact. This is destructive and therefore disabled
 * by default; enable it via {@code contacts-cleaner.cleaning.remove-notes=true}.
 */
final class NoteRemovalRule implements VCardCleaningRule {

	@Override
	public boolean apply(VCard vcard) {
		if (vcard.getNotes().isEmpty()) {
			return false;
		}
		vcard.removeProperties(Note.class);
		return true;
	}

}
