package com.patbaumgartner.contactscleaner.cleaning;

import java.util.Hashtable;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;

import org.springframework.stereotype.Component;

/**
 * Resolves mail domains via DNS (JNDI DNS provider): a domain is considered deliverable
 * when it has an {@code MX} record or — per RFC 5321 fallback — an {@code A}/{@code AAAA}
 * record. A mail-only domain without any website therefore counts as deliverable.
 *
 * <p>
 * Crucial distinction: <strong>NODATA is not NXDOMAIN</strong>. A domain that exists but
 * has none of the queried record types (parked domain, TXT-only, split-horizon DNS,
 * filtering resolver) yields an empty answer and maps to {@link DomainResolution#UNKNOWN}
 * — never to non-existence. Only an authoritative name-not-found error proves the domain
 * is gone.
 */
@Component
class JndiDomainResolver implements DomainResolver {

	private static final String DNS_TIMEOUT_MS = "3000";

	@Override
	public DomainResolution resolve(String domain) {
		try {
			Attributes attributes = context().getAttributes(domain, new String[] { "MX", "A", "AAAA" });
			if (attributes != null && attributes.size() > 0) {
				return DomainResolution.DELIVERABLE;
			}
			// Empty answer (NODATA): the domain exists but carries none of the
			// queried records — inconclusive, never treat as proof of death.
			return DomainResolution.UNKNOWN;
		}
		catch (NameNotFoundException ex) {
			// Authoritative NXDOMAIN — the domain does not exist.
			return DomainResolution.NON_EXISTENT;
		}
		catch (NamingException ex) {
			// Timeout, SERVFAIL, no network — never treat as proof of non-existence.
			return DomainResolution.UNKNOWN;
		}
	}

	private InitialDirContext context() throws NamingException {
		Hashtable<String, String> env = new Hashtable<>();
		env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
		env.put("com.sun.jndi.dns.timeout.initial", DNS_TIMEOUT_MS);
		env.put("com.sun.jndi.dns.timeout.retries", "1");
		return new InitialDirContext(env);
	}

}
