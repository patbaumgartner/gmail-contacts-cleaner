package com.patbaumgartner.contactscleaner.orchestration;

import java.util.ArrayList;
import java.util.List;

import ezvcard.VCard;

/**
 * Renders the reportable properties of a vCard as human-readable lines, used to compute
 * before/after diffs for the HTML report.
 */
final class VCardSnapshot {

	private static final int MAX_NOTE_LENGTH = 160;

	private VCardSnapshot() {
	}

	static List<String> of(VCard vcard) {
		List<String> lines = new ArrayList<>();
		vcard.getTelephoneNumbers().forEach((telephone) -> lines.add("TEL " + telephone.getText()));
		vcard.getEmails().forEach((email) -> lines.add("EMAIL " + email.getValue()));
		vcard.getUrls().forEach((url) -> lines.add("URL " + url.getValue()));
		vcard.getNotes().forEach((note) -> lines.add("NOTE " + abbreviate(note.getValue())));
		if (vcard.getBirthday() != null && vcard.getBirthday().getDate() != null) {
			lines.add("BDAY " + vcard.getBirthday().getDate());
		}
		if (vcard.getFormattedName() != null && vcard.getFormattedName().getValue() != null) {
			lines.add("FN " + vcard.getFormattedName().getValue());
		}
		return lines;
	}

	static String displayName(VCard vcard) {
		return (vcard.getFormattedName() != null && vcard.getFormattedName().getValue() != null
				&& !vcard.getFormattedName().getValue().isBlank()) ? vcard.getFormattedName().getValue() : "<unnamed>";
	}

	private static String abbreviate(String value) {
		if (value == null) {
			return "";
		}
		String flat = value.replace("\r", "").replace("\n", " ⏎ ");
		return (flat.length() > MAX_NOTE_LENGTH) ? flat.substring(0, MAX_NOTE_LENGTH) + "…" : flat;
	}

}
