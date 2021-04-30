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
package io.spring.sample.graphql;

import graphql.GraphQL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.web.WebGraphQLHandler;
import org.springframework.graphql.test.tester.GraphQLTester;

/**
 * GraphQL query tests directly via {@link GraphQL}.
 */
@SpringBootTest
public class QueryTests {

	private GraphQLTester graphQLTester;


	@BeforeEach
	public void setUp(@Autowired WebGraphQLHandler handler) {
		this.graphQLTester = GraphQLTester.create(webInput ->
				handler.handle(webInput).contextWrite(context -> context.put("name", "James")));
	}


	@Test
	void greetingMono() {
		this.graphQLTester.query("{greetingMono}")
				.execute()
				.path("greetingMono")
				.entity(String.class)
				.isEqualTo("Hello James");
	}

	@Test
	void greetingsFlux() {
		this.graphQLTester.query("{greetingsFlux}")
				.execute()
				.path("greetingsFlux")
				.entityList(String.class)
				.containsExactly("Hi James", "Bonjour James", "Hola James", "Ciao James", "Zdravo James");
	}

}
