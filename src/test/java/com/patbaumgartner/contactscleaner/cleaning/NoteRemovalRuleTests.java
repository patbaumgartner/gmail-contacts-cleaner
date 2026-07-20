package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoteRemovalRuleTests {

	private final NoteRemovalRule rule = new NoteRemovalRule();

	@Test
	void removesAllNotes() {
		VCard vcard = new VCard();
		vcard.addNote("synced from Nokia 6310i");
		vcard.addNote("imported 2009-04-01");

		assertThat(rule.apply(vcard)).isTrue();
		assertThat(vcard.getNotes()).isEmpty();
	}

	@Test
	void reportsNoChangeWhenThereAreNoNotes() {
		assertThat(rule.apply(new VCard())).isFalse();
	}

}
