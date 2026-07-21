package com.patbaumgartner.contactscleaner.cleaning;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ezvcard.VCard;
import ezvcard.property.Email;
import ezvcard.property.FormattedName;
import ezvcard.property.StructuredName;

/**
 * Fills missing name components when a contact's e-mail address unambiguously follows the
 * {@code first.last@example.com} or {@code first_last@example.com} convention.
 */
final class EmailNameInferenceRule implements VCardCleaningRule {

	private static final Pattern FIRST_LAST_LOCAL_PART = Pattern
		.compile("^([A-Za-z][A-Za-z0-9'-]*)[._]([A-Za-z][A-Za-z0-9'-]*)$");

	@Override
	public boolean apply(VCard vcard) {
		StructuredName name = vcard.getStructuredName();
		if (name != null && !isBlank(name.getGiven()) && !isBlank(name.getFamily())) {
			return false;
		}

		NameParts inferred = inferUniqueName(vcard);
		if (inferred == null) {
			return false;
		}
		if (name == null) {
			name = new StructuredName();
			vcard.setStructuredName(name);
		}
		boolean changed = false;
		if (isBlank(name.getGiven())) {
			name.setGiven(inferred.given());
			changed = true;
		}
		if (isBlank(name.getFamily())) {
			name.setFamily(inferred.family());
			changed = true;
		}
		if (changed && (vcard.getFormattedName() == null || isBlank(vcard.getFormattedName().getValue()))) {
			vcard.setFormattedName(new FormattedName(name.getGiven() + " " + name.getFamily()));
		}
		return changed;
	}

	private NameParts inferUniqueName(VCard vcard) {
		NameParts inferred = null;
		for (Email email : vcard.getEmails()) {
			NameParts candidate = namePartsOf(email.getValue());
			if (candidate == null) {
				continue;
			}
			if (inferred != null && !inferred.equals(candidate)) {
				return null;
			}
			inferred = candidate;
		}
		return inferred;
	}

	private NameParts namePartsOf(String email) {
		if (email == null) {
			return null;
		}
		int at = email.indexOf('@');
		if (at <= 0 || at != email.lastIndexOf('@')) {
			return null;
		}
		Matcher matcher = FIRST_LAST_LOCAL_PART.matcher(email.substring(0, at));
		return matcher.matches() ? new NameParts(capitalize(matcher.group(1)), capitalize(matcher.group(2))) : null;
	}

	private String capitalize(String value) {
		String lower = value.toLowerCase(Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private record NameParts(String given, String family) {
	}

}