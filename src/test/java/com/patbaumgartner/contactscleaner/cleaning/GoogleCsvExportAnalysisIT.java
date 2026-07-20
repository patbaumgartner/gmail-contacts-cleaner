package com.patbaumgartner.contactscleaner.cleaning;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.patbaumgartner.contactscleaner.orchestration.AccountCleanupResult;
import com.patbaumgartner.contactscleaner.orchestration.CleanupRunCompleted;
import com.patbaumgartner.contactscleaner.orchestration.ContactChange;
import com.patbaumgartner.contactscleaner.reporting.HtmlReportWriter;
import com.patbaumgartner.contactscleaner.reporting.ReportProperties;
import ezvcard.VCard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Visual analysis of the production cleaning rules against a <em>real</em> Google
 * Contacts CSV export ({@code test-data/contacts.csv}, gitignored — contains personal
 * data). Skipped automatically when the file is absent, so CI is unaffected.
 *
 * <p>
 * Run with:
 *
 * <pre>{@code ./mvnw verify -Dit.test=GoogleCsvExportAnalysisIT}</pre>
 *
 * The report prints per-rule hit counts, sample before/after diffs, duplicate candidates
 * and a residual-dirt scan — the fastest way to eyeball whether the rules behave
 * correctly on real-world data and what else deserves a cleaning rule.
 */
class GoogleCsvExportAnalysisIT {

	private static final Path EXPORT = Path.of("test-data", "contacts.csv");

	private static final int MAX_SAMPLE_DIFFS = 15;

	private static final int MAX_LISTED = 25;

	@Test
	void analyzeRealExport() throws Exception {
		assumeTrue(Files.exists(EXPORT), "no local export at " + EXPORT + " — skipping analysis");

		List<VCard> contacts = GoogleCsvContacts.read(EXPORT);
		CleaningProperties properties = CleaningProperties.defaults().withPhoneRegion("CH");

		section("EXPORT: %d contacts loaded from %s".formatted(contacts.size(), EXPORT));

		perRuleHitCounts(contacts, properties);
		List<String> diffs = fullCleanWithDiffs(contacts, properties);
		duplicateReport(contacts, properties);
		sharedNumberReport(contacts);
		organizationCanonicalizationReport(contacts);
		customFieldInventory(contacts);
		residualDirtScan(contacts);
		writeHtmlReport(properties);

		assertThat(contacts).isNotEmpty();
		assertThat(diffs).isNotEmpty();
	}

	/** Applies each rule to a fresh copy of the export and counts changed contacts. */
	private void perRuleHitCounts(List<VCard> original, CleaningProperties properties) throws Exception {
		section("PER-RULE HIT COUNTS (contacts changed by each rule, applied in production order)");
		Map<String, Supplier<VCardCleaningRule>> rules = new LinkedHashMap<>();
		rules.put("EmptyPropertyRemovalRule", EmptyPropertyRemovalRule::new);
		rules.put("NameTrimmingRule", NameTrimmingRule::new);
		rules.put("JunkNameSuffixRemovalRule", JunkNameSuffixRemovalRule::new);
		rules.put("NameRepairRule", NameRepairRule::new);
		rules.put("LabelNormalizationRule", LabelNormalizationRule::new);
		rules.put("FlippedNameRepairRule", FlippedNameRepairRule::new);
		rules.put("PhoneNumberNormalizationRule (CH)", () -> new PhoneNumberNormalizationRule("CH"));
		rules.put("DuplicatePhoneNumberRemovalRule", DuplicatePhoneNumberRemovalRule::new);
		rules.put("PhoneTypeCorrectionRule (CH)", () -> new PhoneTypeCorrectionRule("CH"));
		rules.put("InvalidPhoneNumberRemovalRule (CH, opt-in)", () -> new InvalidPhoneNumberRemovalRule("CH"));
		rules.put("FaxNumberRemovalRule (opt-in)", FaxNumberRemovalRule::new);
		rules.put("EmailNormalizationRule", EmailNormalizationRule::new);
		rules.put("InvalidEmailRemovalRule", InvalidEmailRemovalRule::new);
		rules.put("DuplicateEmailRemovalRule", DuplicateEmailRemovalRule::new);
		rules.put("BirthdayExtractionRule", BirthdayExtractionRule::new);
		rules.put("SocialNetworkNoteRemovalRule", SocialNetworkNoteRemovalRule::new);
		rules.put("UrlCleanupRule", UrlCleanupRule::new);
		rules.put("CustomFieldRemovalRule (Age)", () -> new CustomFieldRemovalRule(java.util.List.of("Age")));
		rules.put("GeoCoordinateAddressRemovalRule", GeoCoordinateAddressRemovalRule::new);
		rules.put("RedundantAddressRemovalRule", RedundantAddressRemovalRule::new);
		rules.put("OrganizationRemovalRule (Namics, opt-in)",
				() -> new OrganizationRemovalRule(java.util.List.of("Namics")));
		rules.put("SelfOrganizationRemovalRule", SelfOrganizationRemovalRule::new);
		rules.put("DanglingTitleRemovalRule", DanglingTitleRemovalRule::new);

		List<VCard> contacts = GoogleCsvContacts.read(EXPORT);
		for (Map.Entry<String, Supplier<VCardCleaningRule>> entry : rules.entrySet()) {
			VCardCleaningRule rule = entry.getValue().get();
			long hits = contacts.stream().filter(rule::apply).count();
			System.out.printf("  %-38s %5d%n", entry.getKey(), hits);
		}
	}

