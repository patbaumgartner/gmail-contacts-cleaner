package com.patbaumgartner.contactscleaner.cleaning;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import ezvcard.VCard;
import ezvcard.property.FormattedName;
import ezvcard.property.StructuredName;

/**
 * Repairs structurally broken names — three well-defined defects, each fixed only when
 * the defect is unambiguous:
 *
 * <ol>
 * <li><strong>ALL-CAPS names</strong> — {@code "JANE DOE"} becomes {@code "Jane Doe"}
 * with smart casing: nobility particles stay lowercase ({@code van der}, {@code von}),
 * {@code McDonald}, {@code MacLeod} and {@code O'Brien} keep their inner capitals,
 * hyphenated names capitalize both parts. Mixed-case names are never touched.</li>
 * <li><strong>Prefix canonicalization</strong> — {@code Dr} → {@code Dr.}, {@code Prof} →
 * {@code Prof.} (known honorifics only).</li>
 * <li><strong>"Last, First" display names</strong> — {@code "Muster, Max"} becomes
 * {@code "Max Muster"}; the comma convention is unambiguous, and empty given/family
 * fields are populated from the parts.</li>
 * <li><strong>E-mail addresses stuck in name fields</strong> — the address is moved to
 * the contact's e-mails (if not already there) and removed from the name; a contact left
 * nameless gets a readable name derived from the local part ({@code jane.doe@…} →
 * {@code Jane Doe}).</li>
 * </ol>
 */
final class NameRepairRule implements VCardCleaningRule {

	private static final Set<String> LOWERCASE_PARTICLES = Set.of("van", "von", "der", "den", "de", "di", "da", "del",
			"della", "le", "la", "du", "dos", "das", "ter", "ten", "zu", "und", "y", "e");

	private static final Map<String, String> CANONICAL_PREFIXES = Map.of("dr", "Dr.", "prof", "Prof.", "ing", "Ing.",
			"mag", "Mag.", "med", "med.");

	private static final Pattern EMAIL_TOKEN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$");

	@Override
	public boolean apply(VCard vcard) {
		boolean changed = false;
		changed |= rescueEmailsFromNameFields(vcard);
		changed |= repairCommaFormattedDisplayName(vcard);
		changed |= repairAllCapsComponents(vcard);
		changed |= canonicalizePrefixes(vcard);
		return changed;
	}

	// ── "Last, First" display names ───────────────────────────────────────────

	/**
	 * {@code "Muster, Max"} → {@code "Max Muster"}. Applies only to the unambiguous
	 * single-comma form with two non-empty parts; anything fancier (multiple commas,
	 * suffix notation) is left alone.
	 */
	private boolean repairCommaFormattedDisplayName(VCard vcard) {
		FormattedName formatted = vcard.getFormattedName();
		if (formatted == null || formatted.getValue() == null) {
			return false;
		}
		String value = formatted.getValue().trim();
		int comma = value.indexOf(',');
		if (comma <= 0 || value.indexOf(',', comma + 1) >= 0 || value.contains("@")) {
			return false;
		}
		String family = value.substring(0, comma).trim();
		String given = value.substring(comma + 1).trim();
		if (family.isEmpty() || given.isEmpty() || looksLikeOrganization(given) || looksLikeOrganization(family)) {
			return false;
		}
		// If a structured name exists and contradicts the comma reading
		// ("family, given"), trust the structured name and do nothing.
		StructuredName name = vcard.getStructuredName();
		if (name != null && !isBlank(name.getGiven()) && !isBlank(name.getFamily())
				&& !(equalsIgnoreCaseTrimmed(name.getFamily(), family)
						&& equalsIgnoreCaseTrimmed(name.getGiven(), given))) {
			return false;
		}
		formatted.setValue(given + " " + family);
		if (name == null) {
			name = new StructuredName();
			vcard.setStructuredName(name);
		}
		if (isBlank(name.getGiven())) {
			name.setGiven(given);
		}
		if (isBlank(name.getFamily())) {
			name.setFamily(family);
		}
		return true;
	}

	/** "Müller & Partner AG" is a company fragment, not a first name. */
	private boolean looksLikeOrganization(String part) {
		return part.contains("&") || part
			.matches("(?i).*\\b(ag|gmbh|inc|ltd|llc|sa|kg|co|plc|sarl|sagl|partner|partners|associates)\\b\\.?.*");
	}

	private boolean equalsIgnoreCaseTrimmed(String first, String second) {
		return first != null && second != null && first.trim().equalsIgnoreCase(second.trim());
	}

	// ── E-mail in name ────────────────────────────────────────────────────────

	private boolean rescueEmailsFromNameFields(VCard vcard) {
		StructuredName name = vcard.getStructuredName();
		boolean changed = false;
		String rescued = null;

		if (name != null) {
			if (isEmail(name.getGiven())) {
				rescued = name.getGiven().trim();
				name.setGiven(null);
				changed = true;
			}
			if (isEmail(name.getFamily())) {
				rescued = name.getFamily().trim();
				name.setFamily(null);
				changed = true;
			}
		}
		FormattedName formatted = vcard.getFormattedName();
		if (formatted != null && isEmail(formatted.getValue())) {
			rescued = formatted.getValue().trim();
			changed = true;
		}
		if (rescued == null) {
			return false;
		}

		String address = rescued.toLowerCase(Locale.ROOT);
		boolean known = vcard.getEmails()
			.stream()
			.anyMatch((email) -> email.getValue() != null
					&& email.getValue().trim().toLowerCase(Locale.ROOT).equals(address));
		if (!known) {
			vcard.addEmail(address);
		}
		rebuildNameIfEmpty(vcard, address);
		return changed;
	}

