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
			"http://picasaweb.google.ch/jane.doe", "http://friendfeed.com/janedoe",
			"https://www.xing.com/profile/Jane_Doe", "https://www.facebook.com/jane.doe", "https://twitter.com/janedoe",
			"https://x.com/janedoe", "https://t.co/AbC123", "https://bsky.app/profile/janedoe.bsky.social",
			"https://mastodon.social/@janedoe", "https://www.instagram.com/janedoe/",
			"https://www.threads.net/@janedoe", "https://www.tiktok.com/@janedoe", "https://vimeo.com/janedoe",
			"https://www.flickr.com/photos/janedoe" })
	void removesDeadAndAggregatorServiceUrls(String url) {
		VCard vcard = new VCard();
		vcard.addUrl(url);

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getUrls()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://www.linkedin.com/in/janedoe", "https://janedoe.example.com",
			"https://github.com/janedoe", "https://xkcd.com/927/", "https://tex.co.example/page" })
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