	/** Runs the full production cleaner and prints sample before/after diffs. */
	private List<String> fullCleanWithDiffs(List<VCard> contacts, CleaningProperties properties) {
		ContactCleaner cleaner = new ContactCleaner(properties);
		List<String> diffs = new ArrayList<>();
		int changed = 0;
		int empty = 0;
		for (VCard vcard : contacts) {
			List<String> before = snapshot(vcard);
			CleaningResult result = cleaner.clean(vcard);
			if (result.changed()) {
				changed++;
				List<String> after = snapshot(vcard);
				if (diffs.size() < MAX_SAMPLE_DIFFS) {
					diffs.add(diff(displayName(vcard), before, after));
				}
			}
			if (vcard.getTelephoneNumbers().isEmpty() && vcard.getEmails().isEmpty()) {
				empty++;
			}
		}
		section("FULL CLEAN: %d of %d contacts would be updated | %d contacts have no phone AND no e-mail"
			.formatted(changed, contacts.size(), empty));
		section("SAMPLE BEFORE/AFTER DIFFS (first %d changed contacts)".formatted(MAX_SAMPLE_DIFFS));
		diffs.forEach(System.out::println);
		return diffs;
	}

	private void duplicateReport(List<VCard> contacts, CleaningProperties properties) {
		List<DuplicateCandidate> candidates = new DuplicateContactDetector(properties).detect(contacts);
		section("DUPLICATE CANDIDATES: %d pairs (first %d shown)".formatted(candidates.size(), MAX_LISTED));
		candidates.stream()
			.limit(MAX_LISTED)
			.forEach((candidate) -> System.out.printf("  '%s' <-> '%s'  (%s)%n", candidate.firstContact(),
					candidate.secondContact(), candidate.reason()));
	}

	/**
	 * Renders the production HTML report from the export as a simulated dry run — open
	 * reports/cleanup-report-latest.html in a browser for the visual check.
	 */
	private void writeHtmlReport(CleaningProperties properties) throws Exception {
		List<VCard> contacts = GoogleCsvContacts.read(EXPORT);
		ContactCleaner cleaner = new ContactCleaner(properties);
		List<ContactChange> changes = new ArrayList<>();
		int updated = 0;
		java.util.Map<VCard, List<String>> snapshots = new java.util.IdentityHashMap<>();
		for (VCard vcard : contacts) {
			snapshots.put(vcard, snapshot(vcard));
			if (cleaner.clean(vcard).changed()) {
				updated++;
				List<String> before = snapshots.get(vcard);
				List<String> after = snapshot(vcard);
				changes.add(new ContactChange(displayName(vcard), ContactChange.Type.UPDATED,
						before.stream().filter((line) -> !after.contains(line)).toList(),
						after.stream().filter((line) -> !before.contains(line)).toList()));
			}
		}
		var duplicates = new DuplicateContactDetector(properties).detect(contacts);
		var result = new AccountCleanupResult("export-analysis (simulated)", true, contacts.size(), updated, 0,
				duplicates, changes, true, 0, "Simulated from " + EXPORT);
		new HtmlReportWriter(new ReportProperties(true, "reports"))
			.onCleanupRunCompleted(new CleanupRunCompleted(java.time.Instant.now(), List.of(result)));
		section("HTML REPORT written to reports/cleanup-report-latest.html — open it in a browser");
	}

	/** What would the opt-in shared-office-number removal do? */
	private void sharedNumberReport(List<VCard> contacts) {
		CleaningProperties enabled = new CleaningProperties(true, "CH", true, true, false, false, true, true, true,
				false, true, true, true, true, true, true, true, true, false, true, true, true,
				java.util.List.of("Age"), java.util.List.of(), true, true, true, true, 2, false, false);
		var changed = new SharedPhoneNumberRemover(enabled).removeSharedNumbers(contacts);
		section("SHARED PHONE NUMBERS (opt-in remove-shared-phone-numbers, default threshold 2): %d contacts affected"
			.formatted(changed.size()));
	}