	/** A contact must not end up nameless — derive "Jane Doe" from "jane.doe@…". */
	private void rebuildNameIfEmpty(VCard vcard, String address) {
		StructuredName name = vcard.getStructuredName();
		boolean hasName = name != null && (!isBlank(name.getGiven()) || !isBlank(name.getFamily()));
		if (!hasName) {
			String localPart = address.substring(0, address.indexOf('@'));
			String[] tokens = localPart.split("[._-]+");
			StringBuilder derived = new StringBuilder();
			for (String token : tokens) {
				if (!token.isBlank()) {
					if (!derived.isEmpty()) {
						derived.append(' ');
					}
					derived.append(capitalize(token));
				}
			}
			vcard.setFormattedName(new FormattedName(derived.toString()));
		}
		else {
			String rebuilt = ((isBlank(name.getGiven()) ? "" : name.getGiven()) + " "
					+ (isBlank(name.getFamily()) ? "" : name.getFamily()))
				.trim();
			vcard.setFormattedName(new FormattedName(rebuilt));
		}
	}

	// ── ALL-CAPS repair ───────────────────────────────────────────────────────

	private boolean repairAllCapsComponents(VCard vcard) {
		boolean changed = false;
		StructuredName name = vcard.getStructuredName();
		if (name != null) {
			String combined = ((name.getGiven() != null ? name.getGiven() : "") + " "
					+ (name.getFamily() != null ? name.getFamily() : ""))
				.trim();
			if (qualifiesForRepair(combined)) {
				String given = recase(name.getGiven());
				if (!Objects.equals(given, name.getGiven())) {
					name.setGiven(given);
					changed = true;
				}
				String family = recase(name.getFamily());
				if (!Objects.equals(family, name.getFamily())) {
					name.setFamily(family);
					changed = true;
				}
			}
		}
		FormattedName formatted = vcard.getFormattedName();
		if (formatted != null && qualifiesForRepair(formatted.getValue())) {
			String value = recase(formatted.getValue());
			if (!Objects.equals(value, formatted.getValue())) {
				formatted.setValue(value);
				changed = true;
			}
		}
		return changed;
	}

	/**
	 * A name qualifies only when it is entirely upper-case AND contains at least one real
	 * word (three or more letters) — initials like {@code "JD NG"} are left alone, they
	 * might be deliberate.
	 */
	private boolean qualifiesForRepair(String value) {
		if (value == null || value.isBlank() || !value.equals(value.toUpperCase(Locale.ROOT))) {
			return false;
		}
		for (String token : value.split("[\s-]+")) {
			if (token.chars().filter(Character::isLetter).count() >= 3) {
				return true;
			}
		}
		return false;
	}

	private String recase(String value) {
		if (value == null) {
			return null;
		}
		StringBuilder result = new StringBuilder();
		for (String token : value.split(" ")) {
			if (!result.isEmpty()) {
				result.append(' ');
			}
			result.append(smartCase(token));
		}
		return result.toString();
	}

	private String smartCase(String token) {
		String lower = token.toLowerCase(Locale.ROOT);
		if (LOWERCASE_PARTICLES.contains(lower)) {
			return lower;
		}
		if (lower.contains("-")) {
			String[] parts = lower.split("-", -1);
			for (int i = 0; i < parts.length; i++) {
				parts[i] = smartCase(parts[i]);
			}
			return String.join("-", parts);
		}
		if (lower.startsWith("mc") && lower.length() > 2) {
			return "Mc" + capitalize(lower.substring(2));
		}
		if (lower.startsWith("o'") && lower.length() > 2) {
			return "O'" + capitalize(lower.substring(2));
		}
		return capitalize(lower);
	}

	// ── Prefix canonicalization ───────────────────────────────────────────────

	private boolean canonicalizePrefixes(VCard vcard) {
		StructuredName name = vcard.getStructuredName();
		if (name == null || name.getPrefixes().isEmpty()) {
			return false;
		}
		boolean changed = false;
		List<String> prefixes = name.getPrefixes();
		for (int i = 0; i < prefixes.size(); i++) {
			String prefix = prefixes.get(i);
			if (prefix == null) {
				continue;
			}
			String canonical = CANONICAL_PREFIXES.get(prefix.trim().toLowerCase(Locale.ROOT).replaceAll("\\.$", ""));
			if (canonical != null && !canonical.equals(prefix)) {
				prefixes.set(i, canonical);
				changed = true;
			}
		}
		return changed;
	}

	private String capitalize(String token) {
		return token.isEmpty() ? token : Character.toUpperCase(token.charAt(0)) + token.substring(1);
	}

	private boolean isEmail(String value) {
		return value != null && EMAIL_TOKEN.matcher(value.trim()).matches();
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
