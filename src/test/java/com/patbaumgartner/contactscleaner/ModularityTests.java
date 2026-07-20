package com.patbaumgartner.contactscleaner;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifies the Spring Modulith module structure: no cyclic dependencies, no access to
 * module internals — and generates up-to-date module documentation (PlantUML component
 * diagrams and module canvases) under {@code target/spring-modulith-docs}.
 */
class ModularityTests {

	private static final ApplicationModules modules = ApplicationModules.of(ContactsCleanerApplication.class);

	@Test
	void verifiesModularStructure() {
		modules.verify();
	}

	@Test
	void createsModuleDocumentation() {
		new Documenter(modules).writeDocumentation();
	}

}
