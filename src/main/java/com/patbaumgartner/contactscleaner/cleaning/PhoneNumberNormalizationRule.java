package com.patbaumgartner.contactscleaner.cleaning;

import java.util.Objects;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import ezvcard.VCard;
import ezvcard.property.Telephone;

/**
 * Normalizes phone numbers in two stages:
 *
 * <ol>
 * <li><strong>Separator stripping</strong> — removes spaces, dashes, dots and parentheses
 * and rewrites the international call prefix {@code 00} to {@code +}.</li>
 * <li><strong>E.164 formatting</strong> (optional) — when a default phone region is
 * configured, numbers are parsed with Google's libphonenumber and rendered in E.164, so
 * the national form {@code 044 668 18 00} becomes {@code +41446681800}. Numbers that
 * cannot be parsed or are not valid keep the stripped form — the rule never destroys a
 * value it does not understand.</li>
 * </ol>
 */
final class PhoneNumberNormalizationRule implements VCardCleaningRule {

	private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

	private final String phoneRegion;

	/**
	 * Creates the rule.
	 * @param phoneRegion ISO 3166-1 alpha-2 region for parsing national numbers; blank
	 * disables E.164 formatting
	 */
	PhoneNumberNormalizationRule(String phoneRegion) {
		this.phoneRegion = (phoneRegion != null) ? phoneRegion.trim().toUpperCase(java.util.Locale.ROOT) : "";
	}

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
		String stripped = phoneNumber.replaceAll("[\\s\\-./()]", "").trim();
		if (stripped.startsWith("00")) {
			stripped = "+" + stripped.substring(2);
		}
		if (this.phoneRegion.isEmpty() || stripped.isEmpty()) {
			return stripped;
		}
		return toE164(stripped);
	}

	private String toE164(String stripped) {
		try {
			PhoneNumber parsed = this.phoneNumberUtil.parse(stripped, this.phoneRegion);
			if (this.phoneNumberUtil.isValidNumber(parsed)) {
				return this.phoneNumberUtil.format(parsed, PhoneNumberFormat.E164);
			}
		}
		catch (NumberParseException ex) {
			// Not a parseable phone number (short code, alphanumeric, garbage) —
			// deliberately fall through and keep the stripped original.
		}
		return stripped;
	}

}
