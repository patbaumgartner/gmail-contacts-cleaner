package com.patbaumgartner.contactscleaner.cleaning;

/**
 * Result of resolving a mail domain.
 */
public enum DomainResolution {

	/** The domain has an MX, A or AAAA record — mail could be delivered. */
	DELIVERABLE,

	/** The domain definitively does not exist (NXDOMAIN). */
	NON_EXISTENT,

	/** DNS could not be queried (timeout, SERVFAIL) — no conclusion possible. */
	UNKNOWN

}
