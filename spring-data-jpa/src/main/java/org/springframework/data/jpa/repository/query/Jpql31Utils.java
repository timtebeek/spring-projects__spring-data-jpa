/*
 * Copyright 2022-2023 the original author or authors.
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
package org.springframework.data.jpa.repository.query;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 * Collection of utility methods to support parsing JPQL 3.1 queries.
 *
 * @author Greg Turnquist
 * @since 3.1
 */
class Jpql31Utils {

	/**
	 * Shortcut to parse the {@literal query} but without failing fast.
	 *
	 * @param query
	 */
	static Jpql31Parser.StartContext parse(String query) {
		return parse(query, false);
	}

	/**
	 * Parse the provided {@literal query} per the spec.
	 *
	 * @param query
	 * @param failFast
	 */
	static Jpql31Parser.StartContext parse(String query, boolean failFast) {

		var lexer = new Jpql31Lexer(CharStreams.fromString(query));
		var parser = new Jpql31Parser(new CommonTokenStream(lexer));

		if (failFast) {
			parser.addErrorListener(new JpqlSyntaxErrorListener());
		}

		return parser.start();
	}

	/**
	 * Shortcut to parse the {@literal query} and fail fast.
	 *
	 * @param query
	 */
	static Jpql31Parser.StartContext parseWithFastFailure(String query) {
		return parse(query, true);
	}

}
