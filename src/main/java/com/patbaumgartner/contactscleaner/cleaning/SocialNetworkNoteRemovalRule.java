package com.patbaumgartner.contactscleaner.cleaning;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import ezvcard.VCard;
import ezvcard.property.Note;

/**
 * Removes machine-generated social-network sync content from contact notes — the debris
 * that the old XING, LinkedIn and similar "sync contacts" apps wrote into every contact,
 * e.g.:
 *
 * <pre>
 * XING: https://www.xing.com/profile/Jane_Doe
 * LinkedIn: http://www.linkedin.com/in/janedoe
 * Created via LinkedIn
 *
 * Position: CTO &amp; Co-Founder
 * Connected on: 3/12/11, 10:11 PM
 * </pre>
 *
 * <p>
 * Two detection layers:
 * <ul>
 * <li><strong>Unconditional sync lines</strong> — profile URLs and
 * {@code "<network>: ..."} / {@code "Created via ..."} markers are always removed.</li>
 * <li><strong>LinkedIn import blocks</strong> — {@code Position:} / {@code Company:}
 * lines are only removed when the same note also carries a {@code "Connected on ..."}
 * marker, the unmistakable signature of a LinkedIn contact import. A user-written
 * "Position: ..." note without that marker is preserved.</li>
 * </ul>
 *
 * <p>
 * The rule is surgical: only matching lines are removed, remaining user-written text is
 * preserved, and a note is deleted entirely only when nothing but sync debris remains.
 */
final class SocialNetworkNoteRemovalRule implements VCardCleaningRule {

	/** Lines removed unconditionally (case-insensitive). */
	private static final List<Pattern> SYNC_LINE_PATTERNS = List.of(
			Pattern.compile("(?i).*\\bxing\\.com/(profile|profiles)/.*"),
			Pattern.compile("(?i).*\\blinkedin\\.com/(in|pub|profile)/.*"),
			Pattern.compile("(?i)^\\s*(xing|linkedin|facebook|twitter)\\s*:.*"),
			Pattern.compile("(?i)^\\s*(created|imported|added)\\s+(via|from|by)\\s+(xing|linkedin)\\b.*"),
			Pattern.compile("(?i)^\\s*connected\\s+on\\b.*"),
			// The "Position: ... | Company: ..." one-liner is the LinkedIn import
			// format even when the "Connected on" marker is missing.
			Pattern.compile("(?i)^\\s*position\\s*:.*\\|\\s*company\\s*:.*"));

	/**
	 * The LinkedIn import marker: a "Connected on <date>" line. Its presence unlocks
	 * removal of the accompanying Position/Company lines.
	 */
	private static final Pattern LINKEDIN_IMPORT_MARKER = Pattern.compile("(?i)^\\s*connected\\s+on\\b.*");

	/** Lines removed only inside a LinkedIn import block. */
	private static final Pattern LINKEDIN_BLOCK_LINE = Pattern.compile("(?i)^\\s*(position|company)\\s*:.*");

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
		boolean linkedInImport = value.lines().anyMatch((line) -> LINKEDIN_IMPORT_MARKER.matcher(line).matches());
		List<String> kept = new ArrayList<>();
		for (String line : value.split("\\R", -1)) {
			if (isSyncLine(line) || (linkedInImport && LINKEDIN_BLOCK_LINE.matcher(line).matches())) {
				continue;
			}
			kept.add(line);
		}
		// Collapse leftover blank lines caused by removed lines.
		return String.join("\n", kept).replaceAll("\\n{3,}", "\n\n").strip();
	}

	private boolean isSyncLine(String line) {
		return SYNC_LINE_PATTERNS.stream().anyMatch((pattern) -> pattern.matcher(line).matches());
	}

}
