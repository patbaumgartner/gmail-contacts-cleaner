package com.patbaumgartner.contactscleaner.cleaning;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import ezvcard.VCard;
import ezvcard.property.Email;

/**
 * Removes duplicate e-mail addresses within a single contact, keeping the first
 * occurrence. Intended to run <em>after</em> {@link EmailNormalizationRule} so that
 * addresses differing only in case or surrounding whitespace are recognized as equal.
 */
final class DuplicateEmailRemovalRule implements VCardCleaningRule {

	@Override
	public boolean apply(VCard vcard) {
		Set<String> seen = new HashSet<>();
		boolean changed = false;
		for (Iterator<Email> iterator = vcard.getEmails().iterator(); iterator.hasNext();) {
			Email email = iterator.next();
			if (email.getValue() != null && !seen.add(email.getValue())) {
				iterator.remove();
				changed = true;
			}
		}
		return changed;
	}

}
