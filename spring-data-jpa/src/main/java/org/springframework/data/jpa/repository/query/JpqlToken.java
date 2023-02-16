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

import java.util.function.Supplier;

import org.antlr.v4.runtime.ParserRuleContext;

/**
 * A value type used to represent a JPQL token.
 *
 * @author Greg Turnquist
 * @since 3.1
 */
class JpqlToken {

	/**
	 * The text value of the token.
	 */
	private Supplier<String> token;

	/**
	 * The surrounding contextual information of the parsing rule the token came from.
	 */
	private ParserRuleContext context;

	/**
	 * Whether or not to render a space after the token itself is rendered.
	 */
	private JpqlTokenSpacer space = JpqlTokenSpacer.SPACE;

	/**
	 * Indicates if a line break should be rendered before the token itself is rendered (DEBUG only)
	 */
	private boolean lineBreak = false;

	/**
	 * Is this token for debug purposes only?
	 */
	private boolean debugOnly = false;

	public JpqlToken(Supplier<String> token, ParserRuleContext context) {

		this.token = token;
		this.context = context;
	}

	public JpqlToken(Supplier<String> token, ParserRuleContext context, JpqlTokenSpacer space) {

		this(token, context);
		this.space = space;
	}

	public JpqlToken(String token, ParserRuleContext ctx) {
		this(() -> token, ctx);
	}

	public JpqlToken(String token, ParserRuleContext ctx, JpqlTokenSpacer tokenSpace) {
		this(() -> token, ctx, tokenSpace);
	}

	public JpqlToken(String token, ParserRuleContext ctx, boolean lineBreak) {

		this(() -> token, ctx);
		this.lineBreak = lineBreak;
	}

	public String getToken() {
		return this.token.get();
	}

	public ParserRuleContext getContext() {
		return context;
	}

	public JpqlTokenSpacer getSpace() {
		return this.space;
	}

	public void setSpace(JpqlTokenSpacer space) {
		this.space = space;
	}

	public boolean isLineBreak() {
		return lineBreak;
	}

	public boolean isDebugOnly() {
		return debugOnly;
	}

	@Override
	public String toString() {
		return "JpqlToken{" + "token='" + token + '\'' + ", context=" + context + ", space=" + space + ", lineBreak="
				+ lineBreak + ", debugOnly=" + debugOnly + '}';
	}
}
