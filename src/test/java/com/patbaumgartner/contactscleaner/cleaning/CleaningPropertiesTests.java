package com.patbaumgartner.contactscleaner.cleaning;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class CleaningPropertiesTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void builderDefaultsMatchTheValuesSpringBinds() {
		this.runner.run((context) -> assertThat(context.getBean(CleaningProperties.class))
			.isEqualTo(CleaningProperties.defaults()));
	}

	@Test
	void toBuilderRoundTripsEveryComponent() {
		CleaningProperties customized = CleaningProperties.builder()
			.normalizePhoneNumbers(false)
			.phoneRegion("CH")
			.removeDuplicatePhoneNumbers(false)
			.correctPhoneTypes(false)
			.removeFaxNumbers(true)
			.removeInvalidPhoneNumbers(true)
			.normalizeEmailAddresses(false)
			.removeDuplicateEmailAddresses(false)
			.removeInvalidEmails(false)
			.verifyEmailDomains(true)
			.trimNames(false)
			.removeJunkNameSuffixes(false)
			.repairNames(false)
			.removeWrappingNameQuotes(false)
			.repairCommaFormattedNames(false)
			.normalizeLabels(false)
			.removeEmptyProperties(false)
			.removeRedundantAddresses(false)
			.removeGeoCoordinateAddresses(false)
			.detectDuplicateContacts(false)
			.repairFlippedNames(false)
			.extractBirthdays(false)
			.removeSocialNetworkNotes(false)
			.cleanUrls(false)
			.removeInstantMessengers(false)
			.removeCustomFields(List.of("Anniversary"))
			.removeOrganizations(List.of("Acme"))
			.removeAdditionalOrganizations(true)
			.removeSelfOrganizations(false)
			.removeDanglingTitles(false)
			.canonicalizeOrganizations(false)
			.removeSharedPhoneNumbers(true)
			.sharedPhoneNumberThreshold(5)
			.removeNotes(true)
			.deleteEmptyContacts(true)
			.deleteBirthdayOnlyContacts(true)
			.inferNamesFromEmailAddresses(false)
			.removeEmailDomains(List.of("former.example"))
			.build();

		assertThat(customized.toBuilder().build()).isEqualTo(customized);
		assertThat(customized).isNotEqualTo(CleaningProperties.defaults());
	}

	@Test
	void normalizesListComponents() {
		CleaningProperties properties = CleaningProperties.builder()
			.removeOrganizations(List.of("Acme", "   "))
			.removeEmailDomains(List.of(" FORMER.example ", "former.example", ""))
			.build();

		assertThat(properties.removeOrganizations()).containsExactly("Acme");
		assertThat(properties.removeEmailDomains()).containsExactly("former.example");
	}

	@Test
	void rejectsASharedPhoneNumberThresholdThatWouldDeleteEveryNumber() {
		this.runner.withPropertyValues("contacts-cleaner.cleaning.shared-phone-number-threshold=1")
			.run((context) -> assertThat(context).hasFailed()
				.getFailure()
				.hasStackTraceContaining("sharedPhoneNumberThreshold"));
	}

	@Test
	void acceptsASharedPhoneNumberThresholdOfTwo() {
		this.runner.withPropertyValues("contacts-cleaner.cleaning.shared-phone-number-threshold=2")
			.run((context) -> assertThat(context.getBean(CleaningProperties.class).sharedPhoneNumberThreshold())
				.isEqualTo(2));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(CleaningProperties.class)
	static class TestConfiguration {

	}

}
