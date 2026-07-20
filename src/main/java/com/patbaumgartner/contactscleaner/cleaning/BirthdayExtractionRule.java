package com.patbaumgartner.contactscleaner.cleaning;

import java.time.LocalDate;
import java.time.Year;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ezvcard.VCard;
import ezvcard.property.Birthday;
import ezvcard.property.Note;

/**
 * Promotes a birthday hidden in the contact's notes to a proper vCard {@code BDAY}
 * property — a very common leftover from old phone/SIM imports where the birthday ended
 * up as free text like:
 *
 * <pre>
 * Geburtstag: 12.03.1980
 * Birthday: 1980-03-12
 * born 12/03/1980
 * </pre>
 *
 * <p>
 * Conservative by design:
 * <ul>
 * <li>runs only when the contact has <em>no</em> {@code BDAY} yet — an existing birthday
 * is never overwritten,</li>
 * <li>requires a birthday keyword next to the date — a bare date in a note is too
 * ambiguous (could be a meeting, an import timestamp, ...),</li>
 * <li>dotted/slashed dates are interpreted day-first ({@code 12.03.1980} = 12 March),
 * matching the German/Swiss locales where this note style originates; ISO dates are
 * unambiguous,</li>
 * <li>implausible dates (invalid day/month, year outside 1900..today) are ignored,</li>
 * <li>the note itself is left untouched — only the structured field is added.</li>
 * </ul>
 */
final class BirthdayExtractionRule implements VCardCleaningRule {

	private static final String KEYWORD = "(?:birthday|b-?day|born|geburtstag|geb\\.?|anniversaire|compleanno)";

	/** Keyword, then a day-first dotted/slashed date: {@code Geburtstag: 12.03.1980}. */
	private static final Pattern DAY_FIRST = Pattern
		.compile("(?i)" + KEYWORD + "\\s*[:\\-]?\\s*(\\d{1,2})[./](\\d{1,2})[./](\\d{4})\\b");

	/** Keyword, then an ISO date: {@code Birthday: 1980-03-12}. */
	private static final Pattern ISO = Pattern
		.compile("(?i)" + KEYWORD + "\\s*[:\\-]?\\s*(\\d{4})-(\\d{2})-(\\d{2})\\b");

	private static final int MINIMUM_PLAUSIBLE_YEAR = 1900;

	@Override
	public boolean apply(VCard vcard) {
		if (vcard.getBirthday() != null) {
			return false;
		}
		for (Note note : vcard.getNotes()) {
			String text = note.getValue();
			if (text == null) {
				continue;
			}
			Optional<LocalDate> birthday = extract(text);
			if (birthday.isPresent()) {
				vcard.setBirthday(new Birthday(birthday.get()));
				return true;
			}
		}
		return false;
	}

	/**
	 * Extracts the first plausible keyword-tagged birthday from the given text.
	 * @param text free-text note content
	 * @return the birthday, or empty if none was found
	 */
	Optional<LocalDate> extract(String text) {
		Matcher dayFirst = DAY_FIRST.matcher(text);
		if (dayFirst.find()) {
			return plausibleDate(Integer.parseInt(dayFirst.group(3)), Integer.parseInt(dayFirst.group(2)),
					Integer.parseInt(dayFirst.group(1)));
		}
		Matcher iso = ISO.matcher(text);
		if (iso.find()) {
			return plausibleDate(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)),
					Integer.parseInt(iso.group(3)));
		}
		return Optional.empty();
	}

	private Optional<LocalDate> plausibleDate(int year, int month, int day) {
		if (year < MINIMUM_PLAUSIBLE_YEAR || year > Year.now().getValue()) {
			return Optional.empty();
		}
		try {
			LocalDate date = LocalDate.of(year, month, day);
			return date.isAfter(LocalDate.now()) ? Optional.empty() : Optional.of(date);
		}
		catch (java.time.DateTimeException ex) {
			return Optional.empty();
		}
	}

}
