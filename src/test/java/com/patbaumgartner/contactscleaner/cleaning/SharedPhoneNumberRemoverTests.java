package com.patbaumgartner.contactscleaner.cleaning;

import java.util.List;

import ezvcard.VCard;
import ezvcard.property.FormattedName;
import ezvcard.property.Telephone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SharedPhoneNumberRemoverTests {

	private static CleaningProperties enabled(int threshold) {
		return new CleaningProperties(true, "", true, true, false, false, true, true, true, false, true, true, true,
				true, true, true, true, true, false, true, true, true, true, java.util.List.of("Age"),
				java.util.List.of(), false, true, true, true, true, threshold, false, false);
	}

	private static VCard contact(String name, String... numbers) {
		VCard vcard = new VCard();
		vcard.setFormattedName(new FormattedName(name));
		for (String number : numbers) {
			vcard.addTelephoneNumber(number);
		}
		return vcard;
	}

	@Test
	void removesNumbersSharedByThresholdManyContacts() {
		var remover = new SharedPhoneNumberRemover(enabled(3));
		VCard first = contact("Colleague One", "+41440000001", "+41791111111");
		VCard second = contact("Colleague Two", "+41440000001");
		VCard third = contact("Colleague Three", "+41440000001", "+41792222222");

		var changed = remover.removeSharedNumbers(List.of(first, second, third));

		assertThat(changed).containsExactlyInAnyOrder(first, second, third);
		assertThat(first.getTelephoneNumbers()).extracting(Telephone::getText).containsExactly("+41791111111");
		assertThat(second.getTelephoneNumbers()).isEmpty();
		assertThat(third.getTelephoneNumbers()).extracting(Telephone::getText).containsExactly("+41792222222");
	}

	@Test
	void defaultThresholdRemovesLandlinesSharedByTwoContacts() {
		var remover = new SharedPhoneNumberRemover(enabled(2));
		VCard wife = contact("Jane Doe", "+41446681800", "+41791111111");
		VCard husband = contact("John Doe", "+41446681800");

		var changed = remover.removeSharedNumbers(List.of(wife, husband));

		assertThat(changed).containsExactlyInAnyOrder(wife, husband);
		assertThat(wife.getTelephoneNumbers()).extracting(Telephone::getText).containsExactly("+41791111111");
		assertThat(husband.getTelephoneNumbers()).isEmpty();
	}

	@Test
	void raisedThresholdKeepsCoupleLandlines() {
		var remover = new SharedPhoneNumberRemover(enabled(3));
		VCard wife = contact("Jane Doe", "+41446681800");
		VCard husband = contact("John Doe", "+41446681800");

		assertThat(remover.removeSharedNumbers(List.of(wife, husband))).isEmpty();
		assertThat(wife.getTelephoneNumbers()).hasSize(1);
		assertThat(husband.getTelephoneNumbers()).hasSize(1);
	}

	@Test
	void sameNumberTwiceOnOneContactCountsAsOneOwner() {
		var remover = new SharedPhoneNumberRemover(enabled(3));
		VCard doubled = contact("Jane Doe", "+41446681800", "+41446681800");
		VCard other = contact("John Doe", "+41446681800");

		assertThat(remover.removeSharedNumbers(List.of(doubled, other))).isEmpty();
	}

	@Test
	void disabledByDefault() {
		var remover = new SharedPhoneNumberRemover(CleaningProperties.defaults());
		List<VCard> office = List.of(contact("A", "+41440000001"), contact("B", "+41440000001"),
				contact("C", "+41440000001"));

		assertThat(remover.removeSharedNumbers(office)).isEmpty();
		assertThat(office.getFirst().getTelephoneNumbers()).hasSize(1);
	}

}
