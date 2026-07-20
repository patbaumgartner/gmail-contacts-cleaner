package com.patbaumgartner.contactscleaner.cleaning;

import java.util.Objects;

import ezvcard.VCard;
import ezvcard.property.Telephone;

/**
 * Normalizes phone numbers: removes separator characters (spaces, dashes, dots,
 * parentheses) and rewrites the international call prefix {@code 00} to {@code +}.
 *
 * <p>
 * Examples: {@code "044 668-18 00"} → {@code "0446681800"}, {@code "0041 44 668 18 00"}
 * → {@code "+41446681800"}.
 */
final class PhoneNumberNormalizationRule implements VCardCleaningRule {

	@Override
	public boolean apply(VCard vcard) {
		boolean changed = false;
		for (Telephone telephone : vcard.getTelephoneNumbers()) {
			String original = telephone.getText();
			String normalized = normalize(original);
			if (!Objects.equals(original, normalized)) {
				telephone.setText(normalized);
				changed = true;
			}
		}
		return changed;
	}

	/**
	 * Normalizes a single phone number string.
	 * @param phoneNumber the raw phone number; may be {@code null}
	 * @return the normalized number, or {@code null} if the input was {@code null}
	 */
	String normalize(String phoneNumber) {
		if (phoneNumber == null) {
			return null;
		}
		String normalized = phoneNumber.replaceAll("[\\s\\-./()]", "").trim();
		if (normalized.startsWith("00")) {
			normalized = "+" + normalized.substring(2);
		}
		return normalized;
	}

}
