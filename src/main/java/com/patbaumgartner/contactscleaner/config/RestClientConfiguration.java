package com.patbaumgartner.contactscleaner.config;

import java.net.URI;

import com.patbaumgartner.contactscleaner.carddav.CardDavProperties;
import com.patbaumgartner.contactscleaner.peopleapi.PeopleApiProperties;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client wiring for the CardDAV module.
 *
 * <p>
 * Uses Apache HttpClient 5 because CardDAV requires the WebDAV {@code REPORT} verb (RFC
 * 6352), which neither the JDK client nor Spring's default request factory accepts.
 * {@link WebDavAwareRequestFactory} extends the HttpComponents factory with support for
 * non-standard methods.
 */
@Configuration(proxyBeanMethods = false)
class RestClientConfiguration {

	@Bean
	RestClient carddavRestClient(RestClient.Builder builder, CardDavProperties properties) {
		CloseableHttpClient httpClient = HttpClients.custom()
			.setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
				.setDefaultConnectionConfig(ConnectionConfig.custom()
					.setConnectTimeout(properties.connectTimeout().toMillis(),
							java.util.concurrent.TimeUnit.MILLISECONDS)
					.build())
				.build())
			.disableCookieManagement()
			.build();
		WebDavAwareRequestFactory requestFactory = new WebDavAwareRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
	}

	@Bean
	RestClient peopleApiRestClient(RestClient.Builder builder, PeopleApiProperties properties) {
		return builder.baseUrl(properties.baseUrl()).build();
	}

	/**
	 * {@link HttpComponentsClientHttpRequestFactory} that also accepts WebDAV methods
	 * such as {@code REPORT}, which the base class rejects.
	 */
	static class WebDavAwareRequestFactory extends HttpComponentsClientHttpRequestFactory {

		WebDavAwareRequestFactory(CloseableHttpClient httpClient) {
			super(httpClient);
		}

		@Override
		protected ClassicHttpRequest createHttpUriRequest(HttpMethod httpMethod, URI uri) {
			return ClassicRequestBuilder.create(httpMethod.name()).setUri(uri).build();
		}

	}

}
