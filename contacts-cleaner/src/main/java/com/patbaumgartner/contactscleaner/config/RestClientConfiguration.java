package com.patbaumgartner.contactscleaner.config;

import com.patbaumgartner.contactscleaner.carddav.CardDavProperties;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.http.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;

/**
 * HTTP client wiring for the CardDAV module.
 *
 * <p>
 * Uses Apache HttpClient 5 because the JDK client refuses non-standard HTTP methods
 * such as the WebDAV {@code REPORT} verb required by CardDAV.
 */
@Configuration(proxyBeanMethods = false)
class RestClientConfiguration {

	@Bean
	RestClient carddavRestClient(RestClient.Builder builder, CardDavProperties properties) {
		CloseableHttpClient httpClient = HttpClients.custom().disableCookieManagement().build();
		ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.httpComponents()
			.withHttpClient(httpClient)
			.build(ClientHttpRequestFactorySettings.defaults()
				.withConnectTimeout(properties.connectTimeout())
				.withReadTimeout(properties.readTimeout()));
		return builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
	}

}
