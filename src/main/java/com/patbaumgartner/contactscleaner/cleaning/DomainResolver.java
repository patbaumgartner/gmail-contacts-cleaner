package com.patbaumgartner.contactscleaner.cleaning;

/**
 * Strategy for resolving mail domains, pluggable so tests never hit real DNS.
 */
public interface DomainResolver {

	/**
	 * Resolves the given domain.
	 * @param domain the bare domain of an e-mail address (e.g. {@code example.com})
	 * @return the resolution outcome, never {@code null}
	 */
	DomainResolution resolve(String domain);

}
