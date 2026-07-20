package com.patbaumgartner.contactscleaner.cleaning;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ezvcard.VCard;
import ezvcard.property.FormattedName;
import ezvcard.property.StructuredName;

/**
 * Test-scope reader for the Google Contacts CSV export format ("Google CSV" from
 * <a href="https://contacts.google.com">contacts.google.com</a> → Export). Converts each
 * row into a {@link VCard} so the production cleaning rules can be exercised against a
 * real address book.
 *
 * <p>
 * Handles RFC 4180 quoting (multi-line notes!) and Google's {@code " ::: "} multi-value
 * separator within a single field.
 */
final class GoogleCsvContacts {

	private static final String MULTI_VALUE_SEPARATOR = "\\s*:::\\s*";

	private GoogleCsvContacts() {
	}

	static List<VCard> read(Path csv) throws IOException {
		List<Map<String, String>> rows = parse(Files.readString(csv, StandardCharsets.UTF_8));
		return rows.stream().map(GoogleCsvContacts::toVCard).toList();
	}

	private static VCard toVCard(Map<String, String> row) {
		VCard vcard = new VCard();

		StructuredName name = new StructuredName();
		name.setGiven(emptyToNull(row.get("First Name")));
		name.setFamily(emptyToNull(row.get("Last Name")));
		if (!row.getOrDefault("Middle Name", "").isBlank()) {
			name.getAdditionalNames().add(row.get("Middle Name"));
		}
		if (!row.getOrDefault("Name Suffix", "").isBlank()) {
			name.getSuffixes().add(row.get("Name Suffix"));
		}
		if (!row.getOrDefault("Name Prefix", "").isBlank()) {
			name.getPrefixes().add(row.get("Name Prefix"));
		}
		vcard.setStructuredName(name);
		String formatted = (row.getOrDefault("First Name", "") + " " + row.getOrDefault("Middle Name", "") + " "
				+ row.getOrDefault("Last Name", ""))
			.replaceAll("\\s+", " ")
			.trim();
		if (!formatted.isEmpty()) {
			vcard.setFormattedName(new FormattedName(formatted));
		}

		if (!row.getOrDefault("Notes", "").isBlank()) {
			vcard.addNote(row.get("Notes"));
		}
		if (!row.getOrDefault("Organization Name", "").isBlank()) {
			vcard.setOrganization(row.get("Organization Name"));
		}
		if (!row.getOrDefault("Organization Title", "").isBlank()) {
			vcard.addTitle(row.get("Organization Title"));
		}

		for (int i = 1; i <= 6; i++) {
			String label = row.getOrDefault("Phone " + i + " - Label", "");
			for (String number : multiValues(row.get("Phone " + i + " - Value"))) {
				ezvcard.property.Telephone telephone = new ezvcard.property.Telephone(number);
				if (label.toLowerCase(java.util.Locale.ROOT).contains("fax")) {
					// Emulate Google's custom-label representation (grouped X-ABLabel).
					String group = "itemp" + i;
					telephone.setGroup(group);
					vcard.addExtendedProperty("X-ABLabel", label).setGroup(group);
				}
				vcard.addTelephoneNumber(telephone);
			}
		}
		for (int i = 1; i <= 5; i++) {
			String label = row.getOrDefault("E-mail " + i + " - Label", "").replaceFirst("^\\s*\\*\\s*", "").trim();
			boolean custom = !label.isEmpty()
					&& !java.util.Set.of("work", "home", "other").contains(label.toLowerCase(java.util.Locale.ROOT));
			int index = 0;
			for (String value : multiValues(row.get("E-mail " + i + " - Value"))) {
				ezvcard.property.Email email = new ezvcard.property.Email(value);
				if (custom) {
					String group = "iteme" + i + "x" + (index++);
					email.setGroup(group);
					vcard.addExtendedProperty("X-ABLabel", label).setGroup(group);
				}
				vcard.addEmail(email);
			}
		}
		for (int i = 1; i <= 2; i++) {
			multiValues(row.get("Website " + i + " - Value")).forEach(vcard::addUrl);
		}
		for (int i = 1; i <= 2; i++) {
			ezvcard.property.Address address = new ezvcard.property.Address();
			address.setStreetAddress(emptyToNull(row.get("Address " + i + " - Street")));
			address.setLocality(emptyToNull(row.get("Address " + i + " - City")));
			address.setPoBox(emptyToNull(row.get("Address " + i + " - PO Box")));
			address.setRegion(emptyToNull(row.get("Address " + i + " - Region")));
			address.setPostalCode(emptyToNull(row.get("Address " + i + " - Postal Code")));
			address.setCountry(emptyToNull(row.get("Address " + i + " - Country")));
			address.setExtendedAddress(emptyToNull(row.get("Address " + i + " - Extended Address")));
			boolean empty = address.getStreetAddress() == null && address.getLocality() == null
					&& address.getPoBox() == null && address.getRegion() == null && address.getPostalCode() == null
					&& address.getCountry() == null && address.getExtendedAddress() == null;
			if (!empty) {
				vcard.addAddress(address);
			}
		}
		for (int i = 1; i <= 3; i++) {
			String label = row.getOrDefault("Custom Field " + i + " - Label", "");
			String value = row.getOrDefault("Custom Field " + i + " - Value", "");
			if (!label.isBlank() && !value.isBlank()) {
				String group = "itemc" + i;
				ezvcard.property.RawProperty abLabel = vcard.addExtendedProperty("X-ABLabel", label);
				abLabel.setGroup(group);
				ezvcard.property.RawProperty custom = vcard.addExtendedProperty("X-ABCustom", value);
				custom.setGroup(group);
			}
		}
		return vcard;
	}

	private static List<String> multiValues(String field) {
		if (field == null || field.isBlank()) {
			return List.of();
		}
		return List.of(field.split(MULTI_VALUE_SEPARATOR));
	}

	private static String emptyToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}

	/** Minimal RFC 4180 parser (quoted fields may contain commas, quotes, newlines). */
	private static List<Map<String, String>> parse(String content) {
		List<List<String>> records = new ArrayList<>();
		List<String> record = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < content.length(); i++) {
			char c = content.charAt(i);
			if (quoted) {
				if (c == '"' && i + 1 < content.length() && content.charAt(i + 1) == '"') {
					field.append('"');
					i++;
				}
				else if (c == '"') {
					quoted = false;
				}
				else {
					field.append(c);
				}
			}
			else if (c == '"') {
				quoted = true;
			}
			else if (c == ',') {
				record.add(field.toString());
				field.setLength(0);
			}
			else if (c == '\n' || c == '\r') {
				if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
					i++;
				}
				record.add(field.toString());
				field.setLength(0);
				if (record.stream().anyMatch((v) -> !v.isEmpty())) {
					records.add(record);
				}
				record = new ArrayList<>();
			}
			else {
				field.append(c);
			}
		}
		if (!field.isEmpty() || !record.isEmpty()) {
			record.add(field.toString());
			records.add(record);
		}

		List<String> header = records.getFirst();
		List<Map<String, String>> rows = new ArrayList<>();
		for (List<String> values : records.subList(1, records.size())) {
			Map<String, String> row = new HashMap<>();
			for (int i = 0; i < header.size() && i < values.size(); i++) {
				row.put(header.get(i).strip(), values.get(i));
			}
			rows.add(row);
		}
		return rows;
	}

}
