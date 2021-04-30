/*
 * Copyright 2020-2021 the original author or authors.
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
package org.springframework.graphql.web.webmvc;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.web.WebGraphQLHandler;
import org.springframework.graphql.web.WebInput;
import org.springframework.util.Assert;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * GraphQL handler to expose as a WebMvc.fn endpoint via
 * {@link org.springframework.web.servlet.function.RouterFunctions}.
 */
public class GraphQLHttpHandler {

	private final static Log logger = LogFactory.getLog(GraphQLHttpHandler.class);

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_PARAMETERIZED_TYPE_REF =
			new ParameterizedTypeReference<Map<String, Object>>() {};


	private final WebGraphQLHandler graphQLHandler;


	/**
	 * Create a new instance.
	 * @param graphQLHandler common handler for GraphQL over HTTP requests
	 */
	public GraphQLHttpHandler(WebGraphQLHandler graphQLHandler) {
		Assert.notNull(graphQLHandler, "WebGraphQLHandler is required");
		this.graphQLHandler = graphQLHandler;
	}


	/**
	 * {@inheritDoc}
	 *
	 * @throws ServletException may be raised when reading the request body,
	 * e.g. {@link HttpMediaTypeNotSupportedException}.
	 */
	public ServerResponse handle(ServerRequest request) throws ServletException {
		WebInput input = new WebInput(request.uri(), request.headers().asHttpHeaders(), readBody(request), null);
		if (logger.isDebugEnabled()) {
			logger.debug("Executing: " + input);
		}
		Mono<ServerResponse> responseMono = this.graphQLHandler.handle(input)
				.map(output -> {
					if (logger.isDebugEnabled()) {
						logger.debug("Execution complete");
					}
					ServerResponse.BodyBuilder builder = ServerResponse.ok();
					if (output.getResponseHeaders() != null) {
						builder.headers(headers -> headers.putAll(output.getResponseHeaders()));
					}
					return builder.body(output.toSpecification());
				});
		return ServerResponse.async(responseMono);
	}

	private static Map<String, Object> readBody(ServerRequest request) throws ServletException {
		try {
			return request.body(MAP_PARAMETERIZED_TYPE_REF);
		}
		catch (IOException ex) {
			throw new ServerWebInputException("I/O error while reading request body", null, ex);
		}
	}

}