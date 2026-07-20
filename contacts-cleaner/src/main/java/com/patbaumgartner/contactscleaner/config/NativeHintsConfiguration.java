package com.patbaumgartner.contactscleaner.config;

import ezvcard.VCard;
import ezvcard.io.scribe.ScribeIndex;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * GraalVM native-image hints. Registers reflection metadata for ez-vcard (whose scribe
 * index instantiates property scribes reflectively) and resource hints for validation
 * messages, so the app runs unchanged as a native executable.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(NativeHintsConfiguration.ContactsCleanerRuntimeHints.class)
class NativeHintsConfiguration {

	static class ContactsCleanerRuntimeHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			// ez-vcard core types used reflectively during parsing/writing
			hints.reflection()
				.registerType(VCard.class, MemberCategory.INVOKE_PUBLIC_METHODS,
						MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
				.registerType(ScribeIndex.class, MemberCategory.INVOKE_PUBLIC_METHODS,
						MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);

			// Bean Validation message interpolation resources
			hints.resources().registerPattern("org/hibernate/validator/ValidationMessages*.properties");
		}

	}

}
