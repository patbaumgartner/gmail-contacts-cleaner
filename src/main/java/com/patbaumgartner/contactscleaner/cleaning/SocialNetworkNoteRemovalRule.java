package com.patbaumgartner.contactscleaner.cleaning;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import ezvcard.VCard;
import ezvcard.property.Note;

/**
 * Removes machine-generated social-network sync lines from contact notes — the debris
 * that the old XING, LinkedIn and similar "sync contacts" apps wrote into every contact,
 * e.g.:
 *
 * <pre>
 * XING: https://www.xing.com/profile/Jane_Doe
 * LinkedIn: http://www.linkedin.com/in/janedoe
 * Created via LinkedIn
 * </pre>
 *
 * <p>
 * The rule is surgical: only lines matching a known sync pattern are removed. Text the
 * user wrote themselves is preserved; a note is deleted entirely only when nothing but
 * sync debris remains.
 */
final class SocialNetworkNoteRemovalRule implements VCardCleaningRule {

	/**
	 * A line is removed when it matches one of these patterns (case-insensitive): profile
	 * URLs of the networks, or {@code "<network>: ..."} prefixed lines, or the "Created
	 * via ..." markers the sync apps appended.
	 */
	private static final List<Pattern> SYNC_LINE_PATTERNS = List.of(
			Pattern.compile("(?i).*\\bxing\\.com/(profile|profiles)/.*"),
			Pattern.compile("(?i).*\\blinkedin\\.com/(in|pub|profile)/.*"),
			Pattern.compile("(?i)^\\s*(xing|linkedin|facebook|twitter)\\s*:.*"),
			Pattern.compile("(?i)^\\s*(created|imported|added)\\s+(via|from|by)\\s+(xing|linkedin)\\b.*"));

	@Override
	public boolean apply(VCard vcard) {
		boolean changed = false;
		for (Iterator<Note> iterator = vcard.getNotes().iterator(); iterator.hasNext();) {
			Note note = iterator.next();
			String value = note.getValue();
			if (value == null) {
				continue;
			}
			String cleaned = removeSyncLines(value);
			if (cleaned.equals(value)) {
				continue;
			}
			if (cleaned.isBlank()) {
				iterator.remove();
			}
			else {
				note.setValue(cleaned);
			}
			changed = true;
		}
		return changed;
	}

	private String removeSyncLines(String value) {
		List<String> kept = new ArrayList<>();
		for (String line : value.split("\\R", -1)) {
			if (!isSyncLine(line)) {
				kept.add(line);
			}
		}
		// Collapse leftover blank lines at the edges caused by removed lines.
		return String.join("\n", kept).strip();
	}

	private boolean isSyncLine(String line) {
		return SYNC_LINE_PATTERNS.stream().anyMatch((pattern) -> pattern.matcher(line).matches());
	}

}
