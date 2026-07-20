package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Impp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstantMessengerRemovalRuleTests {

	private final InstantMessengerRemovalRule rule = new InstantMessengerRemovalRule();

	@Test
	void removesAllInstantMessengerHandles() {
		VCard vcard = new VCard();
		vcard.addImpp(Impp.aim("106607701"));
		vcard.addImpp(Impp.skype("hacky_cologne"));
		vcard.addImpp(Impp.icq("12345678"));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getImpps()).isEmpty();
	}

	@Test
	void handlesContactsWithoutImHandles() {
		assertThat(this.rule.apply(new VCard())).isFalse();
	}

}
