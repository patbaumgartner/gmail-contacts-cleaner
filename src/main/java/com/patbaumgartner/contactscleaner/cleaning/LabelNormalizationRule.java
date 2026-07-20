package com.patbaumgartner.contactscleaner.cleaning;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ezvcard.VCard;
import ezvcard.parameter.AddressType;
import ezvcard.parameter.EmailType;
import ezvcard.parameter.TelephoneType;
import ezvcard.property.Address;
import ezvcard.property.Email;
import ezvcard.property.RawProperty;
import ezvcard.property.Url;
import ezvcard.property.VCardProperty;

/**
 * Normalizes custom labels on e-mail addresses, phone numbers and postal addresses to the
 * standard vCard types. Decades of imports leave Apple-style label debris like
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

	private static final Set<String> MOBILE_LABELS = Set.of("mobile", "mobil", "cell", "handy", "natel", "work mobile");

	@Override
	public boolean apply(VCard vcard) {
		boolean changed = removeGrouplessLabels(vcard);
		Map<String, RawProperty> labelsByGroup = labelsByGroup(vcard);
		if (labelsByGroup.isEmpty()) {
			return changed;
		}
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

		for (ezvcard.property.Telephone telephone : vcard.getTelephoneNumbers()) {
			RawProperty label = labelsByGroup.get(telephone.getGroup());
			if (label != null) {
				String normalized = normalize(label.getValue());
				if (normalized.contains("fax")) {
					// Preserve the semantics as the standard FAX type so the (later)
					// fax-removal rule still recognizes the number.
					telephone.getTypes().add(TelephoneType.FAX);
					if (WORK_LABELS.stream().anyMatch(normalized::contains)) {
						telephone.getTypes().add(TelephoneType.WORK);
					}
				}
				else if (MOBILE_LABELS.contains(normalized)) {
					telephone.getTypes().add(TelephoneType.CELL);
				}
				else if (WORK_LABELS.contains(normalized)) {
					telephone.getTypes().add(TelephoneType.WORK);
				}
				else if (HOME_LABELS.contains(normalized)) {
					telephone.getTypes().add(TelephoneType.HOME);
				}
				// Unknown labels ('WhatsApp', 'Old', 'Conference') → default type.
				telephone.setGroup(null);
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

	/** An X-ABLabel without a group labels nothing — pure sync debris. */
	private boolean removeGrouplessLabels(VCard vcard) {
		boolean changed = false;
		for (RawProperty property : java.util.List.copyOf(vcard.getExtendedProperties())) {
			if (property.getGroup() == null && "X-ABLabel".equalsIgnoreCase(property.getPropertyName())) {
				vcard.removeProperty(property);
				changed = true;
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
