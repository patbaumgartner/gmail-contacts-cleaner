package com.patbaumgartner.contactscleaner.cleaning;

import java.util.Iterator;
import java.util.Locale;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import ezvcard.VCard;
import ezvcard.property.Telephone;

/**
 * Removes phone numbers that are not valid for their country — leftovers of truncated SIM
 * imports ({@code "12 9001"}), star codes ({@code "*133#"}) and other fragments that can
 * never be dialed.
 *
 * <p>
 * Validation is delegated to Google's libphonenumber, the same ruleset phones use: a
 * number is removed only when it parses <em>and</em> is provably invalid (wrong length
 * for its region, impossible prefix), or when it cannot be parsed at all. Judgment
 * requires context:
 * <ul>
 * <li>numbers in international format ({@code +...}) are always validated,</li>
 * <li>national numbers are validated only when
 * {@code contacts-cleaner.cleaning.phone-region} is configured — without a region a local
 * number cannot be judged and is kept.</li>
 * </ul>
 *
 * Destructive, therefore disabled by default; enable via
 * {@code contacts-cleaner.cleaning.remove-invalid-phone-numbers=true} and review the
 * dry-run report first. Intended to run <em>after</em>
 * {@link PhoneNumberNormalizationRule}, so separators and bidi marks are already gone.
 */
final class InvalidPhoneNumberRemovalRule implements VCardCleaningRule {

	private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

	private final String phoneRegion;

	InvalidPhoneNumberRemovalRule(String phoneRegion) {
		this.phoneRegion = (phoneRegion != null) ? phoneRegion.trim().toUpperCase(Locale.ROOT) : "";
	}

	@Override
	public boolean apply(VCard vcard) {
		boolean changed = false;
		for (Iterator<Telephone> iterator = vcard.getTelephoneNumbers().iterator(); iterator.hasNext();) {
			String number = iterator.next().getText();
			if (number != null && !number.isBlank() && isProvablyInvalid(number.trim())) {
				iterator.remove();
				changed = true;
			}
		}
		return changed;
	}

	/**
	 * Whether the number is judgeable and judged invalid.
	 * @param number the (already normalized) phone number
	 * @return {@code true} if the number should be removed
	 */
	boolean isProvablyInvalid(String number) {
		boolean international = number.startsWith("+");
		if (!international && this.phoneRegion.isEmpty()) {
			// A national number without a configured region cannot be judged.
			return false;
		}
		try {
			return !this.phoneNumberUtil
				.isValidNumber(this.phoneNumberUtil.parse(number, international ? null : this.phoneRegion));
		}
		catch (NumberParseException ex) {
			// Unparseable fragments ("*133#", "12 9001", letters) can never be dialed.
			return true;
		}
	}

}
