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

import static org.springframework.data.jpa.repository.query.JpqlTokenSpacer.*;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Collection of utility methods to support applying Spring Data transformations to JPQL 3.1 queries.
 *
 * @author Greg Turnquist
 * @since 3.1
 */
class SpringDataJpql31Utils {

	private static final Log LOG = LogFactory.getLog(SpringDataJpql31Utils.class);

	/**
	 * Parse the {@literal query} and apply Spring Data JPA's transformations.
	 *
	 * @param query
	 */
	static String query(String query) {

		LOG.debug("Parsing query " + query);

		return withSpringJpqlVisitor(new SpringDataJpqlVisitor()) //
				.withQuery(query) //
				.failFast() //
				.query();
	}

	/**
	 * Parse the {@literal query} and apply Spring Data JPA's transformations, with debugging turned on.
	 *
	 * @param query
	 */
	static String queryDebug(String query) {

		return withSpringJpqlVisitor(new SpringDataJpqlVisitor()) //
				.withQuery(query) //
				.failFast() //
				.queryDebug();
	}

	/**
	 * Parse the {@literal query} and apply Spring Data JPA's transformations with {@link Sort} applied.
	 *
	 * @param query
	 * @param sort
	 */
	static String query(String query, Sort sort) {

		LOG.debug("Applying sort " + sort + " to " + query);

		String query1 = withSpringJpqlVisitor(new SpringDataJpqlVisitor()) //
				.withQuery(query) //
				.withSort(sort) //
				.failFast() //
				.query();
		return query1;
	}

