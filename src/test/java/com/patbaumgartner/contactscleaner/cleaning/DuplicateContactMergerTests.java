package com.patbaumgartner.contactscleaner.cleaning;

import java.util.List;

import ezvcard.VCard;
import ezvcard.property.Birthday;
import ezvcard.property.Email;
import ezvcard.property.FormattedName;
import ezvcard.property.Telephone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateContactMergerTests {

	private static CleaningProperties enabled() {
		return new CleaningProperties(true, "", true, true, true, true, false, true, true, true, true, true, true, true,
				false, 2, false, false);
	}

	private final DuplicateContactMerger merger = new DuplicateContactMerger(enabled());

	private static VCard contact(String name, String phone, String email) {
		VCard vcard = new VCard();
		vcard.setFormattedName(new FormattedName(name));
		if (phone != null) {
			vcard.addTelephoneNumber(phone);
		}
		if (email != null) {
			vcard.addEmail(email);
		}
		return vcard;
	}

	@Test
	void mergesFlippedNamesSharingAPhoneNumber() {
		VCard rich = contact("Tudor Girba", "+41765790423", "tudor@example.com");
		VCard poor = contact("Girba Tudor", "+41765790423", null);
		poor.addNote("met at ESUG");

		List<DuplicateContactMerger.Merge> merges = this.merger.merge(List.of(rich, poor));

		assertThat(merges).singleElement().satisfies((merge) -> {
			assertThat(merge.primary()).isSameAs(rich);
			assertThat(merge.merged()).containsExactly(poor);
		});
		// Union: the primary now carries the note of the merged card.
		assertThat(rich.getNotes()).extracting((note) -> note.getValue()).containsExactly("met at ESUG");
		assertThat(rich.getTelephoneNumbers()).extracting(Telephone::getText).containsExactly("+41765790423");
	}

	@Test
	void mergesIdenticalNamesSharingAnEmail() {
		VCard first = contact("Matthias Kappeller", null, "mkappeller@gmail.com");
		VCard second = contact("Matthias Kappeller", "+498662639072", "mkappeller@gmail.com");

		List<DuplicateContactMerger.Merge> merges = this.merger.merge(List.of(first, second));

		assertThat(merges).singleElement().satisfies((merge) -> {
			assertThat(merge.primary()).isSameAs(second);
			assertThat(merge.merged()).containsExactly(first);
		});
		assertThat(second.getEmails()).extracting(Email::getValue).containsExactly("mkappeller@gmail.com");
	}

	@Test
	void neverMergesSameNameWithoutASharedValue() {
		// Two different John Smiths — same name is not enough.
		VCard first = contact("John Smith", "+41791111111", "john@acme.example");
		VCard second = contact("John Smith", "+41792222222", "john@other.example");

		assertThat(this.merger.merge(List.of(first, second))).isEmpty();
	}

	@Test
	void neverMergesSharedValueWithDifferentNames() {
		// Colleagues sharing a switchboard — that is SharedPhoneNumberRemover's job.
		VCard first = contact("Jane Doe", "+41712286788", null);
		VCard second = contact("Max Muster", "+41712286788", null);

		assertThat(this.merger.merge(List.of(first, second))).isEmpty();
	}

	@Test
	void mergesTransitiveGroupsIntoTheRichestCard() {
		VCard a = contact("Jane Doe", "+41791111111", null);
		VCard b = contact("Doe Jane", "+41791111111", "jane@example.com");
		VCard c = contact("Jane Doe", null, "jane@example.com");
		b.setBirthday(new Birthday(java.time.LocalDate.of(1980, 3, 12)));

		List<DuplicateContactMerger.Merge> merges = this.merger.merge(List.of(a, b, c));

		assertThat(merges).singleElement().satisfies((merge) -> {
			assertThat(merge.primary()).isSameAs(b);
			assertThat(merge.merged()).containsExactlyInAnyOrder(a, c);
		});
	}

	@Test
	void takesOverMissingBirthday() {
		VCard withBirthday = contact("Jane Doe", "+41791111111", null);
		withBirthday.setBirthday(new Birthday(java.time.LocalDate.of(1980, 3, 12)));
		VCard richer = contact("Doe Jane", "+41791111111", "jane@example.com");
		richer.addUrl("https://janedoe.example");

		this.merger.merge(List.of(richer, withBirthday));

		assertThat(richer.getBirthday()).isNotNull();
	}

	@Test
	void singleTokenNamesAreTooAmbiguousToMerge() {
		VCard first = contact("Jane", "+41791111111", null);
		VCard second = contact("Jane", "+41791111111", null);

		assertThat(this.merger.merge(List.of(first, second))).isEmpty();
	}

	@Test
	void disabledByDefault() {
		var disabled = new DuplicateContactMerger(CleaningProperties.defaults());
		VCard first = contact("Tudor Girba", "+41765790423", null);
		VCard second = contact("Girba Tudor", "+41765790423", null);

		assertThat(disabled.merge(List.of(first, second))).isEmpty();
	}

}
