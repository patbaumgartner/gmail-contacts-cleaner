package com.patbaumgartner.contactscleaner.cleaning;

import java.util.List;
import java.util.Objects;

import ezvcard.VCard;
import ezvcard.property.FormattedName;
import ezvcard.property.StructuredName;

/**
 * Trims leading and trailing whitespace around given, family, additional (middle) and
 * formatted names. Stray whitespace is a classic artifact of years of contact syncing
 * between devices.
 */
final class NameTrimmingRule implements VCardCleaningRule {

	@Override
	public boolean apply(VCard vcard) {
		boolean changed = false;

		StructuredName name = vcard.getStructuredName();
		if (name != null) {
			changed |= trimComponent(name.getGiven(), name::setGiven);
			changed |= trimComponent(name.getFamily(), name::setFamily);
			changed |= trimList(name.getAdditionalNames());
		}

		FormattedName formattedName = vcard.getFormattedName();
		if (formattedName != null && formattedName.getValue() != null) {
			String trimmed = formattedName.getValue().trim();
			if (!Objects.equals(formattedName.getValue(), trimmed)) {
				formattedName.setValue(trimmed);
				changed = true;
			}
		}
		return changed;
	}

	private boolean trimComponent(String value, java.util.function.Consumer<String> setter) {
		if (value == null) {
			return false;
		}
		String trimmed = value.trim();
		if (!Objects.equals(value, trimmed)) {
			setter.accept(trimmed);
			return true;
		}
		return false;
	}

	private boolean trimList(List<String> values) {
		boolean changed = false;
		for (int i = 0; i < values.size(); i++) {
			String value = values.get(i);
			if (value != null && !Objects.equals(value, value.trim())) {
				values.set(i, value.trim());
				changed = true;
			}
		}
		return changed;
	}

}
