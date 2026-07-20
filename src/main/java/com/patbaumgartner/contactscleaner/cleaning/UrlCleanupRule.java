package com.patbaumgartner.contactscleaner.cleaning;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import ezvcard.VCard;
import ezvcard.property.Url;

/**
 * Cleans the contact's website URLs:
 *
 * <ol>
 * <li><strong>Dead / aggregator service removal</strong> — URLs pointing at services that
 * no longer exist (Klout, Google+, Google Profiles, Picasa Web, FriendFeed) or at pure
 * avatar plumbing (Gravatar). These were injected by social-aggregator sync tools of the
 * 2010s (Rapportive &amp; friends) and carry no information today. Living networks
 * (LinkedIn, XING, Facebook, ...) are deliberately kept.</li>
 * <li><strong>Normalization</strong> — trims surrounding whitespace.</li>
 * <li><strong>Deduplication</strong> — removes repeated URLs within one contact, keeping
 * the first occurrence.</li>
 * </ol>
 */
final class UrlCleanupRule implements VCardCleaningRule {

	/**
	 * Host fragments of services that are shut down, pure sync plumbing, or social
	 * networks whose profile links were bulk-injected by sync apps (Twitter/X, Bluesky,
	 * Mastodon, Instagram, ...). Matched against the lower-cased URL. LinkedIn and
	 * personal websites are kept.
	 */
	private static final List<Pattern> DEAD_SERVICE_PATTERNS = List.of(Pattern.compile(".*\\bklout\\.com/.*"),
			Pattern.compile(".*\\bgravatar\\.com/.*"), Pattern.compile(".*\\bplus\\.google\\.com/.*"),
			Pattern.compile(".*\\bprofiles\\.google\\.com/.*"), Pattern.compile(".*\\bgoogle\\.com/profiles.*"),
			Pattern.compile(".*\\bpicasaweb\\.google\\.[a-z.]+/.*"), Pattern.compile(".*\\bfriendfeed\\.com/.*"),
			Pattern.compile(".*\\bxing\\.com/.*"), Pattern.compile(".*\\bfacebook\\.com/.*"),
			Pattern.compile(".*\\bfb\\.com/.*"), Pattern.compile(".*\\btwitter\\.com/.*"),
			Pattern.compile("(?:https?://)?(?:www\\.)?x\\.com/.*"), Pattern.compile(".*\\bt\\.co/.*"),
			Pattern.compile(".*\\bbsky\\.(app|social)/.*"), Pattern.compile(".*\\bmastodon\\.[a-z.]+/.*"),
			Pattern.compile(".*\\binstagram\\.com/.*"), Pattern.compile(".*\\bthreads\\.net/.*"),
			Pattern.compile(".*\\btiktok\\.com/.*"), Pattern.compile(".*\\bsnapchat\\.com/.*"),
			Pattern.compile(".*\\bflickr\\.com/.*"), Pattern.compile(".*\\bvimeo\\.com/.*"),
			Pattern.compile(".*\\bfoursquare\\.com/.*"), Pattern.compile(".*\\babout\\.me/.*"));

	@Override
	public boolean apply(VCard vcard) {
		boolean changed = false;
		Set<String> seen = new HashSet<>();
		for (Iterator<Url> iterator = vcard.getUrls().iterator(); iterator.hasNext();) {
			Url url = iterator.next();
			String value = url.getValue();
			if (value == null) {
				continue;
			}
			String trimmed = value.trim();
			if (isDeadService(trimmed) || !seen.add(trimmed.toLowerCase(Locale.ROOT))) {
				iterator.remove();
				changed = true;
				continue;
			}
			if (!Objects.equals(value, trimmed)) {
				url.setValue(trimmed);
				changed = true;
			}
		}
		return changed;
	}

	private boolean isDeadService(String url) {
		String lower = url.toLowerCase(Locale.ROOT);
		return DEAD_SERVICE_PATTERNS.stream().anyMatch((pattern) -> pattern.matcher(lower).matches());
	}

}
