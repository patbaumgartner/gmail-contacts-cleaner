package com.patbaumgartner.contactscleaner.cleaning;

import java.util.Locale;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import ezvcard.VCard;
import ezvcard.parameter.TelephoneType;
import ezvcard.property.Telephone;

/**
 * Corrects the mobile/landline classification of phone numbers against the actual
 * numbering plan (via libphonenumber): a Swiss {@code 079…} number labeled "Work" is a
 * mobile no matter what the label says, and a {@code 044…} number carrying the mobile
 * type is a landline.
 *
 * <ul>
 * <li>a provably <strong>mobile</strong> number gets {@code TYPE=CELL} (existing
 * WORK/HOME context is kept — a work mobile stays a work mobile),</li>
 * <li>a provably <strong>fixed-line</strong> number loses a wrong
 * {@code TYPE=CELL}/{@code PAGER},</li>
 * <li>numbers in plans that do not distinguish the two (e.g. US:
 * {@code FIXED_LINE_OR_MOBILE}), unparseable numbers, and national numbers without a
 * configured {@code phone-region} are never touched.</li>
 * </ul>
 *
 * Runs after {@link PhoneNumberNormalizationRule}, so numbers are already in a parseable
 * shape.
 */
final class PhoneTypeCorrectionRule implements VCardCleaningRule {

	private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

	private final String phoneRegion;

	PhoneTypeCorrectionRule(String phoneRegion) {
		this.phoneRegion = (phoneRegion != null) ? phoneRegion.trim().toUpperCase(Locale.ROOT) : "";
	}

	@Override
	public boolean apply(VCard vcard) {
		boolean changed = false;
		for (Telephone telephone : vcard.getTelephoneNumbers()) {
			changed |= correct(telephone);
		}
		return changed;
	}

	private boolean correct(Telephone telephone) {
		String number = telephone.getText();
		if (number == null || number.isBlank()) {
			return false;
		}
		PhoneNumberType type = typeOf(number.trim());
		if (type == PhoneNumberType.MOBILE) {
			if (!telephone.getTypes().contains(TelephoneType.CELL)) {
				telephone.getTypes().add(TelephoneType.CELL);
				return true;
			}
		}
		else if (type == PhoneNumberType.FIXED_LINE) {
			boolean removedCell = telephone.getTypes().remove(TelephoneType.CELL);
			boolean removedPager = telephone.getTypes().remove(TelephoneType.PAGER);
			return removedCell || removedPager;
		}
		return false;
	}

	/** {@code UNKNOWN} for anything we must not judge. */
	private PhoneNumberType typeOf(String number) {
		boolean international = number.startsWith("+");
		if (!international && this.phoneRegion.isEmpty()) {
			return PhoneNumberType.UNKNOWN;
		}
		try {
			var parsed = this.phoneNumberUtil.parse(number, international ? null : this.phoneRegion);
			if (!this.phoneNumberUtil.isValidNumber(parsed)) {
				return PhoneNumberType.UNKNOWN;
			}
			return this.phoneNumberUtil.getNumberType(parsed);
		}
		catch (NumberParseException ex) {
			return PhoneNumberType.UNKNOWN;
		}
	}

}
