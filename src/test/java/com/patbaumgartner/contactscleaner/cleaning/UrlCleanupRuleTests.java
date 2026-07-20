package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Url;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class UrlCleanupRuleTests {

	private final UrlCleanupRule rule = new UrlCleanupRule();

	@ParameterizedTest
	@ValueSource(strings = { "http://klout.com/janedoe", "https://secure.gravatar.com/avatar/abc123",
			"https://plus.google.com/1234567890", "http://profiles.google.com/jane.doe",
			"http://www.google.com/profiles/jane.doe", "https://picasaweb.google.com/jane.doe",
			"http://picasaweb.google.ch/jane.doe", "http://friendfeed.com/janedoe" })
	void removesDeadAndAggregatorServiceUrls(String url) {
		VCard vcard = new VCard();
		vcard.addUrl(url);

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getUrls()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://www.linkedin.com/in/janedoe", "https://www.xing.com/profile/Jane_Doe",
			"https://twitter.com/janedoe", "https://janedoe.example.com", "https://github.com/janedoe" })
	void keepsLivingNetworksAndPersonalSites(String url) {
		VCard vcard = new VCard();
		vcard.addUrl(url);

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getUrls()).hasSize(1);
	}

	@Test
	void trimsAndDeduplicatesUrls() {
		VCard vcard = new VCard();
		vcard.addUrl(" https://example.com/jane ");
		vcard.addUrl("https://example.com/jane");
		vcard.addUrl("HTTPS://EXAMPLE.COM/JANE");
		vcard.addUrl("https://example.com/other");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getUrls()).extracting(Url::getValue)
			.containsExactly("https://example.com/jane", "https://example.com/other");
	}

	@Test
	void handlesContactsWithoutUrls() {
		assertThat(this.rule.apply(new VCard())).isFalse();
	}

}
