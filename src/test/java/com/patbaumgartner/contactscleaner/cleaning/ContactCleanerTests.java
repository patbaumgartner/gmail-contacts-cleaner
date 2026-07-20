package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Email;
import ezvcard.property.Telephone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContactCleanerTests {

	private static CleaningProperties defaults() {
		return CleaningProperties.defaults();
	}

	@Test
	void appliesNormalizationAndDeduplicationInOrder() {
		ContactCleaner cleaner = new ContactCleaner(defaults());
		VCard vcard = new VCard();
		vcard.addTelephoneNumber(new Telephone("+41 44 668 18 00"));
		vcard.addTelephoneNumber(new Telephone("0041446681800"));
		vcard.addEmail(new Email("Jane.Doe@gmail.com"));
		vcard.addEmail(new Email("jane.doe@gmail.com"));

		CleaningResult result = cleaner.clean(vcard);

		assertThat(result.changed()).isTrue();
		assertThat(result.empty()).isFalse();
		assertThat(vcard.getTelephoneNumbers()).extracting(Telephone::getText).containsExactly("+41446681800");
		assertThat(vcard.getEmails()).extracting(Email::getValue).containsExactly("jane.doe@gmail.com");
	}

	@Test
	void reportsNoChangeForAlreadyCleanContact() {
		ContactCleaner cleaner = new ContactCleaner(defaults());
		VCard vcard = new VCard();
		vcard.addTelephoneNumber(new Telephone("+41446681800"));
		vcard.addEmail(new Email("jane.doe@gmail.com"));

		assertThat(cleaner.clean(vcard).changed()).isFalse();
	}

	@Test
	void flagsEmptyContactsOnlyWhenDeletionIsEnabled() {
		VCard emptyContact = new VCard();

		ContactCleaner conservative = new ContactCleaner(defaults());
		assertThat(conservative.clean(emptyContact).empty()).isFalse();

		ContactCleaner destructive = new ContactCleaner(defaults().withDestructiveOptions(false, true));
		assertThat(destructive.clean(emptyContact).empty()).isTrue();
	}

	@Test
	void contactWithOnlyABirthdayIsNotEmpty() {
		ContactCleaner cleaner = new ContactCleaner(defaults().withDestructiveOptions(false, true));
		VCard vcard = new VCard();
		vcard.setBirthday(new ezvcard.property.Birthday(java.time.LocalDate.of(1980, 3, 12)));

		assertThat(cleaner.clean(vcard).empty()).isFalse();
	}

	@Test
	void contactWithOnlyANoteIsNotEmpty() {
		ContactCleaner cleaner = new ContactCleaner(defaults().withDestructiveOptions(false, true));
		VCard vcard = new VCard();
		vcard.addNote("wedding photographer of Anna");

		assertThat(cleaner.clean(vcard).empty()).isFalse();
	}

	@Test
	void contactWithOnlyAPhoneNumberIsNotEmpty() {
		ContactCleaner cleaner = new ContactCleaner(defaults().withDestructiveOptions(false, true));
		VCard vcard = new VCard();
		vcard.addTelephoneNumber(new Telephone("+41446681800"));

		assertThat(cleaner.clean(vcard).empty()).isFalse();
	}

	@Test
	void removesNotesOnlyWhenEnabled() {
		VCard vcard = new VCard();
		vcard.addNote("legacy sync note");

		ContactCleaner conservative = new ContactCleaner(defaults());
		assertThat(conservative.clean(vcard).changed()).isFalse();
		assertThat(vcard.getNotes()).hasSize(1);

		ContactCleaner destructive = new ContactCleaner(defaults().withDestructiveOptions(true, false));
		assertThat(destructive.clean(vcard).changed()).isTrue();
		assertThat(vcard.getNotes()).isEmpty();
	}

	@Test
	void disabledRulesAreNotApplied() {
		ContactCleaner cleaner = new ContactCleaner(new CleaningProperties(false, "", false, false, false, false, false,
				false, false, false, true, true, false, true, true, false, false, false, false, false,
				java.util.List.of("Age"), java.util.List.of(), true, true, true, false, 3, false, false));
		VCard vcard = new VCard();
		vcard.addTelephoneNumber(new Telephone("+41 44 668 18 00"));
		vcard.addEmail(new Email("Jane.Doe@GMAIL.com"));

		assertThat(cleaner.clean(vcard).changed()).isFalse();
		assertThat(vcard.getTelephoneNumbers().getFirst().getText()).isEqualTo("+41 44 668 18 00");
		assertThat(vcard.getEmails().getFirst().getValue()).isEqualTo("Jane.Doe@GMAIL.com");
	}

}