	/** What would organization canonicalization rewrite? */
	private void organizationCanonicalizationReport(List<VCard> contacts) {
		var changed = new OrganizationCanonicalizer(CleaningProperties.defaults()).canonicalize(contacts);
		section("ORGANIZATION CANONICALIZATION: %d contacts rewritten to the majority spelling"
			.formatted(changed.size()));
	}

	/**
	 * Which custom-field labels exist? Feed the interesting ones to remove-custom-fields.
	 */
	private void customFieldInventory(List<VCard> contacts) {
		Map<String, Integer> labels = new java.util.TreeMap<>();
		for (VCard vcard : contacts) {
			for (ezvcard.property.RawProperty property : vcard.getExtendedProperties()) {
				if ("X-ABLabel".equalsIgnoreCase(property.getPropertyName()) && property.getValue() != null) {
					labels.merge(property.getValue().trim(), 1, Integer::sum);
				}
			}
		}
		section("CUSTOM FIELD INVENTORY (configure removals via contacts-cleaner.cleaning.remove-custom-fields)");
		labels.forEach((label, count) -> System.out.printf("  %-38s %5d%n", label, count));
	}

	/** What is still dirty after the full clean? Ideas for the next cleaning rules. */
	private void residualDirtScan(List<VCard> contacts) {
		section("RESIDUAL DIRT AFTER CLEANING (candidates for new rules)");
		count(contacts, "phones not in +E.164 shape", (vcard) -> vcard.getTelephoneNumbers()
			.stream()
			.anyMatch((telephone) -> telephone.getText() != null && !telephone.getText().matches("\\+\\d{7,15}")));
		count(contacts, "notes still mentioning linkedin/xing/connected on",
				(vcard) -> vcard.getNotes()
					.stream()
					.anyMatch((note) -> note.getValue() != null
							&& Pattern.compile("(?i)linkedin|xing|connected on").matcher(note.getValue()).find()));
		count(contacts, "urls to klout/gravatar/google+ still present",
				(vcard) -> vcard.getUrls()
					.stream()
					.anyMatch((url) -> url.getValue() != null && url.getValue()
						.toLowerCase(Locale.ROOT)
						.matches(".*(klout|gravatar|plus\\.google|picasaweb|friendfeed).*")));
		count(contacts, "uppercase left in e-mail addresses",
				(vcard) -> vcard.getEmails()
					.stream()
					.anyMatch((email) -> email.getValue() != null
							&& !email.getValue().equals(email.getValue().toLowerCase(Locale.ROOT))));
		count(contacts, "ALL-CAPS formatted names",
				(vcard) -> vcard.getFormattedName() != null && vcard.getFormattedName().getValue() != null
						&& vcard.getFormattedName().getValue().length() > 3
						&& vcard.getFormattedName()
							.getValue()
							.equals(vcard.getFormattedName().getValue().toUpperCase(Locale.ROOT)));
		count(contacts, "contacts without any name", (vcard) -> vcard.getFormattedName() == null
				|| vcard.getFormattedName().getValue() == null || vcard.getFormattedName().getValue().isBlank());
	}

	private void count(List<VCard> contacts, String label, java.util.function.Predicate<VCard> predicate) {
		System.out.printf("  %-48s %5d%n", label, contacts.stream().filter(predicate).count());
	}

	private List<String> snapshot(VCard vcard) {
		List<String> lines = new ArrayList<>();
		vcard.getTelephoneNumbers().forEach((telephone) -> lines.add("TEL   " + telephone.getText()));
		vcard.getEmails().forEach((email) -> lines.add("EMAIL " + email.getValue()));
		vcard.getUrls().forEach((url) -> lines.add("URL   " + url.getValue()));
		vcard.getNotes().forEach((note) -> lines.add("NOTE  " + abbreviate(note.getValue())));
		if (vcard.getBirthday() != null) {
			lines.add("BDAY  " + vcard.getBirthday().getDate());
		}
		return lines;
	}

	private String diff(String name, List<String> before, List<String> after) {
		StringBuilder out = new StringBuilder("\n■ ").append(name);
		before.stream().filter((line) -> !after.contains(line)).forEach((line) -> out.append("\n  - ").append(line));
		after.stream().filter((line) -> !before.contains(line)).forEach((line) -> out.append("\n  + ").append(line));
		return out.toString();
	}

	private String abbreviate(String value) {
		if (value == null) {
			return null;
		}
		String flat = value.replace("\n", "\\n");
		return (flat.length() > 100) ? flat.substring(0, 100) + "…" : flat;
	}

	private String displayName(VCard vcard) {
		return (vcard.getFormattedName() != null && vcard.getFormattedName().getValue() != null)
				? vcard.getFormattedName().getValue() : "<unnamed>";
	}

	private void section(String title) {
		System.out.println("\n" + "═".repeat(90) + "\n" + title + "\n" + "═".repeat(90));
	}

}
