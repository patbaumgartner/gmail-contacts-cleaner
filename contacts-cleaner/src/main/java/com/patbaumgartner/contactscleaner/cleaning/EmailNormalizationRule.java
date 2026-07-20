package com.patbaumgartner.contactscleaner.cleaning;

import java.util.Locale;
import java.util.Objects;

import ezvcard.VCard;
import ezvcard.property.Email;

/**
 * Normalizes e-mail addresses: trims whitespace and lower-cases the address.
 * E-mail addresses are case-insensitive in practice, and Google treats
 * {@code Jane.Doe@gmail.com} and {@code jane.doe@gmail.com} as the same mailbox.
 */
final class EmailNormalizationRule implements VCardCleaningRule {

	@Override
	public boolean apply(VCard vcard) {
		boolean changed = false;
		for (Email email : vcard.getEmails()) {
			String original = email.getValue();
			if (original == null) {
				continue;
			}
			String normalized = original.trim().toLowerCase(Locale.ROOT);
			if (!Objects.equals(original, normalized)) {
				email.setValue(normalized);
				changed = true;
			}
		}
		return changed;
	}

}
