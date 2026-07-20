package com.patbaumgartner.contactscleaner.cleaning;

import java.time.LocalDate;

import ezvcard.VCard;
import ezvcard.property.Birthday;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class BirthdayExtractionRuleTests {

	private final BirthdayExtractionRule rule = new BirthdayExtractionRule();

	@ParameterizedTest(name = "\"{0}\" -> {1}")
	@CsvSource(textBlock = """
			'Geburtstag: 12.03.1980',            1980-03-12
			'Geburtstag 1.4.1975',               1975-04-01
			'geb. 07.11.1968',                   1968-11-07
			'Birthday: 1980-03-12',              1980-03-12
			'birthday - 12/03/1980',             1980-03-12
			'born 24.12.1990 in Zurich',         1990-12-24
			'B-Day: 05.06.2001',                 2001-06-05
			'anniversaire: 15.08.1985',          1985-08-15
			""")
	void extractsKeywordTaggedBirthdays(String note, LocalDate expected) {
		assertThat(this.rule.extract(note)).contains(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "Meeting on 12.03.2020", "12.03.1980", "Geburtstag: 32.13.1980", "Geburtstag: 12.03.1850",
			"Geburtstag: 12.03.3021", "likes birthdays", "" })
	void ignoresUntaggedImplausibleOrMissingDates(String note) {
		assertThat(this.rule.extract(note)).isEmpty();
	}

	@Test
	void promotesNoteBirthdayToBdayProperty() {
		VCard vcard = new VCard();
		vcard.addNote("Geburtstag: 12.03.1980");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getBirthday().getDate()).isEqualTo(LocalDate.of(1980, 3, 12));
		// The note itself stays untouched — only the structured field is added.
		assertThat(vcard.getNotes()).hasSize(1);
	}

	@Test
	void neverOverwritesAnExistingBirthday() {
		VCard vcard = new VCard();
		vcard.setBirthday(new Birthday(LocalDate.of(1975, 1, 1)));
		vcard.addNote("Geburtstag: 12.03.1980");

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getBirthday().getDate()).isEqualTo(LocalDate.of(1975, 1, 1));
	}

	@Test
	void handlesContactsWithoutNotes() {
		assertThat(this.rule.apply(new VCard())).isFalse();
	}

	@Test
	void rejectsFutureDates() {
		String note = "Geburtstag: 31.12." + (LocalDate.now().getYear());
		if (LocalDate.now().getDayOfYear() < 365) {
			assertThat(this.rule.extract(note)).isEmpty();
		}
	}

}
