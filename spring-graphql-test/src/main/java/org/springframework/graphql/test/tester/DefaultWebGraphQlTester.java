/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.graphql.test.tester;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import graphql.ExecutionResult;

import org.springframework.graphql.RequestInput;
import org.springframework.graphql.web.WebGraphQlHandler;
import org.springframework.graphql.web.WebInput;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Default implementation of {@link WebGraphQlTester}.
 *
 * @author Rossen Stoyanchev
 */
class DefaultWebGraphQlTester extends DefaultGraphQlTester implements WebGraphQlTester {

	@Nullable
	private final HttpHeaders defaultHeaders;


	DefaultWebGraphQlTester(RequestStrategy requestStrategy, @Nullable HttpHeaders defaultHeaders) {
		super(requestStrategy);
		this.defaultHeaders = defaultHeaders;
	}


	@Override
	public WebRequestSpec query(String query) {
		return new DefaultWebRequestSpec(getRequestStrategy(), query, this.defaultHeaders);
	}


	/**
	 * Default implementation to build {@link WebGraphQlTester}.
	 */
	final static class DefaultBuilder implements WebGraphQlTester.Builder {

		private final Supplier<RequestStrategy> requestStrategySupplier;

		private final BuilderDelegate delegate = new BuilderDelegate();

		@Nullable
		private HttpHeaders headers;

		DefaultBuilder(WebTestClient client) {
			this.requestStrategySupplier = () ->
					new WebTestClientRequestStrategy(
							client.mutate().responseTimeout(this.delegate.getResponseTimeout()).build(),
							this.delegate.initJsonPathConfig());
		}

		DefaultBuilder(WebGraphQlHandler handler) {
			this.requestStrategySupplier = () ->
					new WebGraphQlHandlerRequestStrategy(handler,
							this.delegate.initJsonPathConfig(),
							this.delegate.getResponseTimeout());
		}

		@Override
		public DefaultBuilder jsonPathConfig(Configuration config) {
			this.delegate.jsonPathConfig(config);
			return this;
		}

		@Override
		public DefaultBuilder responseTimeout(Duration timeout) {
			this.delegate.responseTimeout(timeout);
			return this;
		}

		@Override
		public DefaultBuilder defaultHeader(String headerName, String... headerValues) {
			this.headers = (this.headers != null ? this.headers : new HttpHeaders());
			for (String headerValue : headerValues) {
				this.headers.add(headerName, headerValue);
			}
			return this;
		}

		@Override
		public WebGraphQlTester.Builder defaultHeaders(Consumer<HttpHeaders> headersConsumer) {
			this.headers = (this.headers != null ? this.headers : new HttpHeaders());
			headersConsumer.accept(this.headers);
			return this;
		}

		@Override
		public WebGraphQlTester build() {
			return new DefaultWebGraphQlTester(this.requestStrategySupplier.get(), this.headers);
		}
	}

	/**
	 * {@link RequestStrategy} that works as an HTTP client with requests executed through
	 * {@link WebTestClient} that in turn may work connect with or without a live server
	 * for Spring MVC and WebFlux.
	 */
	private static class WebTestClientRequestStrategy implements RequestStrategy {

		private final WebTestClient client;

		private final Configuration jsonPathConfig;

		WebTestClientRequestStrategy(WebTestClient client, Configuration jsonPathConfig) {
			this.client = client;
			this.jsonPathConfig = jsonPathConfig;
		}

		@Override
		public ResponseSpec execute(RequestInput requestInput) {
			EntityExchangeResult<byte[]> result = this.client.post()
					.contentType(MediaType.APPLICATION_JSON)
					.headers(headers -> headers.putAll(getHeaders(requestInput)))
					.bodyValue(requestInput)
					.exchange()
					.expectStatus()
					.isOk()
					.expectHeader()
					.contentType(MediaType.APPLICATION_JSON)
					.expectBody()
					.returnResult();

			byte[] bytes = result.getResponseBodyContent();
			Assert.notNull(bytes, "Expected GraphQL response content");
			String content = new String(bytes, StandardCharsets.UTF_8);
			DocumentContext documentContext = JsonPath.parse(content, this.jsonPathConfig);

			return new DefaultResponseSpec(documentContext, result::assertWithDiagnostics);
		}

		@Override
		public SubscriptionSpec executeSubscription(RequestInput requestInput) {
			FluxExchangeResult<TestExecutionResult> exchangeResult = this.client.post()
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.TEXT_EVENT_STREAM)
					.headers(headers -> headers.putAll(getHeaders(requestInput)))
					.bodyValue(requestInput)
					.exchange()
					.expectStatus()
					.isOk()
					.expectHeader()
					.contentType(MediaType.TEXT_EVENT_STREAM)
					.returnResult(TestExecutionResult.class);

			return new DefaultSubscriptionSpec(exchangeResult.getResponseBody().cast(ExecutionResult.class),
					this.jsonPathConfig, exchangeResult::assertWithDiagnostics);
		}

		private HttpHeaders getHeaders(RequestInput requestInput) {
			Assert.isInstanceOf(WebInput.class, requestInput);
			return ((WebInput) requestInput).getHeaders();
		}

	}

	/**
	 * {@link RequestStrategy} that performs requests directly on
	 * {@link WebGraphQlHandler}, i.e. Web request testing without a transport.
	 */
	private static class WebGraphQlHandlerRequestStrategy extends AbstractDirectRequestStrategy {

		private final WebGraphQlHandler graphQlHandler;

		WebGraphQlHandlerRequestStrategy(WebGraphQlHandler handler, Configuration config, Duration responseTimeout) {
			super(config, responseTimeout);
			this.graphQlHandler = handler;
		}

		protected ExecutionResult executeInternal(RequestInput input) {
			Assert.isInstanceOf(WebInput.class, input);
			WebInput webInput = (WebInput) input;
			ExecutionResult result = this.graphQlHandler.handle(webInput).block(getResponseTimeout());
			Assert.notNull(result, "Expected ExecutionResult");
			return result;
		}

	}

	private static final class DefaultWebRequestSpec extends DefaultRequestSpec implements WebRequestSpec {

		private static final URI DEFAULT_URL = URI.create("");

		private final HttpHeaders headers = new HttpHeaders();

		DefaultWebRequestSpec(RequestStrategy requestStrategy, String query, @Nullable HttpHeaders headers) {
			super(requestStrategy, query);
			if (!CollectionUtils.isEmpty(headers)) {
				this.headers.putAll(headers);
			}
		}

		@Override
		public WebRequestSpec header(String headerName, String... headerValues) {
			for (String headerValue : headerValues) {
				this.headers.add(headerName, headerValue);
			}
			return this;
		}

		@Override
		public WebRequestSpec headers(Consumer<HttpHeaders> headersConsumer) {
			headersConsumer.accept(this.headers);
			return this;
		}

		@Override
		protected RequestInput createRequestInput() {
			RequestInput requestInput = super.createRequestInput();
			return new WebInput(DEFAULT_URL, this.headers, requestInput.toMap(), null);
		}

	}

}
