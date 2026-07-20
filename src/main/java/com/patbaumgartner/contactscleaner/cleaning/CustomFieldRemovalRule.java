package com.patbaumgartner.contactscleaner.cleaning;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ezvcard.VCard;
import ezvcard.property.RawProperty;
import ezvcard.property.VCardProperty;

/**
 * Removes custom fields by label — e.g. a stale {@code Age} field written once by an
 * ancient sync tool and never updated since (an age that does not age is worse than no
 * age).
 *
 * <p>
 * Google exposes custom fields in vCard as Apple-style label groups:
 *
 * <pre>
 * item3.X-ABLabel:Age
 * item3.X-ABCustom:42
 * </pre>
 *
 * The rule finds groups whose {@code X-ABLabel} matches a configured label
 * (case-insensitive) and removes <em>every</em> property of that group, plus standalone
 * extended properties named {@code X-<label>} (e.g. {@code X-AGE}). Configure the labels
 * via {@code contacts-cleaner.cleaning.remove-custom-fields} (default: {@code Age}); an
 * empty list disables the rule.
 */
final class CustomFieldRemovalRule implements VCardCleaningRule {

	private final Set<String> labels;

	CustomFieldRemovalRule(List<String> labels) {
		Set<String> normalized = new HashSet<>();
		for (String label : labels) {
			if (label != null && !label.isBlank()) {
				normalized.add(label.trim().toLowerCase(Locale.ROOT));
			}
		}
		this.labels = Set.copyOf(normalized);
	}

	@Override
	public boolean apply(VCard vcard) {
		if (this.labels.isEmpty()) {
			return false;
		}
		Set<String> doomedGroups = findLabeledGroups(vcard);
		boolean changed = false;

		List<VCardProperty> doomed = new ArrayList<>();
		for (VCardProperty property : vcard.getProperties()) {
			if (property.getGroup() != null && doomedGroups.contains(property.getGroup())) {
				doomed.add(property);
			}
			else if (property instanceof RawProperty raw && raw.getPropertyName() != null
					&& this.labels.contains(stripXPrefix(raw.getPropertyName()))) {
				doomed.add(property);
			}
		}
		for (VCardProperty property : doomed) {
			vcard.removeProperty(property);
			changed = true;
		}
		return changed;
	}

	/** Groups whose {@code X-ABLabel} value matches one of the configured labels. */
	private Set<String> findLabeledGroups(VCard vcard) {
		Set<String> groups = new HashSet<>();
		for (RawProperty property : vcard.getExtendedProperties()) {
			if (property.getGroup() != null && "X-ABLabel".equalsIgnoreCase(property.getPropertyName())
					&& property.getValue() != null
					&& this.labels.contains(property.getValue().trim().toLowerCase(Locale.ROOT))) {
				groups.add(property.getGroup());
			}
		}
		return groups;
	}

	/** {@code X-AGE} → {@code age}. */
	private String stripXPrefix(String propertyName) {
		String name = propertyName.toLowerCase(Locale.ROOT);
		return name.startsWith("x-") ? name.substring(2) : name;
	}

}