	/**
	 * Parse the {@literal query} and apply Spring Data JPA's transformations to generate a count-based query.
	 *
	 * @param query
	 */
	static String countQuery(String query) {

		LOG.debug("Deriving a count query for " + query);

		try {
			return withSpringJpqlVisitor(new SpringDataJpqlVisitor()) //
					.withQuery(query) //
					.failFast() //
					.countQuery();
		} catch (JpqlSyntaxError e) {
			LOG.debug(e);
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Parse the {@literal query} and apply Spring Data JPA's transformations with {@link Sort} applied to generate a
	 * count-based query.
	 *
	 * @param query
	 * @param sort
	 */
	static String countQuery(String query, Sort sort) {

		LOG.debug("Deriving a count query for " + query + " // " + sort);

		try {
			return withSpringJpqlVisitor(new SpringDataJpqlVisitor()) //
					.withQuery(query) //
					.withSort(sort) //
					.failFast() //
					.countQuery();
		} catch (JpqlSyntaxError e) {
			LOG.debug(e);
			throw new IllegalArgumentException(e);
		}
	}

	static String countQuery(String query, String countProjection) {

		LOG.debug("Deriving a count query for " + query);

		try {
			return withSpringJpqlVisitor(new SpringDataJpqlVisitor()) //
					.withQuery(query) //
					.withCountProjection(countProjection) //
					.failFast() //
					.countQuery();
		} catch (JpqlSyntaxError e) {
			LOG.debug(e);
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Parse the {@literal query} and apply Spring Data JPA's transformations to generate count-based query, with
	 * debugging turned on.
	 *
	 * @param query
	 */
	static String countQueryDebug(String query) {

		try {
			return withSpringJpqlVisitor(new SpringDataJpqlVisitor()) //
					.withQuery(query) //
					.failFast() //
					.countQueryDebug();
		} catch (JpqlSyntaxError e) {
			LOG.debug(e);
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Parse the {@literal query} and extract the query's alias.
	 * <p/>
	 * TODO: Should it return {@literal null} or throw an {@link JpqlSyntaxError} if the query is invalid?
	 *
	 * @param query
	 * @returns either the designated alias of the query or {@literal null}
	 */
	@Nullable
	static String alias(String query) {

		LOG.debug("Looking up the alias for " + query);

		try {
			return withSpringJpqlVisitor(new SpringDataJpqlVisitor()) //
					.withQuery(query) //
					.failFast() //
					.alias();
		} catch (JpqlSyntaxError e) {
			LOG.debug(e);
			return null;
		}
	}

	/**
	 * Extract all the selected columns from the query, e.g. with {@literal select u.name, u.role from Employee u}, the
	 * projection is {@literal u.name, u.role}.
	 *
	 * @param query
	 */
	static String projection(String query) {

		LOG.debug("What is the projection for " + query);

		try {
			return withSpringJpqlVisitor(new SpringDataJpqlVisitor()) //
					.withQuery(query) //
					.failFast() //
					.projection();
		} catch (JpqlSyntaxError e) {
			LOG.debug(e);
			return "";
		}
	}

	/**
	 * Does the {@literal query} have a constructor expression? Returns {@literal false} for invalid queries.
	 *
	 * @param query
	 */
	static boolean hasConstructorExpression(String query) {

		// LOG.debug("Does " + query + " have a constructor expressions?");

		try {
			return withSpringJpqlVisitor(new SpringDataJpqlVisitor()) //
					.withQuery(query) //
					.failFast() //
					.hasConstructorExpression();
		} catch (JpqlSyntaxError e) {
			return false;
		}
	}

	/**
	 * Builder method to create a {@link Jpql31Parser} with a {@link SpringDataJpqlVisitor} registered.
	 *
	 * @param jpqlVisitor
	 */
	static Jpql31ParserBuilder withSpringJpqlVisitor(SpringDataJpqlVisitor jpqlVisitor) {
		return new Jpql31ParserBuilder(jpqlVisitor);
	}

	/**
	 * Utility method to create a rule-based "tag"
	 *
	 * @param ctx
	 */
	private static String TAG(ParserRuleContext ctx) {
		return "[" + simplified(ctx.getClass()) + "]";
	}

	/**
	 * Utility method to extract the "useful" name of a given {@link ParserRuleContext}.
	 *
	 * @param className
	 */
	private static String simplified(Class<? extends ParserRuleContext> className) {

		var simpleName = className.getSimpleName();
		var index = simpleName.indexOf("Context");
		return simpleName.substring(0, index);
	}

	/**
	 * Builder to assemble a {@link Jpql31Parser}.
	 *
	 * @author Greg Turnquist
	 * @since 3.1
	 */
	static class Jpql31ParserBuilder {

		private SpringDataJpqlVisitor jpqlVisitor;
		private String query;

		private boolean failFast = false;

		private String countProjection = "";

		Jpql31ParserBuilder(SpringDataJpqlVisitor jpqlVisitor) {

			Assert.notNull(jpqlVisitor, "jpqlVisitor cannot be null!");

			this.jpqlVisitor = jpqlVisitor;
		}

		// Assemble the details of the parser

		Jpql31ParserBuilder withQuery(String query) {

			Assert.hasText(query, "query cannot be null or empty!");

			this.query = query;
			return this;
		}

		Jpql31ParserBuilder withSort(Sort sort) {

			Assert.notNull(sort, "sort cannot be null!");

			this.jpqlVisitor.setSort(sort);
			return this;
		}

		Jpql31ParserBuilder withCountProjection(String countProjection) {

			if (countProjection != null) {
				this.countProjection = countProjection;
			}
			return this;

		}

		/**
		 * Register an error handler to force querying to fail faster.
		 */
		Jpql31ParserBuilder failFast() {

			this.failFast = true;
			return this;
		}

		// Terminal operations

		/**
		 * Create a plain query.
		 */
		String query() {

			var tree = parse();
			if (tree == null) {
				throw new IllegalArgumentException("Failed to parse '" + this.query + "'");
			}
			return transform(this.jpqlVisitor.visit(tree));
		}

		/**
		 * Create a plain query, but with debugging turned on.
		 */
		String queryDebug() {

			var tree = parse();
			if (tree == null) {
				throw new IllegalArgumentException("Failed to parse '" + this.query + "'");
			}
			return debug(this.jpqlVisitor.visit(tree));
		}

		/**
		 * Create a count-based query.
		 */
		String countQuery() {

			var tree = parse();
			if (tree == null) {
				return "";
			}
			var countingVisitor = this.jpqlVisitor.withCounts();
			return transform(countingVisitor.visit(tree));
		}

		/**
		 * Create a count-based query, but with debugging turned on.
		 */
		String countQueryDebug() {

			var tree = parse();
			if (tree == null) {
				return "";
			}
			var countingVisitor = this.jpqlVisitor.withCounts();
			return debug(countingVisitor.visit(tree));
		}

		/**
		 * Create a {@literal query}, but extract the alias from it.
		 *
		 * @returns either the designation alias of the query or {@literal null}
		 */
		@Nullable
		String alias() {

			var tree = parse();

			if (tree == null) {
				LOG.warn("Failed to parse " + this.query + ". See console for more details.");
				return null;
			}
			this.jpqlVisitor.visit(tree);
			return this.jpqlVisitor.getAlias();
		}

		/**
		 * Create a {@literal query} and extract the projection from it.
		 */
		String projection() {

			if (StringUtils.hasText(this.countProjection)) {
				return this.countProjection;
			}

			var tree = parse();
			if (tree == null) {
				return "";
			}
			this.jpqlVisitor.visit(tree);
			return transform(this.jpqlVisitor.getProjection());
		}

		/**
		 * Figure out if the {@literal query} uses a constructor expression.
		 */
		boolean hasConstructorExpression() {

			var tree = parse();
			if (tree == null) {
				throw new IllegalArgumentException("Failed to parse '" + this.query + "'");
			}
			this.jpqlVisitor.visit(tree);
			return this.jpqlVisitor.hasConstructorExpression();
		}

		/**
		 * Parse the query.
		 */
		private Jpql31Parser.StartContext parse() {
			return Jpql31Utils.parse(this.query, this.failFast);
		}

		/**
		 * Render the list of {@link JpqlToken}s into a query string.
		 *
		 * @param tokens
		 */
		private static String transform(List<JpqlToken> tokens) {

			if (tokens == null) {
				return "";
			}

			var results = new StringBuilder();

			tokens.stream() //
					.filter(token -> !token.isDebugOnly()) //
					.forEach(token -> {
						String tokenValue = token.getToken();
						results.append(tokenValue);
						if (token.getSpace() == SPACE) {
							results.append(" ");
						}
					});

			return results.toString().trim();
		}

		/**
		 * Render the list of {@link JpqlToken}s into a query string (with debugging turned on).
		 *
		 * @param tokens
		 */
		private static String debug(List<JpqlToken> tokens) {

			var results = new StringBuilder();

			tokens.forEach(token -> {

				if (token.isLineBreak()) {
					results.append("\n");
				}

				results.append(token.getToken());
				results.append(TAG(token.getContext()));
			});

			return results.toString();
		}

	}
}
