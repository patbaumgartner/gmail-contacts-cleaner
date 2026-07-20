package com.patbaumgartner.contactscleaner.cleaning;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ezvcard.VCard;
import ezvcard.parameter.AddressType;
import ezvcard.parameter.EmailType;
import ezvcard.property.Address;
import ezvcard.property.Email;
import ezvcard.property.RawProperty;
import ezvcard.property.Url;
import ezvcard.property.VCardProperty;

/**
 * Normalizes custom labels on e-mail addresses and postal addresses to the standard vCard
 * types. Decades of imports leave Apple-style label debris like
 * {@code X-ABLabel:Internet email}, {@code Obsolete} or localized labels
 * ({@code Geschäftlich}, {@code Sonstige}) that no client renders sensibly.
 *
 * <ul>
 * <li>labels that clearly mean work or home (localized variants included) become the
 * standard {@code TYPE=WORK} / {@code TYPE=HOME},</li>
 * <li>every other custom label ({@code Internet email}, {@code Obsolete},
 * {@code Conference}, ...) is dropped, leaving the default type,</li>
 * <li>the orphaned {@code X-ABLabel} is removed and the group cleared when no other
 * property still uses it.</li>
 * </ul>
 */
final class LabelNormalizationRule implements VCardCleaningRule {

	private static final Set<String> WORK_LABELS = Set.of("work", "büro", "buero", "office", "geschäftlich",
			"geschaeftlich", "arbeit", "business");

	private static final Set<String> HOME_LABELS = Set.of("home", "privat", "private", "zuhause");

	@Override
	public boolean apply(VCard vcard) {
		Map<String, RawProperty> labelsByGroup = labelsByGroup(vcard);
		if (labelsByGroup.isEmpty()) {
			return false;
		}
		boolean changed = false;
		Set<RawProperty> consumedLabels = new HashSet<>();

		for (Email email : vcard.getEmails()) {
			RawProperty label = labelsByGroup.get(email.getGroup());
			if (label != null) {
				String normalized = normalize(label.getValue());
				if (WORK_LABELS.contains(normalized)) {
					email.getTypes().add(EmailType.WORK);
				}
				else if (HOME_LABELS.contains(normalized)) {
					email.getTypes().add(EmailType.HOME);
				}
				email.setGroup(null);
				consumedLabels.add(label);
				changed = true;
			}
		}
		for (Address address : vcard.getAddresses()) {
			RawProperty label = labelsByGroup.get(address.getGroup());
			if (label != null) {
				String normalized = normalize(label.getValue());
				if (WORK_LABELS.contains(normalized)) {
					address.getTypes().add(AddressType.WORK);
				}
				else if (HOME_LABELS.contains(normalized)) {
					address.getTypes().add(AddressType.HOME);
				}
				address.setGroup(null);
				consumedLabels.add(label);
				changed = true;
			}
		}

		for (Url url : vcard.getUrls()) {
			RawProperty label = labelsByGroup.get(url.getGroup());
			if (label != null) {
				String normalized = normalize(label.getValue());
				if (WORK_LABELS.contains(normalized)) {
					url.setType("work");
				}
				else if (HOME_LABELS.contains(normalized)) {
					url.setType("home");
				}
				url.setGroup(null);
				consumedLabels.add(label);
				changed = true;
			}
		}

		// Orphaned labels: their labeled property is gone (e.g. removed by the URL
		// cleanup) — nothing left to describe.
		for (RawProperty label : labelsByGroup.values()) {
			if (!consumedLabels.contains(label) && !groupStillInUse(vcard, label.getGroup())) {
				consumedLabels.add(label);
				changed = true;
			}
		}

		for (RawProperty label : consumedLabels) {
			if (!groupStillInUse(vcard, label.getGroup())) {
				vcard.removeProperty(label);
			}
		}
		return changed;
	}

	private Map<String, RawProperty> labelsByGroup(VCard vcard) {
		Map<String, RawProperty> labels = new HashMap<>();
		for (RawProperty property : vcard.getExtendedProperties()) {
			if (property.getGroup() != null && "X-ABLabel".equalsIgnoreCase(property.getPropertyName())
					&& property.getValue() != null && !property.getValue().isBlank()) {
				labels.put(property.getGroup(), property);
			}
		}
		return labels;
	}

	/**
	 * Another property (e.g. a custom-labeled TEL) may still legitimately use the group.
	 */
	private boolean groupStillInUse(VCard vcard, String group) {
		for (VCardProperty property : vcard.getProperties()) {
			if (group.equals(property.getGroup())
					&& !(property instanceof RawProperty raw && "X-ABLabel".equalsIgnoreCase(raw.getPropertyName()))) {
				return true;
			}
		}
		return false;
	}

	/** "* Geschäftlich " → "geschäftlich" (Google prefixes preferred labels with '*'). */
	private String normalize(String label) {
		return label.replaceFirst("^\\s*\\*\\s*", "").trim().toLowerCase(Locale.ROOT);
	}

}
