package com.patbaumgartner.contactscleaner.cleaning;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import ezvcard.VCard;
import ezvcard.property.FormattedName;
import ezvcard.property.StructuredName;

/**
 * Repairs structurally broken names — well-defined defects, each fixed only when the
 * defect is unambiguous:
 *
 * <ol>
 * <li><strong>Given and family name casing</strong> — all-uppercase components get smart
 * casing ({@code "MCDONALD"} becomes {@code "McDonald"}), while components that already
 * start upper-case retain intentional inner capitals ({@code "O'Brien"},
 * {@code "Jean-Luc"}).</li>
 * <li><strong>Prefix canonicalization</strong> — {@code Dr} → {@code Dr.}, {@code Prof} →
 * {@code Prof.} (known honorifics only).</li>
 * <li><strong>Quoted names</strong> — {@code "\"Jane Doe\""} loses its wrapping quotes
 * (double, single or typographic), another messenger-import artifact.</li>
 * <li><strong>"Last, First" display names</strong> — {@code "Muster, Max"} becomes
 * {@code "Max Muster"}; the comma convention is unambiguous, and empty given/family
 * fields are populated from the parts.</li>
 * <li><strong>Display-name e-mail suffixes</strong> — {@code "Jane Doe
 * (jane.doe@example.com)"} becomes {@code "Jane Doe"}; the e-mail is moved to the
 * contact's e-mails and an unambiguous two-part display name fills missing given/family
 * fields.</li>
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

	private static final Pattern DISPLAY_NAME_EMAIL_SUFFIX = Pattern
		.compile("^(.+?)\\s*\\(([^()\\s]+@[^()\\s]+\\.[A-Za-z]{2,})\\)\\s*$");

	private final boolean removeWrappingQuotes;

	private final boolean repairCommaFormattedNames;

	NameRepairRule() {
		this(true, true);
	}

	NameRepairRule(boolean removeWrappingQuotes, boolean repairCommaFormattedNames) {
		this.removeWrappingQuotes = removeWrappingQuotes;
		this.repairCommaFormattedNames = repairCommaFormattedNames;
	}

	@Override
	public boolean apply(VCard vcard) {
		boolean changed = false;
		if (removeWrappingQuotes) {
			changed |= sanitizeNames(vcard);
		}
		changed |= rescueEmailsFromNameFields(vcard);
		if (repairCommaFormattedNames) {
			changed |= repairCommaFormattedDisplayName(vcard);
		}
		changed |= normalizeStructuredNameCase(vcard);
		changed |= canonicalizePrefixes(vcard);
		return changed;
	}

	// ── Name sanitization: quotes, emojis, whitespace ─────────────────────────

	/**
	 * Quote characters stripped from name boundaries (straight, typographic, guillemets).
	 */
	private static final Pattern BOUNDARY_QUOTES = Pattern
		.compile("^[\\s\"'\u201c\u201d\u2018\u2019\u00ab\u00bb`´]+|[\\s\"'\u201c\u201d\u2018\u2019\u00ab\u00bb`´]+$");

	/**
	 * Emojis and other symbols that do not belong in a name: symbol characters (includes
	 * all pictographs), invisible format characters (ZWJ, bidi marks), variation
	 * selectors and skin-tone modifiers.
	 */
	private static final Pattern NAME_NOISE = Pattern
		.compile("[\\p{So}\\p{Cf}\\uFE0E\\uFE0F\\u20E3]|[\\uD83C\\uDFFB-\\uD83C\\uDFFF]");

	/**
	 * Cleans every name component: strips boundary quotes (also one-sided strays),
	 * removes emojis and invisible format characters, collapses whitespace and trims.
	 * Inner quotes ({@code Patrick "Pat" Miller}) are preserved; a component that would
	 * end up empty keeps its original value.
	 */
	private boolean sanitizeNames(VCard vcard) {
		boolean changed = false;
		FormattedName formatted = vcard.getFormattedName();
		if (formatted != null && formatted.getValue() != null) {
			String sanitized = sanitize(formatted.getValue());
			if (!sanitized.equals(formatted.getValue())) {
				formatted.setValue(sanitized);
				changed = true;
			}
		}
		StructuredName name = vcard.getStructuredName();
		if (name != null) {
			if (name.getGiven() != null && !sanitize(name.getGiven()).equals(name.getGiven())) {
				name.setGiven(sanitize(name.getGiven()));
				changed = true;
			}
			if (name.getFamily() != null && !sanitize(name.getFamily()).equals(name.getFamily())) {
				name.setFamily(sanitize(name.getFamily()));
				changed = true;
			}
			changed |= sanitizeList(name.getAdditionalNames());
			changed |= sanitizeList(name.getPrefixes());
			changed |= sanitizeList(name.getSuffixes());
		}
		return changed;
	}

	private boolean sanitizeList(List<String> values) {
		boolean changed = false;
		for (int i = 0; i < values.size(); i++) {
			String value = values.get(i);
			if (value != null && !sanitize(value).equals(value)) {
				values.set(i, sanitize(value));
				changed = true;
			}
		}
		return changed;
	}

	private String sanitize(String value) {
		String result = NAME_NOISE.matcher(value).replaceAll("");
		result = result.replace("\\\"", "\"").replace("\\'", "'");
		result = BOUNDARY_QUOTES.matcher(result).replaceAll("");
		result = result.replaceAll("\\s+", " ").trim();
		return result.isEmpty() ? value : result;
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
		if (formatted != null) {
			DisplayNameEmailSuffix suffix = displayNameEmailSuffixOf(formatted.getValue());
			if (suffix != null) {
				formatted.setValue(suffix.displayName());
				rescued = suffix.email();
				changed = true;
				changed |= populateMissingNameParts(vcard, suffix.displayName());
			}
			else if (isEmail(formatted.getValue())) {
				rescued = formatted.getValue().trim();
				changed = true;
			}
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

	private DisplayNameEmailSuffix displayNameEmailSuffixOf(String value) {
		if (value == null) {
			return null;
		}
		java.util.regex.Matcher matcher = DISPLAY_NAME_EMAIL_SUFFIX.matcher(value);
		if (!matcher.matches() || !isEmail(matcher.group(2))) {
			return null;
		}
		String displayName = matcher.group(1).trim();
		return displayName.isEmpty() ? null : new DisplayNameEmailSuffix(displayName, matcher.group(2));
	}

	private boolean populateMissingNameParts(VCard vcard, String displayName) {
		StructuredName name = vcard.getStructuredName();
		if (name != null && !isBlank(name.getGiven()) && !isBlank(name.getFamily())) {
			return false;
		}
		String[] parts = displayName.split("\\s+", 2);
		if (parts.length != 2 || looksLikeOrganization(displayName)) {
			return false;
		}
		if (name == null) {
			name = new StructuredName();
			vcard.setStructuredName(name);
		}
		boolean changed = false;
		if (isBlank(name.getGiven())) {
			name.setGiven(parts[0]);
			changed = true;
		}
		if (isBlank(name.getFamily())) {
			name.setFamily(parts[1]);
			changed = true;
		}
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

	// ── Given/family name casing ──────────────────────────────────────────────

	private boolean normalizeStructuredNameCase(VCard vcard) {
		boolean changed = false;
		StructuredName name = vcard.getStructuredName();
		if (name == null) {
			return false;
		}
		String given = normalizeNameComponent(name.getGiven());
		if (given != null && !given.equals(name.getGiven())) {
			name.setGiven(given);
			changed = true;
		}
		String family = normalizeNameComponent(name.getFamily());
		if (family != null && !family.equals(name.getFamily())) {
			name.setFamily(family);
			changed = true;
		}
		return changed;
	}

	private String normalizeNameComponent(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}
		if (qualifiesForSmartCase(value)) {
			return smartCase(value);
		}
		return Character.isLowerCase(value.codePointAt(0)) ? smartCase(value) : value;
	}

	private boolean qualifiesForSmartCase(String value) {
		return value.equals(value.toUpperCase(Locale.ROOT)) && value.chars().filter(Character::isLetter).count() >= 3;
	}

	private String smartCase(String value) {
		StringBuilder result = new StringBuilder();
		for (String token : value.split(" ")) {
			if (!result.isEmpty()) {
				result.append(' ');
			}
			result.append(smartCaseToken(token));
		}
		return result.toString();
	}

	private String smartCaseToken(String token) {
		String lower = token.toLowerCase(Locale.ROOT);
		if (LOWERCASE_PARTICLES.contains(lower)) {
			return lower;
		}
		if (lower.contains("-")) {
			String[] parts = lower.split("-", -1);
			for (int i = 0; i < parts.length; i++) {
				parts[i] = smartCaseToken(parts[i]);
			}
			return String.join("-", parts);
		}
		if (lower.startsWith("mc") && lower.length() > 2) {
			return "Mc" + pascalCase(lower.substring(2));
		}
		if (lower.startsWith("o'") && lower.length() > 2) {
			return "O'" + pascalCase(lower.substring(2));
		}
		return pascalCase(lower);
	}

	private String pascalCase(String value) {
		if (value.isEmpty()) {
			return value;
		}
		return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
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

	private record DisplayNameEmailSuffix(String displayName, String email) {
	}

}
