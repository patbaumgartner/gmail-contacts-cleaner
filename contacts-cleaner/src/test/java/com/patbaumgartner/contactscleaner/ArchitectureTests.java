package com.patbaumgartner.contactscleaner;

import com.enofex.taikai.Taikai;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforced with <a href="https://github.com/enofex/taikai">Taikai</a>
 * (ArchUnit). Complements the Spring Modulith verification in {@link ModularityTests}
 * with general coding conventions.
 */
class ArchitectureTests {

	@Test
	void enforcesArchitectureConventions() {
		Taikai.builder()
			.namespace("com.patbaumgartner.contactscleaner")
			.java((java) -> java.noUsageOfDeprecatedAPIs()
				.noUsageOfSystemOutOrErr()
				.finalClassesShouldNotHaveProtectedMembers()
				.classesShouldImplementHashCodeAndEquals()
				.methodsShouldNotDeclareGenericExceptions()
				.fieldsShouldNotBePublic()
				.imports((imports) -> imports.shouldHaveNoCycles().shouldNotImport("..shaded.."))
				.naming((naming) -> naming.classesShouldNotMatch(".*Impl").interfacesShouldNotHavePrefixI()))
			.logging((logging) -> logging.loggersShouldFollowConventions(org.slf4j.Logger.class, "log|logger"))
			.spring((spring) -> spring.noAutowiredFields()
				.boot((boot) -> boot.springBootApplicationShouldBeIn("com.patbaumgartner.contactscleaner"))
				.services((services) -> services.shouldNotDependOnControllers()))
			.build()
			.check();
	}

}
