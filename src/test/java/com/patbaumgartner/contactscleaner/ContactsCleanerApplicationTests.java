package com.patbaumgartner.contactscleaner;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ContactsCleanerApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
		assertThat(this.context.containsBean("contactsCleanupService")).isTrue();
		assertThat(this.context.containsBean("contactCleaner")).isTrue();
		assertThat(this.context.containsBean("googleCardDavClient")).isTrue();
	}

	@Test
	void schedulerIsDisabledOutsideServerProfile() {
		assertThat(this.context.containsBean("contactsCleanupScheduler")).isFalse();
	}

}
