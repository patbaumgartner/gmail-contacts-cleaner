package com.patbaumgartner.contactscleaner.cleaning;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import ezvcard.VCard;
import ezvcard.property.Email;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailDomainVerifierTests {

	private static CleaningProperties enabled() {
		return new CleaningProperties(true, "", true, true, true, true, true, true, true, true, true, true, true, false,
				3, false, false);
	}

	private static VCard contact(String... addresses) {
		VCard vcard = new VCard();
		for (String address : addresses) {
			vcard.addEmail(new Email(address));
		}
		return vcard;
	}

	@Test
	void removesAddressesOfNonExistentDomains() {
		var verifier = new EmailDomainVerifier(enabled(), (domain) -> domain.equals("dead-company.example")
				? DomainResolution.NON_EXISTENT : DomainResolution.DELIVERABLE);
		VCard vcard = contact("jane@dead-company.example", "jane.doe@gmail.com");

		var changed = verifier.removeUndeliverableAddresses(List.of(vcard));

		assertThat(changed).containsExactly(vcard);
		assertThat(vcard.getEmails()).extracting(Email::getValue).containsExactly("jane.doe@gmail.com");
	}

	@Test
	void neverRemovesOnDnsUncertainty() {
		var verifier = new EmailDomainVerifier(enabled(), (domain) -> DomainResolution.UNKNOWN);
		VCard vcard = contact("jane@flaky-dns.example");

		assertThat(verifier.removeUndeliverableAddresses(List.of(vcard))).isEmpty();
		assertThat(vcard.getEmails()).hasSize(1);
	}

	@Test
	void resolvesEachDomainOnlyOnce() {
		AtomicInteger lookups = new AtomicInteger();
		var verifier = new EmailDomainVerifier(enabled(), (domain) -> {
			lookups.incrementAndGet();
			return DomainResolution.DELIVERABLE;
		});

		verifier.removeUndeliverableAddresses(List.of(contact("a@example.com"), contact("b@example.com"),
				contact("c@example.com", "d@other.example")));

		assertThat(lookups.get()).isEqualTo(2);
	}

	@Test
	void disabledByDefault() {
		var verifier = new EmailDomainVerifier(CleaningProperties.defaults(),
				(domain) -> DomainResolution.NON_EXISTENT);
		VCard vcard = contact("jane@dead-company.example");

		assertThat(verifier.removeUndeliverableAddresses(List.of(vcard))).isEmpty();
		assertThat(vcard.getEmails()).hasSize(1);
	}

	@Test
	void skipsMalformedAddressesWithoutDomain() {
		Map<String, DomainResolution> resolved = new java.util.HashMap<>();
		var verifier = new EmailDomainVerifier(enabled(), (domain) -> {
			resolved.put(domain, DomainResolution.DELIVERABLE);
			return DomainResolution.DELIVERABLE;
		});

		verifier.removeUndeliverableAddresses(List.of(contact("no-at-sign", "trailing@")));

		assertThat(resolved).isEmpty();
	}

}
