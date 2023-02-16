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

import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;

/**
 * The implementation of {@link QueryEnhancer} using {@link Jpql31Parser}.
 *
 * @author Greg Turnquist
 * @since 3.1
 */
public class Jpql31ParserQueryEnhancer implements QueryEnhancer {

	private final DeclaredQuery query;

	public Jpql31ParserQueryEnhancer(DeclaredQuery query) {
		this.query = query;
	}

	@Override
	public String applySorting(Sort sort, @Nullable String alias) {
		return SpringDataJpql31Utils.query(this.query.getQueryString(), sort);
	}

	@Override
	public String detectAlias() {
		return SpringDataJpql31Utils.alias(this.query.getQueryString());
	}

	@Override
	public String createCountQueryFor(@Nullable String countProjection) {
		return SpringDataJpql31Utils.countQuery(this.query.getQueryString(), countProjection);

	}

	@Override
	public boolean hasConstructorExpression() {
		return SpringDataJpql31Utils.hasConstructorExpression(getQuery().getQueryString());
	}

	@Override
	public String getProjection() {
		return SpringDataJpql31Utils.projection(this.query.getQueryString());
	}

	@Override
	public Set<String> getJoinAliases() {
		return Set.of();
	}

	@Override
	public DeclaredQuery getQuery() {
		return this.query;
	}
}
