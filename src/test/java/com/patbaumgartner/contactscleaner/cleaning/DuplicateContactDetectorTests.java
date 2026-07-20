package com.patbaumgartner.contactscleaner.cleaning;

import java.util.List;

import ezvcard.VCard;
import ezvcard.property.FormattedName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateContactDetectorTests {

	private final DuplicateContactDetector detector = new DuplicateContactDetector(CleaningProperties.defaults());

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
	void detectsContactsSharingAPhoneNumber() {
		List<DuplicateCandidate> candidates = detector.detect(
				List.of(contact("Jane Doe", "+41446681800", null), contact("J. Doe (work)", "+41 44 668 18 00", null)));

		assertThat(candidates).singleElement().satisfies((candidate) -> {
			assertThat(candidate.firstContact()).isEqualTo("Jane Doe");
			assertThat(candidate.secondContact()).isEqualTo("J. Doe (work)");
			assertThat(candidate.reason()).contains("phone number");
		});
	}

	@Test
	void detectsContactsSharingAnEmailAddress() {
		List<DuplicateCandidate> candidates = detector.detect(List.of(contact("Jane Doe", null, "jane.doe@gmail.com"),
				contact("Doe Jane", null, "Jane.Doe@gmail.com")));

		assertThat(candidates).singleElement()
			.satisfies((candidate) -> assertThat(candidate.reason()).contains("e-mail address"));
	}

	@Test
	void detectsNearIdenticalNames() {
		List<DuplicateCandidate> candidates = detector
			.detect(List.of(contact("Jane Doe", "+41446681800", null), contact("Jane  Doe", "+41791234567", null)));

		assertThat(candidates).singleElement()
			.satisfies((candidate) -> assertThat(candidate.reason()).contains("similar name"));
	}

	@Test
	void reportsFlippedNamePairsForGooglesMergeTool() {
		List<DuplicateCandidate> candidates = detector.detect(
				List.of(contact("Tudor Girba", "+41791111111", null), contact("Girba Tudor", "+41792222222", null)));

		assertThat(candidates).singleElement()
			.satisfies((candidate) -> assertThat(candidate.reason()).contains("different word order"));
	}

	@Test
	void handlesNamesWithRepeatedTokens() {
		List<DuplicateCandidate> candidates = detector.detect(
				List.of(contact("Yassine Yassine", "+41791111111", null), contact("Max Muster", "+41792222222", null)));

		assertThat(candidates).isEmpty();
	}

	@Test
	void reportsEachPairOnlyOnce() {
		List<DuplicateCandidate> candidates = detector
			.detect(List.of(contact("Jane Doe", "+41446681800", "jane.doe@gmail.com"),
					contact("Jane Doe", "+41446681800", "jane.doe@gmail.com")));

		assertThat(candidates).hasSize(1);
	}

	@Test
	void ignoresSwitchboardNumbersSharedByThreeOrMoreContacts() {
		List<DuplicateCandidate> candidates = detector.detect(List.of(contact("Colleague One", "+41712286788", null),
				contact("Colleague Two", "+41712286788", null), contact("Colleague Three", "+41712286788", null)));

		assertThat(candidates).isEmpty();
	}

	@Test
	void distinctContactsProduceNoCandidates() {
		List<DuplicateCandidate> candidates = detector
			.detect(List.of(contact("Jane Doe", "+41446681800", "jane.doe@gmail.com"),
					contact("Max Muster", "+41791234567", "max@example.com")));

		assertThat(candidates).isEmpty();
	}

	@Test
	void ignoresUnnamedContactsForNameMatching() {
		List<DuplicateCandidate> candidates = detector
			.detect(List.of(new VCard(), new VCard(), contact("Jane Doe", null, null)));

		assertThat(candidates).isEmpty();
	}

	@Test
	void returnsEmptyWhenDetectionIsDisabled() {
		var disabled = new DuplicateContactDetector(new CleaningProperties(true, "", true, false, false, true, true,
				true, true, false, true, true, true, true, true, true, false, false, true, true, true,
				java.util.List.of("Age"), java.util.List.of(), true, true, true, false, 3, false, false));

		assertThat(disabled
			.detect(List.of(contact("Jane Doe", "+41446681800", null), contact("Jane Doe", "+41446681800", null))))
			.isEmpty();
	}

}
