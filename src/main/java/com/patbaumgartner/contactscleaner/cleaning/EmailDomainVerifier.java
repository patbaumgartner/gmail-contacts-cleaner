package com.patbaumgartner.contactscleaner.cleaning;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ezvcard.VCard;
import ezvcard.property.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Cross-contact pass: removes e-mail addresses whose domain no longer exists in DNS —
 * mail to them is guaranteed to bounce. Typical victims are addresses at long-defunct
 * employers or ISPs from the 2000s.
 *
 * <p>
 * Deliberately cautious:
 * <ul>
 * <li>an address is removed only on an authoritative <strong>NXDOMAIN</strong>; DNS
 * timeouts or server errors never count as proof (fail-open),</li>
 * <li>each domain is resolved once per run and cached — 5000 contacts at the same three
 * employers cause three lookups, not thousands,</li>
 * <li>disabled by default (network access + destructive); enable via
 * {@code contacts-cleaner.cleaning.verify-email-domains=true} and test with dry-run
 * first.</li>
 * </ul>
 */
@Component
public class EmailDomainVerifier {

	private static final Logger log = LoggerFactory.getLogger(EmailDomainVerifier.class);

	private final CleaningProperties properties;

	private final DomainResolver domainResolver;

	public EmailDomainVerifier(CleaningProperties properties, DomainResolver domainResolver) {
		this.properties = properties;
		this.domainResolver = domainResolver;
	}

	/**
	 * Removes undeliverable e-mail addresses from the given contacts in place.
	 * @param vcards all contacts of one address book, already individually cleaned
	 * @return the contacts that were modified (identity-based), empty when disabled
	 */
	public Set<VCard> removeUndeliverableAddresses(List<VCard> vcards) {
		if (!this.properties.verifyEmailDomains()) {
			return Set.of();
		}
		Map<String, DomainResolution> cache = new HashMap<>();
		Set<VCard> changed = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		for (VCard vcard : vcards) {
			for (Iterator<Email> iterator = vcard.getEmails().iterator(); iterator.hasNext();) {
				String address = iterator.next().getValue();
				String domain = domainOf(address);
				if (domain == null) {
					continue;
				}
				DomainResolution resolution = cache.computeIfAbsent(domain, this.domainResolver::resolve);
				if (resolution == DomainResolution.NON_EXISTENT) {
					log.info("Removing undeliverable address {} (domain {} does not exist) from '{}'", address, domain,
							displayName(vcard));
					iterator.remove();
					changed.add(vcard);
				}
			}
		}
		long dead = cache.values().stream().filter((r) -> r == DomainResolution.NON_EXISTENT).count();
		log.debug("Verified {} distinct mail domains, {} non-existent", cache.size(), dead);
		return changed;
	}

	private String domainOf(String address) {
		if (address == null) {
			return null;
		}
		int at = address.lastIndexOf('@');
		if (at < 1 || at == address.length() - 1) {
			return null;
		}
		return address.substring(at + 1).trim().toLowerCase(Locale.ROOT);
	}

	private String displayName(VCard vcard) {
		return (vcard.getFormattedName() != null && vcard.getFormattedName().getValue() != null)
				? vcard.getFormattedName().getValue() : "<unnamed>";
	}

}
