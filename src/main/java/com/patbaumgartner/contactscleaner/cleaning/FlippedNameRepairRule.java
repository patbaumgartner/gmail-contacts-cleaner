package com.patbaumgartner.contactscleaner.cleaning;

import java.util.List;
import java.util.Locale;

import ezvcard.VCard;
import ezvcard.property.Email;
import ezvcard.property.FormattedName;
import ezvcard.property.StructuredName;

/**
 * Repairs given/family names that were entered in the wrong order — but only when the
 * contact's own e-mail address <em>proves</em> the correct order.
 *
 * <p>
 * Example: the card says given = {@code Girba}, family = {@code Tudor}, and the contact's
 * e-mail is {@code tudor.girba@example.com}. The local part matches <em>family.given</em>
 * ({@code tudor.girba}) but not <em>given.family</em> ({@code girba.tudor}) — unambiguous
 * evidence that the fields are flipped, so they are swapped and the formatted name is
 * rebuilt.
 *
 * <p>
 * Without such evidence — or with ambiguous evidence (both orders match, neither matches)
 * — the name is never touched. Contacts whose duplicates simply differ in name order are
 * <em>not</em> repaired here; they show up in the duplicate report for Google's own merge
 * tool.
 */
final class FlippedNameRepairRule implements VCardCleaningRule {

	@Override
	public boolean apply(VCard vcard) {
		StructuredName name = vcard.getStructuredName();
		if (name == null || isBlank(name.getGiven()) || isBlank(name.getFamily())) {
			return false;
		}
		String given = normalizeToken(name.getGiven());
		String family = normalizeToken(name.getFamily());
		if (given.isEmpty() || family.isEmpty() || given.equals(family)) {
			return false;
		}

		boolean correctOrderSeen = false;
		boolean flippedOrderSeen = false;
		for (Email email : vcard.getEmails()) {
			String localPart = localPartOf(email.getValue());
			if (localPart == null) {
				continue;
			}
			correctOrderSeen |= matchesOrder(localPart, given, family);
			flippedOrderSeen |= matchesOrder(localPart, family, given);
		}
		if (!flippedOrderSeen || correctOrderSeen) {
			// No evidence, or ambiguous evidence — never guess.
			return false;
		}

		String repairedGiven = name.getFamily();
		String repairedFamily = name.getGiven();
		name.setGiven(repairedGiven);
		name.setFamily(repairedFamily);
		vcard.setFormattedName(new FormattedName(formattedNameOf(repairedGiven, repairedFamily, name)));
		return true;
	}

	/**
	 * Whether the e-mail local part spells {@code first<sep>second} (dot, dash,
	 * underscore or nothing).
	 */
	private boolean matchesOrder(String localPart, String first, String second) {
		return localPart.equals(first + "." + second) || localPart.equals(first + "-" + second)
				|| localPart.equals(first + "_" + second) || localPart.equals(first + second);
	}

	private String localPartOf(String address) {
		if (address == null) {
			return null;
		}
		int at = address.indexOf('@');
		if (at <= 0) {
			return null;
		}
		return normalizeToken(address.substring(0, at));
	}

	/** Lower-case and fold accents so 'Bolboacă' matches 'bolboaca' in an address. */
	private String normalizeToken(String value) {
		if (value == null) {
			return "";
		}
		String folded = java.text.Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT),
				java.text.Normalizer.Form.NFKD);
		return folded.replaceAll("\\p{M}", "");
	}

	private String formattedNameOf(String given, String family, StructuredName name) {
		List<String> parts = new java.util.ArrayList<>();
		parts.add(given);
		parts.addAll(name.getAdditionalNames());
		parts.add(family);
		return String.join(" ", parts).replaceAll("\\s+", " ").trim();
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
