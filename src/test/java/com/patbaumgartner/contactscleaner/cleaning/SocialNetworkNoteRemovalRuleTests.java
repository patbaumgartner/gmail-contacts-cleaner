package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Note;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SocialNetworkNoteRemovalRuleTests {

	private final SocialNetworkNoteRemovalRule rule = new SocialNetworkNoteRemovalRule();

	@ParameterizedTest
	@ValueSource(strings = { "XING: https://www.xing.com/profile/Jane_Doe", "https://www.xing.com/profiles/Jane_Doe",
			"LinkedIn: http://www.linkedin.com/in/janedoe", "www.linkedin.com/pub/jane-doe/12/345/678",
			"linkedin: janedoe", "Created via LinkedIn", "Imported from XING", "xing:" })
	void removesNotesConsistingOnlyOfSyncDebris(String debris) {
		VCard vcard = new VCard();
		vcard.addNote(debris);

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getNotes()).isEmpty();
	}

	@Test
	void preservesUserWrittenTextInMixedNotes() {
		VCard vcard = new VCard();
		vcard.addNote("""
				Met at JavaLand 2024, likes hiking.
				XING: https://www.xing.com/profile/Jane_Doe
				Prefers e-mail over phone.""");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getNotes()).extracting(Note::getValue)
			.containsExactly("Met at JavaLand 2024, likes hiking.\nPrefers e-mail over phone.");
	}

	@Test
	void leavesUserNotesUntouched() {
		VCard vcard = new VCard();
		vcard.addNote("Met at JavaLand 2024. Ask about the LinkedIn talk she gave.");

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getNotes()).hasSize(1);
	}

	@Test
	void removesLinkedInImportPositionBlocks() {
		VCard vcard = new VCard();
		vcard.addNote("""
				Position: CTO & Co-Founder
				Connected on: 3/12/11, 10:11 PM


				Position: CTO & Co-Founder | Company: ACME Corp""");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getNotes()).isEmpty();
	}

	@Test
	void removesPipeCombinedPositionCompanyLinesEvenWithoutMarker() {
		VCard vcard = new VCard();
		vcard.addNote("Position: Co-Founder | Company: ACME GmbH");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getNotes()).isEmpty();
	}

	@Test
	void keepsUserWrittenPositionNotesWithoutTheLinkedInMarker() {
		VCard vcard = new VCard();
		vcard.addNote("Position: goalie in our hobby team");

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getNotes()).hasSize(1);
	}

	@Test
	void preservesUserTextInsideLinkedInImportNotes() {
		VCard vcard = new VCard();
		vcard.addNote("""
				Position: CTO & Co-Founder
				Connected on 12.03.2011
				Met her at Voxxed Days Zurich.""");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getNotes()).extracting(Note::getValue).containsExactly("Met her at Voxxed Days Zurich.");
	}

	@Test
	void handlesContactsWithoutNotes() {
		assertThat(this.rule.apply(new VCard())).isFalse();
	}

}
