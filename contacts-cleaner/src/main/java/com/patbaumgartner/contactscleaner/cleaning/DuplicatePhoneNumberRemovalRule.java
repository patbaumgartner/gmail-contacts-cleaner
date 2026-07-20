package com.patbaumgartner.contactscleaner.cleaning;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import ezvcard.VCard;
import ezvcard.property.Telephone;

/**
 * Removes duplicate phone numbers within a single contact, keeping the first
 * occurrence. Intended to run <em>after</em> {@link PhoneNumberNormalizationRule} so
 * that {@code "+41 44 668 18 00"} and {@code "0041446681800"} are recognized as equal.
 */
final class DuplicatePhoneNumberRemovalRule implements VCardCleaningRule {

	@Override
	public boolean apply(VCard vcard) {
		Set<String> seen = new HashSet<>();
		boolean changed = false;
		for (Iterator<Telephone> iterator = vcard.getTelephoneNumbers().iterator(); iterator.hasNext();) {
			Telephone telephone = iterator.next();
			if (telephone.getText() != null && !seen.add(telephone.getText())) {
				iterator.remove();
				changed = true;
			}
		}
		return changed;
	}

}
