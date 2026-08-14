package com.patbaumgartner.contactscleaner.peopleapi;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ContactIdentifiersTests {

	@Test
	void lowerCasesAndTrimsEmailAddresses() {
		ContactIdentifiers identifiers = ContactIdentifiers.of(List.of("  Jane.Doe@GMAIL.com ", "jane.doe@gmail.com"),
				List.of());

		assertThat(identifiers.emailAddresses()).containsExactly("jane.doe@gmail.com");
	}

	@Test
	void foldsEveryWrittenFormOfTheSameNumberIntoOneKey() {
		ContactIdentifiers identifiers = ContactIdentifiers.of(List.of(),
				List.of("+41 44 668 18 00", "0041446681800", "+41446681800", "(0041) 44-668.18 00"));

		assertThat(identifiers.phoneNumbers()).containsExactly("41446681800");
	}

	@Test
	void ignoresNullAndBlankValues() {
		ContactIdentifiers identifiers = ContactIdentifiers.of(Arrays.asList(null, "", "   "),
				Arrays.asList(null, "", "---"));

		assertThat(identifiers.emailAddresses()).isEmpty();
		assertThat(identifiers.phoneNumbers()).isEmpty();
	}

	@Test
	void noneMatchesNothing() {
		assertThat(ContactIdentifiers.NONE.emailAddresses()).isEmpty();
		assertThat(ContactIdentifiers.NONE.phoneNumbers()).isEmpty();
	}

	@Test
	void isImmutable() {
		ContactIdentifiers identifiers = ContactIdentifiers.of(List.of("jane@example.test"), List.of("+41446681800"));

		assertThatExceptionOfType(UnsupportedOperationException.class)
			.isThrownBy(() -> identifiers.emailAddresses().add("intruder@example.test"));
		assertThatExceptionOfType(UnsupportedOperationException.class)
			.isThrownBy(() -> identifiers.phoneNumbers().add("+41000000000"));
	}

	@Test
	void keepsExplicitlyProvidedSets() {
		ContactIdentifiers identifiers = new ContactIdentifiers(Set.of("jane@example.test"), Set.of("41446681800"));

		assertThat(identifiers.emailAddresses()).containsExactly("jane@example.test");
		assertThat(identifiers.phoneNumbers()).containsExactly("41446681800");
	}

}
