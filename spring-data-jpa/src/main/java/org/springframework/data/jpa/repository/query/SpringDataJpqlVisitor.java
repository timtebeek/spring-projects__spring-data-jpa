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

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;

class SpringDataJpqlVisitor extends Jpql31BaseVisitor<List<JpqlToken>> {

	private Sort sort;
	private boolean countQuery;

	private String alias = "";

	private List<JpqlToken> projection = null;

	private boolean hasConstructorExpression = false;

	SpringDataJpqlVisitor() {
		this(null);
	}

	SpringDataJpqlVisitor(@Nullable Sort sort) {

		this.sort = sort;
		this.countQuery = false;
	}

	/**
	 * Create a new {@link SpringDataJpqlVisitor} with counting switching on.
	 */
	SpringDataJpqlVisitor withCounts() {
		return new SpringDataJpqlVisitor(this.sort, true);
	}

	private SpringDataJpqlVisitor(Sort sort, boolean countQuery) {

		this(sort);
		this.countQuery = countQuery;
	}

	public void setSort(Sort sort) {
		this.sort = sort;
	}

	public String getAlias() {
		return this.alias;
	}

	public List<JpqlToken> getProjection() {
		return this.projection;
	}

	public boolean hasConstructorExpression() {
		return this.hasConstructorExpression;
	}

	/**
	 * Switch the last {@link JpqlToken} to {@link JpqlTokenSpacer#NO_SPACE}.
	 */
	private static List<JpqlToken> NOSPACE(List<JpqlToken> tokens) {

		if (!tokens.isEmpty()) {
			tokens.get(tokens.size() - 1).setSpace(NO_SPACE);
		}
		return tokens;
	}

	/**
	 * Switch the last {@link JpqlToken} to {@link JpqlTokenSpacer#SPACE}.
	 */
	private static List<JpqlToken> SPACE(List<JpqlToken> tokens) {

		if (!tokens.isEmpty()) {
			tokens.get(tokens.size() - 1).setSpace(SPACE);
		}

		return tokens;
	}

	/**
	 * Drop the very last entry from the list of {@link JpqlToken}s.
	 */
	private static List<JpqlToken> CLIP(List<JpqlToken> tokens) {

		if (!tokens.isEmpty()) {
			tokens.remove(tokens.size() - 1);
		}
		return tokens;
	}

	@Override
	public List<JpqlToken> visitStart(Jpql31Parser.StartContext ctx) {
		return visit(ctx.ql_statement());
	}

	@Override
	public List<JpqlToken> visitQl_statement(Jpql31Parser.Ql_statementContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.select_statement() != null) {
			tokens.addAll(visit(ctx.select_statement()));
		} else if (ctx.update_statement() != null) {
			tokens.addAll(visit(ctx.update_statement()));
		} else if (ctx.delete_statement() != null) {
			tokens.addAll(visit(ctx.delete_statement()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitSelect_statement(Jpql31Parser.Select_statementContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.select_clause()));
		tokens.addAll(visit(ctx.from_clause()));

		if (ctx.where_clause() != null) {
			tokens.addAll(visit(ctx.where_clause()));
		}

		if (ctx.groupby_clause() != null) {
			tokens.addAll(visit(ctx.groupby_clause()));
		}

		if (ctx.having_clause() != null) {
			tokens.addAll(visit(ctx.having_clause()));
		}

		if (!this.countQuery) {

			if (ctx.orderby_clause() != null) {
				tokens.addAll(visit(ctx.orderby_clause()));
			}

			if (this.sort != null && this.sort.isSorted()) {

				if (ctx.orderby_clause() != null) {

					NOSPACE(tokens);
					tokens.add(new JpqlToken(",", ctx));
				} else {
					tokens.add(new JpqlToken("order by", ctx));
				}

				this.sort.forEach(order -> {

					if (order.isIgnoreCase()) {
						tokens.add(new JpqlToken("lower(", ctx, NO_SPACE));
					}
					tokens.add(new JpqlToken(() -> this.alias + "." + order.getProperty(), ctx, SPACE));
					if (order.isIgnoreCase()) {
						NOSPACE(tokens);
						tokens.add(new JpqlToken(")", ctx, SPACE));
					}
					tokens.add(new JpqlToken(order.isDescending() ? "desc" : "asc", ctx, NO_SPACE));
					tokens.add(new JpqlToken(",", ctx));
				});
				CLIP(tokens);
			}
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitUpdate_statement(Jpql31Parser.Update_statementContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.update_clause()));

		if (ctx.where_clause() != null) {
			tokens.addAll(visit(ctx.where_clause()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitDelete_statement(Jpql31Parser.Delete_statementContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.delete_clause()));

		if (ctx.where_clause() != null) {
			tokens.addAll(visit(ctx.where_clause()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitFrom_clause(Jpql31Parser.From_clauseContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.FROM().getText(), ctx, true));

		ctx.identification_variable_declaration().forEach(identificationVariableDeclarationContext -> {
			tokens.addAll(visit(identificationVariableDeclarationContext));
		});

		return tokens;
	}

	@Override
	public List<JpqlToken> visitIdentification_variable_declaration(
			Jpql31Parser.Identification_variable_declarationContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.range_variable_declaration()));

		ctx.join().forEach(joinContext -> {
			tokens.addAll(visit(joinContext));
		});
		ctx.fetch_join().forEach(fetchJoinContext -> {
			tokens.addAll(visit(fetchJoinContext));
		});

		return tokens;
	}

	@Override
	public List<JpqlToken> visitRange_variable_declaration(Jpql31Parser.Range_variable_declarationContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.entity_name()));

		if (ctx.AS() != null) {
			tokens.add(new JpqlToken(ctx.AS().getText(), ctx));
		}

		Jpql31Parser.Identification_variableContext identificationVariable = ctx.identification_variable();
		List<JpqlToken> visitedIdentificationVariable = visit(identificationVariable);
		tokens.addAll(visitedIdentificationVariable);

		if (this.alias.equals("")) {
			this.alias = tokens.get(tokens.size() - 1).getToken();
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitJoin(Jpql31Parser.JoinContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.join_spec()));
		tokens.addAll(visit(ctx.join_association_path_expression()));
		if (ctx.AS() != null) {
			tokens.add(new JpqlToken(ctx.AS().getText(), ctx));
		}
		tokens.addAll(visit(ctx.identification_variable()));
		if (ctx.join_condition() != null) {
			tokens.addAll(visit(ctx.join_condition()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitFetch_join(Jpql31Parser.Fetch_joinContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.join_spec()));
		tokens.add(new JpqlToken(ctx.FETCH().getText(), ctx));
		tokens.addAll(visit(ctx.join_association_path_expression()));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitJoin_spec(Jpql31Parser.Join_specContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.LEFT() != null) {
			tokens.add(new JpqlToken(ctx.LEFT().getText(), ctx));
		}
		if (ctx.OUTER() != null) {
			tokens.add(new JpqlToken(ctx.OUTER().getText(), ctx));
		}
		if (ctx.INNER() != null) {
			tokens.add(new JpqlToken(ctx.INNER().getText(), ctx));
		}
		if (ctx.JOIN() != null) {
			tokens.add(new JpqlToken(ctx.JOIN().getText(), ctx));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitJoin_condition(Jpql31Parser.Join_conditionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.ON().getText(), ctx));
		tokens.addAll(visit(ctx.conditional_expression()));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitJoin_association_path_expression(
			Jpql31Parser.Join_association_path_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.TREAT() == null) {
			if (ctx.join_collection_valued_path_expression() != null) {
				tokens.addAll(visit(ctx.join_collection_valued_path_expression()));
			} else if (ctx.join_single_valued_path_expression() != null) {
				tokens.addAll(visit(ctx.join_single_valued_path_expression()));
			}
		} else {
			if (ctx.join_collection_valued_path_expression() != null) {

				tokens.add(new JpqlToken(ctx.TREAT().getText(), ctx));
				tokens.add(new JpqlToken("(", ctx));
				tokens.addAll(visit(ctx.join_collection_valued_path_expression()));
				tokens.add(new JpqlToken(ctx.AS().getText(), ctx));
				tokens.addAll(visit(ctx.subtype()));
				tokens.add(new JpqlToken(")", ctx));
			} else if (ctx.join_single_valued_path_expression() != null) {

				tokens.add(new JpqlToken(ctx.TREAT().getText(), ctx));
				tokens.add(new JpqlToken("(", ctx));
				tokens.addAll(visit(ctx.join_single_valued_path_expression()));
				tokens.add(new JpqlToken(ctx.AS().getText(), ctx));
				tokens.addAll(visit(ctx.subtype()));
				tokens.add(new JpqlToken(")", ctx));
			}
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitJoin_collection_valued_path_expression(
			Jpql31Parser.Join_collection_valued_path_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.identification_variable()));
		tokens.add(new JpqlToken(".", ctx));

		ctx.single_valued_embeddable_object_field().forEach(singleValuedEmbeddableObjectFieldContext -> {
			tokens.addAll(visit(singleValuedEmbeddableObjectFieldContext));
			tokens.add(new JpqlToken(".", singleValuedEmbeddableObjectFieldContext));
		});

		tokens.addAll(visit(ctx.collection_valued_field()));

		tokens.forEach(jpqlToken -> jpqlToken.setSpace(NO_SPACE));
		SPACE(tokens);

		return tokens;
	}

	@Override
	public List<JpqlToken> visitJoin_single_valued_path_expression(
			Jpql31Parser.Join_single_valued_path_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.identification_variable()));
		tokens.add(new JpqlToken(".", ctx));

		ctx.single_valued_embeddable_object_field().forEach(singleValuedEmbeddableObjectFieldContext -> {
			tokens.addAll(visit(singleValuedEmbeddableObjectFieldContext));
			tokens.add(new JpqlToken(".", singleValuedEmbeddableObjectFieldContext));
		});

		tokens.addAll(visit(ctx.single_valued_object_field()));

		tokens.forEach(jpqlToken -> jpqlToken.setSpace(NO_SPACE));
		SPACE(tokens);

		return tokens;
	}

	@Override
	public List<JpqlToken> visitCollection_member_declaration(Jpql31Parser.Collection_member_declarationContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.IN().getText(), ctx));
		tokens.add(new JpqlToken("(", ctx));
		tokens.addAll(visit(ctx.collection_valued_path_expression()));
		tokens.add(new JpqlToken(")", ctx));
		if (ctx.AS() != null) {
			tokens.add(new JpqlToken(ctx.AS().getText(), ctx));
		}
		tokens.addAll(visit(ctx.identification_variable()));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitQualified_identification_variable(
			Jpql31Parser.Qualified_identification_variableContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.map_field_identification_variable() != null) {
			tokens.addAll(visit(ctx.map_field_identification_variable()));
		} else if (ctx.identification_variable() != null) {

			tokens.add(new JpqlToken(ctx.ENTRY().getText(), ctx));
			tokens.add(new JpqlToken("(", ctx));
			tokens.addAll(visit(ctx.identification_variable()));
			tokens.add(new JpqlToken(")", ctx));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitMap_field_identification_variable(
			Jpql31Parser.Map_field_identification_variableContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.KEY() != null) {

			tokens.add(new JpqlToken(ctx.KEY().getText(), ctx));
			tokens.add(new JpqlToken("(", ctx));
			tokens.addAll(visit(ctx.identification_variable()));
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.VALUE() != null) {

			tokens.add(new JpqlToken(ctx.VALUE().getText(), ctx));
			tokens.add(new JpqlToken("(", ctx));
			tokens.addAll(visit(ctx.identification_variable()));
			tokens.add(new JpqlToken(")", ctx));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitSingle_valued_path_expression(Jpql31Parser.Single_valued_path_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.qualified_identification_variable() != null) {
			tokens.addAll(visit(ctx.qualified_identification_variable()));
		} else if (ctx.qualified_identification_variable() != null) {

			tokens.add(new JpqlToken(ctx.TREAT().getText(), ctx, NO_SPACE));
			tokens.add(new JpqlToken("(", ctx, NO_SPACE));
			tokens.addAll(visit(ctx.qualified_identification_variable()));
			tokens.add(new JpqlToken(ctx.AS().getText(), ctx));
			tokens.addAll(visit(ctx.subtype()));
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.state_field_path_expression() != null) {
			tokens.addAll(visit(ctx.state_field_path_expression()));
		} else if (ctx.single_valued_object_path_expression() != null) {
			tokens.addAll(visit(ctx.single_valued_object_path_expression()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitGeneral_identification_variable(Jpql31Parser.General_identification_variableContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.identification_variable() != null) {
			tokens.addAll(visit(ctx.identification_variable()));
		} else if (ctx.map_field_identification_variable() != null) {
			tokens.addAll(visit(ctx.map_field_identification_variable()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitGeneral_subpath(Jpql31Parser.General_subpathContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.simple_subpath() != null) {
			tokens.addAll(visit(ctx.simple_subpath()));
		} else if (ctx.treated_subpath() != null) {

			tokens.addAll(visit(ctx.treated_subpath()));
			ctx.single_valued_object_field().forEach(singleValuedObjectFieldContext -> {
				tokens.add(new JpqlToken(".", singleValuedObjectFieldContext, NO_SPACE));
				tokens.addAll(visit(singleValuedObjectFieldContext));
				NOSPACE(tokens);
			});
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitSimple_subpath(Jpql31Parser.Simple_subpathContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.general_identification_variable()));

		ctx.single_valued_object_field().forEach(singleValuedObjectFieldContext -> {
			tokens.add(new JpqlToken(".", singleValuedObjectFieldContext));
			tokens.addAll(visit(singleValuedObjectFieldContext));
		});

		tokens.forEach(jpqlToken -> jpqlToken.setSpace(NO_SPACE));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitTreated_subpath(Jpql31Parser.Treated_subpathContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.TREAT().getText(), ctx));
		tokens.add(new JpqlToken("(", ctx));
		tokens.addAll(visit(ctx.general_subpath()));
		tokens.add(new JpqlToken(ctx.AS().getText(), ctx));
		tokens.addAll(visit(ctx.subtype()));
		tokens.add(new JpqlToken(")", ctx));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitState_field_path_expression(Jpql31Parser.State_field_path_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.general_subpath()));
		tokens.add(new JpqlToken(".", ctx));
		tokens.addAll(visit(ctx.state_field()));

		tokens.forEach(jpqlToken -> jpqlToken.setSpace(NO_SPACE));
		SPACE(tokens);

		return tokens;
	}

	@Override
	public List<JpqlToken> visitState_valued_path_expression(Jpql31Parser.State_valued_path_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.state_field_path_expression() != null) {
			tokens.addAll(visit(ctx.state_field_path_expression()));
		} else if (ctx.general_identification_variable() != null) {
			tokens.addAll(visit(ctx.general_identification_variable()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitSingle_valued_object_path_expression(
			Jpql31Parser.Single_valued_object_path_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.general_subpath()));
		NOSPACE(tokens);
		tokens.add(new JpqlToken(".", ctx, NO_SPACE));
		tokens.addAll(visit(ctx.single_valued_object_field()));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitCollection_valued_path_expression(
			Jpql31Parser.Collection_valued_path_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.general_subpath()));
		NOSPACE(tokens);
		tokens.add(new JpqlToken(".", ctx));
		tokens.addAll(visit(ctx.collection_value_field()));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitUpdate_clause(Jpql31Parser.Update_clauseContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.UPDATE().getText(), ctx));
		tokens.addAll(visit(ctx.entity_name()));
		if (ctx.AS() != null) {
			tokens.add(new JpqlToken(ctx.AS().getText(), ctx));
		}
		if (ctx.identification_variable() != null) {
			tokens.addAll(visit(ctx.identification_variable()));
		}
		tokens.add(new JpqlToken(ctx.SET().getText(), ctx));
		ctx.update_item().forEach(updateItemContext -> {
			tokens.addAll(visit(updateItemContext));
			tokens.add(new JpqlToken(",", updateItemContext));
		});
		CLIP(tokens);

		return tokens;
	}

	@Override
	public List<JpqlToken> visitUpdate_item(Jpql31Parser.Update_itemContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.identification_variable() != null) {
			tokens.addAll(visit(ctx.identification_variable()));
			tokens.add(new JpqlToken(".", ctx));
		}

		ctx.single_valued_embeddable_object_field().forEach(singleValuedEmbeddableObjectFieldContext -> {
			tokens.addAll(visit(singleValuedEmbeddableObjectFieldContext));
			tokens.add(new JpqlToken(".", singleValuedEmbeddableObjectFieldContext));
		});

		if (ctx.state_field() != null) {
			tokens.addAll(visit(ctx.state_field()));
		} else if (ctx.single_valued_object_field() != null) {
			tokens.addAll(visit(ctx.single_valued_object_field()));
		}

		tokens.add(new JpqlToken("=", ctx));
		tokens.addAll(visit(ctx.new_value()));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitNew_value(Jpql31Parser.New_valueContext ctx) {

		if (ctx.scalar_expression() != null) {
			return visit(ctx.scalar_expression());
		} else if (ctx.simple_entity_expression() != null) {
			return visit(ctx.simple_entity_expression());
		} else if (ctx.NULL() != null) {
			return List.of(new JpqlToken(ctx.NULL().getText(), ctx));
		} else {
			return List.of();
		}
	}

	@Override
	public List<JpqlToken> visitDelete_clause(Jpql31Parser.Delete_clauseContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.DELETE().getText(), ctx));
		tokens.add(new JpqlToken(ctx.FROM().getText(), ctx));
		tokens.addAll(visit(ctx.entity_name()));
		if (ctx.AS() != null) {
			tokens.add(new JpqlToken(ctx.AS().getText(), ctx));
		}
		if (ctx.identification_variable() != null) {
			tokens.addAll(visit(ctx.identification_variable()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitSelect_clause(Jpql31Parser.Select_clauseContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.SELECT().getText(), ctx));

		if (this.countQuery) {
			tokens.add(new JpqlToken("count(", ctx, NO_SPACE));
		}

		if (ctx.DISTINCT() != null) {
			tokens.add(new JpqlToken(ctx.DISTINCT().getText(), ctx));
		}

		List<JpqlToken> selectItemTokens = new ArrayList<>();

		ctx.select_item().forEach(selectItemContext -> {
			selectItemTokens.addAll(visit(selectItemContext));
			NOSPACE(selectItemTokens);
			selectItemTokens.add(new JpqlToken(",", selectItemContext));
		});
		CLIP(selectItemTokens);
		SPACE(selectItemTokens);

		if (this.countQuery) {
			if (ctx.DISTINCT() != null) {
				if (selectItemTokens.stream().anyMatch(jpqlToken -> jpqlToken.getToken().contains("new"))) {
					// constructor
					tokens.add(new JpqlToken(() -> this.alias, ctx));
				} else {
					// keep all the select items to distinct against
					tokens.addAll(selectItemTokens);
				}
			} else {
				tokens.add(new JpqlToken(() -> this.alias, ctx));
			}
			NOSPACE(tokens);
			tokens.add(new JpqlToken(")", ctx));
		} else {
			tokens.addAll(selectItemTokens);
		}

		this.projection = selectItemTokens;

		return tokens;
	}

	@Override
	public List<JpqlToken> visitSelect_item(Jpql31Parser.Select_itemContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.select_expression()));
		SPACE(tokens);

		if (ctx.AS() != null) {
			tokens.add(new JpqlToken(ctx.AS().getText(), ctx));
		}

		if (ctx.result_variable() != null) {
			tokens.addAll(visit(ctx.result_variable()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitSelect_expression(Jpql31Parser.Select_expressionContext ctx) {

		if (ctx.single_valued_path_expression() != null) {
			return visit(ctx.single_valued_path_expression());
		} else if (ctx.scalar_expression() != null) {
			return visit(ctx.scalar_expression());
		} else if (ctx.aggregate_expression() != null) {
			return visit(ctx.aggregate_expression());
		} else if (ctx.identification_variable() != null) {

			if (ctx.OBJECT() == null) {
				return visit(ctx.identification_variable());
			} else {

				List<JpqlToken> tokens = new ArrayList<>();

				tokens.add(new JpqlToken(ctx.OBJECT().getText(), ctx));
				tokens.add(new JpqlToken("(", ctx));
				tokens.addAll(visit(ctx.identification_variable()));
				tokens.add(new JpqlToken(")", ctx));

				return tokens;
			}
		} else if (ctx.constructor_expression() != null) {
			return visit(ctx.constructor_expression());
		} else {
			return List.of();
		}
	}

	@Override
	public List<JpqlToken> visitConstructor_expression(Jpql31Parser.Constructor_expressionContext ctx) {

		this.hasConstructorExpression = true;

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.NEW().getText(), ctx));
		tokens.addAll(visit(ctx.constructor_name()));
		tokens.add(new JpqlToken("(", ctx, NO_SPACE));

		ctx.constructor_item().forEach(constructorItemContext -> {
			tokens.addAll(visit(constructorItemContext));
			NOSPACE(tokens);
			tokens.add(new JpqlToken(",", constructorItemContext));
		});
		CLIP(tokens);

		tokens.add(new JpqlToken(")", ctx));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitConstructor_item(Jpql31Parser.Constructor_itemContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.single_valued_path_expression() != null) {
			tokens.addAll(visit(ctx.single_valued_path_expression()));
		} else if (ctx.scalar_expression() != null) {
			tokens.addAll(visit(ctx.scalar_expression()));
		} else if (ctx.aggregate_expression() != null) {
			tokens.addAll(visit(ctx.aggregate_expression()));
		} else if (ctx.identification_variable() != null) {
			tokens.addAll(visit(ctx.identification_variable()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitAggregate_expression(Jpql31Parser.Aggregate_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.AVG() != null || ctx.MAX() != null || ctx.MIN() != null || ctx.SUM() != null) {

			if (ctx.AVG() != null) {
				tokens.add(new JpqlToken(ctx.AVG().getText(), ctx, NO_SPACE));
			}
			if (ctx.MAX() != null) {
				tokens.add(new JpqlToken(ctx.MAX().getText(), ctx, NO_SPACE));
			}
			if (ctx.MIN() != null) {
				tokens.add(new JpqlToken(ctx.MIN().getText(), ctx, NO_SPACE));
			}
			if (ctx.SUM() != null) {
				tokens.add(new JpqlToken(ctx.SUM().getText(), ctx, NO_SPACE));
			}
			tokens.add(new JpqlToken("(", ctx, NO_SPACE));
			if (ctx.DISTINCT() != null) {
				tokens.add(new JpqlToken(ctx.DISTINCT().getText(), ctx));
			}
			tokens.addAll(visit(ctx.state_valued_path_expression()));
			tokens.add(new JpqlToken(")", ctx, NO_SPACE));
		} else if (ctx.COUNT() != null) {

			tokens.add(new JpqlToken(ctx.COUNT().getText(), ctx, NO_SPACE));
			tokens.add(new JpqlToken("(", ctx, NO_SPACE));
			if (ctx.DISTINCT() != null) {
				tokens.add(new JpqlToken(ctx.DISTINCT().getText(), ctx));
			}
			if (ctx.identification_variable() != null) {
				tokens.addAll(visit(ctx.identification_variable()));
			} else if (ctx.state_valued_path_expression() != null) {
				tokens.addAll(visit(ctx.state_valued_path_expression()));
			} else if (ctx.single_valued_object_path_expression() != null) {
				tokens.addAll(visit(ctx.single_valued_object_path_expression()));
			}
			NOSPACE(tokens);
			tokens.add(new JpqlToken(")", ctx, NO_SPACE));
		} else if (ctx.function_invocation() != null) {
			tokens.addAll(visit(ctx.function_invocation()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitWhere_clause(Jpql31Parser.Where_clauseContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.WHERE().getText(), ctx, true));
		tokens.addAll(visit(ctx.conditional_expression()));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitGroupby_clause(Jpql31Parser.Groupby_clauseContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.GROUP().getText(), ctx));
		tokens.add(new JpqlToken(ctx.BY().getText(), ctx));
		ctx.groupby_item().forEach(groupbyItemContext -> {
			tokens.addAll(visit(groupbyItemContext));
			NOSPACE(tokens);
			tokens.add(new JpqlToken(",", groupbyItemContext));
		});
		CLIP(tokens);
		SPACE(tokens);

		return tokens;
	}

	@Override
	public List<JpqlToken> visitGroupby_item(Jpql31Parser.Groupby_itemContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.single_valued_path_expression() != null) {
			tokens.addAll(visit(ctx.single_valued_path_expression()));
		} else if (ctx.identification_variable() != null) {
			tokens.addAll(visit(ctx.identification_variable()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitHaving_clause(Jpql31Parser.Having_clauseContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.HAVING().getText(), ctx));
		tokens.addAll(visit(ctx.conditional_expression()));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitOrderby_clause(Jpql31Parser.Orderby_clauseContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.ORDER().getText(), ctx));
		tokens.add(new JpqlToken(ctx.BY().getText(), ctx));

		ctx.orderby_item().forEach(orderbyItemContext -> {
			tokens.addAll(visit(orderbyItemContext));
			NOSPACE(tokens);
			tokens.add(new JpqlToken(",", orderbyItemContext));
		});
		CLIP(tokens);

		return tokens;
	}

	@Override
	public List<JpqlToken> visitOrderby_item(Jpql31Parser.Orderby_itemContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.state_field_path_expression() != null) {
			tokens.addAll(visit(ctx.state_field_path_expression()));
		} else if (ctx.general_identification_variable() != null) {
			tokens.addAll(visit(ctx.general_identification_variable()));
		} else if (ctx.result_variable() != null) {
			tokens.addAll(visit(ctx.result_variable()));
		}

		if (ctx.ASC() != null) {
			tokens.add(new JpqlToken(ctx.ASC().getText(), ctx));
		}
		if (ctx.DESC() != null) {
			tokens.add(new JpqlToken(ctx.DESC().getText(), ctx));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitSubquery(Jpql31Parser.SubqueryContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.simple_select_clause()));
		tokens.addAll(visit(ctx.subquery_from_clause()));
		if (ctx.where_clause() != null) {
			tokens.addAll(visit(ctx.where_clause()));
		}
		if (ctx.groupby_clause() != null) {
			tokens.addAll(visit(ctx.groupby_clause()));
		}
		if (ctx.having_clause() != null) {
			tokens.addAll(visit(ctx.having_clause()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitSubquery_from_clause(Jpql31Parser.Subquery_from_clauseContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.FROM().getText(), ctx));
		ctx.subselect_identification_variable_declaration().forEach(subselectIdentificationVariableDeclarationContext -> {
			tokens.addAll(visit(subselectIdentificationVariableDeclarationContext));
			NOSPACE(tokens);
			tokens.add(new JpqlToken(",", subselectIdentificationVariableDeclarationContext));
		});
		CLIP(tokens);

		return tokens;
	}

	@Override
	public List<JpqlToken> visitSubselect_identification_variable_declaration(
			Jpql31Parser.Subselect_identification_variable_declarationContext ctx) {
		return super.visitSubselect_identification_variable_declaration(ctx);
	}

	@Override
	public List<JpqlToken> visitDerived_path_expression(Jpql31Parser.Derived_path_expressionContext ctx) {
		return super.visitDerived_path_expression(ctx);
	}

	@Override
	public List<JpqlToken> visitGeneral_derived_path(Jpql31Parser.General_derived_pathContext ctx) {
		return super.visitGeneral_derived_path(ctx);
	}

	@Override
	public List<JpqlToken> visitSimple_derived_path(Jpql31Parser.Simple_derived_pathContext ctx) {
		return super.visitSimple_derived_path(ctx);
	}

	@Override
	public List<JpqlToken> visitTreated_derived_path(Jpql31Parser.Treated_derived_pathContext ctx) {
		return super.visitTreated_derived_path(ctx);
	}

	@Override
	public List<JpqlToken> visitDerived_collection_member_declaration(
			Jpql31Parser.Derived_collection_member_declarationContext ctx) {
		return super.visitDerived_collection_member_declaration(ctx);
	}

	@Override
	public List<JpqlToken> visitSimple_select_clause(Jpql31Parser.Simple_select_clauseContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.SELECT().getText(), ctx));
		if (ctx.DISTINCT() != null) {
			tokens.add(new JpqlToken(ctx.DISTINCT().getText(), ctx));
		}
		tokens.addAll(visit(ctx.simple_select_expression()));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitSimple_select_expression(Jpql31Parser.Simple_select_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.single_valued_path_expression() != null) {
			tokens.addAll(visit(ctx.single_valued_path_expression()));
		} else if (ctx.scalar_expression() != null) {
			tokens.addAll(visit(ctx.scalar_expression()));
		} else if (ctx.aggregate_expression() != null) {
			tokens.addAll(visit(ctx.aggregate_expression()));
		} else if (ctx.identification_variable() != null) {
			tokens.addAll(visit(ctx.identification_variable()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitScalar_expression(Jpql31Parser.Scalar_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.arithmetic_expression() != null) {
			tokens.addAll(visit(ctx.arithmetic_expression()));
		} else if (ctx.string_expression() != null) {
			tokens.addAll(visit(ctx.string_expression()));
		} else if (ctx.enum_expression() != null) {
			tokens.addAll(visit(ctx.enum_expression()));
		} else if (ctx.datetime_expression() != null) {
			tokens.addAll(visit(ctx.datetime_expression()));
		} else if (ctx.boolean_expression() != null) {
			tokens.addAll(visit(ctx.boolean_expression()));
		} else if (ctx.case_expression() != null) {
			tokens.addAll(visit(ctx.case_expression()));
		} else if (ctx.entity_type_expression() != null) {
			tokens.addAll(visit(ctx.entity_type_expression()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitConditional_expression(Jpql31Parser.Conditional_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.conditional_expression() != null) {
			tokens.addAll(visit(ctx.conditional_expression()));
			tokens.add(new JpqlToken(ctx.OR().getText(), ctx));
			tokens.addAll(visit(ctx.conditional_term()));
		} else {
			tokens.addAll(visit(ctx.conditional_term()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitConditional_term(Jpql31Parser.Conditional_termContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.conditional_term() != null) {
			tokens.addAll(visit(ctx.conditional_term()));
			tokens.add(new JpqlToken(ctx.AND().getText(), ctx));
			tokens.addAll(visit(ctx.conditional_factor()));
		} else {
			tokens.addAll(visit(ctx.conditional_factor()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitConditional_factor(Jpql31Parser.Conditional_factorContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.NOT() != null) {
			tokens.add(new JpqlToken(ctx.NOT().getText(), ctx));
		}

		Jpql31Parser.Conditional_primaryContext conditionalPrimary = ctx.conditional_primary();
		List<JpqlToken> visitedConditionalPrimary = visit(conditionalPrimary);
		tokens.addAll(visitedConditionalPrimary);

		return tokens;
	}

	@Override
	public List<JpqlToken> visitConditional_primary(Jpql31Parser.Conditional_primaryContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.simple_cond_expression() != null) {
			tokens.addAll(visit(ctx.simple_cond_expression()));
		} else if (ctx.conditional_expression() != null) {

			tokens.add(new JpqlToken("(", ctx, NO_SPACE));
			tokens.addAll(visit(ctx.conditional_expression()));
			NOSPACE(tokens);
			tokens.add(new JpqlToken(")", ctx));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitSimple_cond_expression(Jpql31Parser.Simple_cond_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.comparison_expression() != null) {
			tokens.addAll(visit(ctx.comparison_expression()));
		} else if (ctx.between_expression() != null) {
			tokens.addAll(visit(ctx.between_expression()));
		} else if (ctx.in_expression() != null) {
			tokens.addAll(visit(ctx.in_expression()));
		} else if (ctx.like_expression() != null) {
			tokens.addAll(visit(ctx.like_expression()));
		} else if (ctx.null_comparison_expression() != null) {
			tokens.addAll(visit(ctx.null_comparison_expression()));
		} else if (ctx.empty_collection_comparison_expression() != null) {
			tokens.addAll(visit(ctx.empty_collection_comparison_expression()));
		} else if (ctx.collection_member_expression() != null) {
			tokens.addAll(visit(ctx.collection_member_expression()));
		} else if (ctx.exists_expression() != null) {
			tokens.addAll(visit(ctx.exists_expression()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitBetween_expression(Jpql31Parser.Between_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.arithmetic_expression(0) != null) {

			tokens.addAll(visit(ctx.arithmetic_expression(0)));
			if (ctx.NOT() != null) {
				tokens.add(new JpqlToken(ctx.NOT().getText(), ctx));
				tokens.add(new JpqlToken(ctx.BETWEEN().getText(), ctx));
				tokens.addAll(visit(ctx.arithmetic_expression(1)));
				tokens.add(new JpqlToken(ctx.AND().getText(), ctx));
				tokens.addAll(visit(ctx.arithmetic_expression(2)));
			}
		} else if (ctx.string_expression(0) != null) {

			tokens.addAll(visit(ctx.string_expression(0)));
			if (ctx.NOT() != null) {
				tokens.add(new JpqlToken(ctx.NOT().getText(), ctx));
				tokens.add(new JpqlToken(ctx.BETWEEN().getText(), ctx));
				tokens.addAll(visit(ctx.string_expression(1)));
				tokens.add(new JpqlToken(ctx.AND().getText(), ctx));
				tokens.addAll(visit(ctx.string_expression(2)));
			}
		} else if (ctx.datetime_expression(0) != null) {

			tokens.addAll(visit(ctx.datetime_expression(0)));
			if (ctx.NOT() != null) {
				tokens.add(new JpqlToken(ctx.NOT().getText(), ctx));
				tokens.add(new JpqlToken(ctx.BETWEEN().getText(), ctx));
				tokens.addAll(visit(ctx.datetime_expression(1)));
				tokens.add(new JpqlToken(ctx.AND().getText(), ctx));
				tokens.addAll(visit(ctx.datetime_expression(2)));
			}
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitIn_expression(Jpql31Parser.In_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.state_valued_path_expression() != null) {
			tokens.addAll(visit(ctx.state_valued_path_expression()));
		}
		if (ctx.type_discriminator() != null) {
			tokens.addAll(visit(ctx.type_discriminator()));
		}
		if (ctx.NOT() != null) {
			tokens.add(new JpqlToken(ctx.NOT().getText(), ctx));
		}
		if (ctx.IN() != null) {
			tokens.add(new JpqlToken(ctx.IN().getText(), ctx));
		}

		if (ctx.in_item() != null && !ctx.in_item().isEmpty()) {

			tokens.add(new JpqlToken("(", ctx, NO_SPACE));

			ctx.in_item().forEach(inItemContext -> {

				tokens.addAll(visit(inItemContext));
				NOSPACE(tokens);
				tokens.add(new JpqlToken(",", inItemContext));
			});
			CLIP(tokens);

			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.subquery() != null) {

			tokens.add(new JpqlToken("(", ctx, NO_SPACE));
			tokens.addAll(visit(ctx.subquery()));
			NOSPACE(tokens);
			tokens.add(new JpqlToken(")", ctx, NO_SPACE));
		} else if (ctx.collection_valued_input_parameter() != null) {
			tokens.addAll(visit(ctx.collection_valued_input_parameter()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitIn_item(Jpql31Parser.In_itemContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.literal() != null) {
			tokens.addAll(visit(ctx.literal()));
		} else if (ctx.single_valued_input_parameter() != null) {
			tokens.addAll(visit(ctx.single_valued_input_parameter()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitLike_expression(Jpql31Parser.Like_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.string_expression()));
		if (ctx.NOT() != null) {
			tokens.add(new JpqlToken(ctx.NOT().getText(), ctx));
		}
		tokens.add(new JpqlToken(ctx.LIKE().getText(), ctx));
		tokens.add(new JpqlToken(ctx.pattern_value().getText(), ctx));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitNull_comparison_expression(Jpql31Parser.Null_comparison_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.single_valued_path_expression() != null) {
			tokens.addAll(visit(ctx.single_valued_path_expression()));
		} else if (ctx.input_parameter() != null) {
			tokens.addAll(visit(ctx.input_parameter()));
		}

		tokens.add(new JpqlToken(ctx.IS().getText(), ctx));
		if (ctx.NOT() != null) {
			tokens.add(new JpqlToken(ctx.NOT().getText(), ctx));
		}
		tokens.add(new JpqlToken(ctx.NULL().getText(), ctx));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitEmpty_collection_comparison_expression(
			Jpql31Parser.Empty_collection_comparison_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.collection_valued_path_expression()));
		tokens.add(new JpqlToken(ctx.IS().getText(), ctx));
		if (ctx.NOT() != null) {
			tokens.add(new JpqlToken(ctx.NOT().getText(), ctx));
		}
		tokens.add(new JpqlToken(ctx.EMPTY().getText(), ctx));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitCollection_member_expression(Jpql31Parser.Collection_member_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.entity_or_value_expression()));
		if (ctx.NOT() != null) {
			tokens.add(new JpqlToken(ctx.NOT().getText(), ctx));
		}
		tokens.add(new JpqlToken(ctx.MEMBER().getText(), ctx));
		if (ctx.OF() != null) {
			tokens.add(new JpqlToken(ctx.OF().getText(), ctx));
		}
		tokens.addAll(visit(ctx.collection_valued_path_expression()));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitEntity_or_value_expression(Jpql31Parser.Entity_or_value_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.single_valued_object_path_expression() != null) {
			tokens.addAll(visit(ctx.single_valued_object_path_expression()));
		} else if (ctx.state_field_path_expression() != null) {
			tokens.addAll(visit(ctx.state_field_path_expression()));
		} else if (ctx.simple_entity_or_value_expression() != null) {
			tokens.addAll(visit(ctx.simple_entity_or_value_expression()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitSimple_entity_or_value_expression(
			Jpql31Parser.Simple_entity_or_value_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.identification_variable() != null) {
			tokens.addAll(visit(ctx.identification_variable()));
		} else if (ctx.input_parameter() != null) {
			tokens.addAll(visit(ctx.input_parameter()));
		} else if (ctx.literal() != null) {
			tokens.addAll(visit(ctx.literal()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitExists_expression(Jpql31Parser.Exists_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.NOT() != null) {
			tokens.add(new JpqlToken(ctx.NOT().getText(), ctx));
		}
		tokens.add(new JpqlToken(ctx.EXISTS().getText(), ctx));
		tokens.add(new JpqlToken("(", ctx, NO_SPACE));
		tokens.addAll(visit(ctx.subquery()));
		NOSPACE(tokens);
		tokens.add(new JpqlToken(")", ctx));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitAll_or_any_expression(Jpql31Parser.All_or_any_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.ALL() != null) {
			tokens.add(new JpqlToken(ctx.ALL().getText(), ctx));
		} else if (ctx.ANY() != null) {
			tokens.add(new JpqlToken(ctx.ANY().getText(), ctx));
		} else if (ctx.SOME() != null) {
			tokens.add(new JpqlToken(ctx.SOME().getText(), ctx));
		}
		tokens.add(new JpqlToken("(", ctx));
		tokens.addAll(visit(ctx.subquery()));
		tokens.add(new JpqlToken(")", ctx));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitComparison_expression(Jpql31Parser.Comparison_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (!ctx.string_expression().isEmpty()) {

			tokens.addAll(visit(ctx.string_expression(0)));
			tokens.addAll(visit(ctx.comparison_operator()));

			if (ctx.string_expression(1) != null) {
				tokens.addAll(visit(ctx.string_expression(1)));
			} else {
				tokens.addAll(visit(ctx.all_or_any_expression()));
			}
		} else if (!ctx.boolean_expression().isEmpty()) {

			tokens.addAll(visit(ctx.boolean_expression(0)));
			tokens.add(new JpqlToken(ctx.op.getText(), ctx));

			if (ctx.boolean_expression(1) != null) {
				tokens.addAll(visit(ctx.boolean_expression(1)));
			} else {
				tokens.addAll(visit(ctx.all_or_any_expression()));
			}
		} else if (!ctx.enum_expression().isEmpty()) {

			tokens.addAll(visit(ctx.enum_expression(0)));
			tokens.add(new JpqlToken(ctx.op.getText(), ctx));

			if (ctx.enum_expression(1) != null) {
				tokens.addAll(visit(ctx.enum_expression(1)));
			} else {
				tokens.addAll(visit(ctx.all_or_any_expression()));
			}
		} else if (!ctx.datetime_expression().isEmpty()) {

			tokens.addAll(visit(ctx.datetime_expression(0)));
			tokens.addAll(visit(ctx.comparison_operator()));

			if (ctx.datetime_expression(1) != null) {
				tokens.addAll(visit(ctx.datetime_expression(1)));
			} else {
				tokens.addAll(visit(ctx.all_or_any_expression()));
			}
		} else if (!ctx.entity_expression().isEmpty()) {

			tokens.addAll(visit(ctx.entity_expression(0)));
			tokens.add(new JpqlToken(ctx.op.getText(), ctx));

			if (ctx.entity_expression(1) != null) {
				tokens.addAll(visit(ctx.entity_expression(1)));
			} else {
				tokens.addAll(visit(ctx.all_or_any_expression()));
			}
		} else if (!ctx.arithmetic_expression().isEmpty()) {

			tokens.addAll(visit(ctx.arithmetic_expression(0)));
			tokens.addAll(visit(ctx.comparison_operator()));

			if (ctx.arithmetic_expression(1) != null) {
				tokens.addAll(visit(ctx.arithmetic_expression(1)));
			} else {
				tokens.addAll(visit(ctx.all_or_any_expression()));
			}
		} else if (!ctx.entity_type_expression().isEmpty()) {

			tokens.addAll(visit(ctx.entity_type_expression(0)));
			tokens.add(new JpqlToken(ctx.op.getText(), ctx));
			tokens.addAll(visit(ctx.entity_type_expression(1)));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitComparison_operator(Jpql31Parser.Comparison_operatorContext ctx) {
		return List.of(new JpqlToken(ctx.op.getText(), ctx));
	}

	@Override
	public List<JpqlToken> visitArithmetic_expression(Jpql31Parser.Arithmetic_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.arithmetic_expression() != null) {

			tokens.addAll(visit(ctx.arithmetic_expression()));
			tokens.add(new JpqlToken(ctx.op.getText(), ctx));
			tokens.addAll(visit(ctx.arithmetic_term()));

		} else {
			tokens.addAll(visit(ctx.arithmetic_term()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitArithmetic_term(Jpql31Parser.Arithmetic_termContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.arithmetic_term() != null) {

			tokens.addAll(visit(ctx.arithmetic_term()));
			tokens.add(new JpqlToken(ctx.op.getText(), ctx));
			tokens.addAll(visit(ctx.arithmetic_factor()));
		} else {
			tokens.addAll(visit(ctx.arithmetic_factor()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitArithmetic_factor(Jpql31Parser.Arithmetic_factorContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.op != null) {
			tokens.add(new JpqlToken(ctx.op.getText(), ctx));
		}
		tokens.addAll(visit(ctx.arithmetic_primary()));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitArithmetic_primary(Jpql31Parser.Arithmetic_primaryContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.state_valued_path_expression() != null) {
			tokens.addAll(visit(ctx.state_valued_path_expression()));
		} else if (ctx.numeric_literal() != null) {
			tokens.addAll(visit(ctx.numeric_literal()));
		} else if (ctx.arithmetic_expression() != null) {

			tokens.add(new JpqlToken("(", ctx, NO_SPACE));
			tokens.addAll(visit(ctx.arithmetic_expression()));
			NOSPACE(tokens);
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.input_parameter() != null) {
			tokens.addAll(visit(ctx.input_parameter()));
		} else if (ctx.functions_returning_numerics() != null) {
			tokens.addAll(visit(ctx.functions_returning_numerics()));
		} else if (ctx.aggregate_expression() != null) {
			tokens.addAll(visit(ctx.aggregate_expression()));
		} else if (ctx.case_expression() != null) {
			tokens.addAll(visit(ctx.case_expression()));
		} else if (ctx.function_invocation() != null) {
			tokens.addAll(visit(ctx.function_invocation()));
		} else if (ctx.subquery() != null) {

			tokens.add(new JpqlToken("(", ctx, NO_SPACE));
			tokens.addAll(visit(ctx.subquery()));
			NOSPACE(tokens);
			tokens.add(new JpqlToken(")", ctx));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitString_expression(Jpql31Parser.String_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.state_valued_path_expression() != null) {
			tokens.addAll(visit(ctx.state_valued_path_expression()));
		} else if (ctx.string_literal() != null) {
			tokens.addAll(visit(ctx.string_literal()));
		} else if (ctx.input_parameter() != null) {
			tokens.addAll(visit(ctx.input_parameter()));
		} else if (ctx.functions_returning_strings() != null) {
			tokens.addAll(visit(ctx.functions_returning_strings()));
		} else if (ctx.aggregate_expression() != null) {
			tokens.addAll(visit(ctx.aggregate_expression()));
		} else if (ctx.case_expression() != null) {
			tokens.addAll(visit(ctx.case_expression()));
		} else if (ctx.function_invocation() != null) {
			tokens.addAll(visit(ctx.function_invocation()));
		} else if (ctx.subquery() != null) {

			tokens.add(new JpqlToken("(", ctx, NO_SPACE));
			tokens.addAll(visit(ctx.subquery()));
			NOSPACE(tokens);
			tokens.add(new JpqlToken(")", ctx));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitDatetime_expression(Jpql31Parser.Datetime_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.state_valued_path_expression() != null) {
			tokens.addAll(visit(ctx.state_valued_path_expression()));
		} else if (ctx.input_parameter() != null) {
			tokens.addAll(visit(ctx.input_parameter()));
		} else if (ctx.input_parameter() != null) {
			tokens.addAll(visit(ctx.input_parameter()));
		} else if (ctx.functions_returning_datetime() != null) {
			tokens.addAll(visit(ctx.functions_returning_datetime()));
		} else if (ctx.aggregate_expression() != null) {
			tokens.addAll(visit(ctx.aggregate_expression()));
		} else if (ctx.case_expression() != null) {
			tokens.addAll(visit(ctx.case_expression()));
		} else if (ctx.function_invocation() != null) {
			tokens.addAll(visit(ctx.function_invocation()));
		} else if (ctx.date_time_timestamp_literal() != null) {
			tokens.addAll(visit(ctx.date_time_timestamp_literal()));
		} else if (ctx.subquery() != null) {

			tokens.add(new JpqlToken("(", ctx, NO_SPACE));
			tokens.addAll(visit(ctx.subquery()));
			NOSPACE(tokens);
			tokens.add(new JpqlToken(")", ctx));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitBoolean_expression(Jpql31Parser.Boolean_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.state_valued_path_expression() != null) {
			tokens.addAll(visit(ctx.state_valued_path_expression()));
		} else if (ctx.boolean_literal() != null) {
			tokens.addAll(visit(ctx.boolean_literal()));
		} else if (ctx.input_parameter() != null) {
			tokens.addAll(visit(ctx.input_parameter()));
		} else if (ctx.case_expression() != null) {
			tokens.addAll(visit(ctx.case_expression()));
		} else if (ctx.function_invocation() != null) {
			tokens.addAll(visit(ctx.function_invocation()));
		} else if (ctx.subquery() != null) {

			tokens.add(new JpqlToken("(", ctx, NO_SPACE));
			tokens.addAll(visit(ctx.subquery()));
			NOSPACE(tokens);
			tokens.add(new JpqlToken(")", ctx));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitEnum_expression(Jpql31Parser.Enum_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.state_valued_path_expression() != null) {
			tokens.addAll(visit(ctx.state_valued_path_expression()));
		} else if (ctx.enum_literal() != null) {
			tokens.addAll(visit(ctx.enum_literal()));
		} else if (ctx.input_parameter() != null) {
			tokens.addAll(visit(ctx.input_parameter()));
		} else if (ctx.case_expression() != null) {
			tokens.addAll(visit(ctx.case_expression()));
		} else if (ctx.subquery() != null) {

			tokens.add(new JpqlToken("(", ctx, NO_SPACE));
			tokens.addAll(visit(ctx.subquery()));
			NOSPACE(tokens);
			tokens.add(new JpqlToken(")", ctx));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitEntity_expression(Jpql31Parser.Entity_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.single_valued_object_path_expression() != null) {
			tokens.addAll(visit(ctx.single_valued_object_path_expression()));
		} else if (ctx.simple_entity_expression() != null) {
			tokens.addAll(visit(ctx.simple_entity_expression()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitSimple_entity_expression(Jpql31Parser.Simple_entity_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.identification_variable() != null) {
			tokens.addAll(visit(ctx.identification_variable()));
		} else if (ctx.input_parameter() != null) {
			tokens.addAll(visit(ctx.input_parameter()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitEntity_type_expression(Jpql31Parser.Entity_type_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.type_discriminator() != null) {
			tokens.addAll(visit(ctx.type_discriminator()));
		} else if (ctx.entity_type_literal() != null) {
			tokens.addAll(visit(ctx.entity_type_literal()));
		} else if (ctx.input_parameter() != null) {
			tokens.addAll(visit(ctx.input_parameter()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitType_discriminator(Jpql31Parser.Type_discriminatorContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.TYPE().getText(), ctx));
		tokens.add(new JpqlToken("(", ctx));
		if (ctx.general_identification_variable() != null) {
			tokens.addAll(visit(ctx.general_identification_variable()));
		} else if (ctx.single_valued_object_path_expression() != null) {
			tokens.addAll(visit(ctx.single_valued_object_path_expression()));
		} else if (ctx.input_parameter() != null) {
			tokens.addAll(visit(ctx.input_parameter()));
		}
		tokens.add(new JpqlToken(")", ctx));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitFunctions_returning_numerics(Jpql31Parser.Functions_returning_numericsContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.LENGTH() != null) {

			tokens.add(new JpqlToken(ctx.LENGTH().getText(), ctx));
			tokens.add(new JpqlToken("(", ctx));
			tokens.addAll(visit(ctx.string_expression(0)));
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.LOCATE() != null) {

			tokens.add(new JpqlToken(ctx.LOCATE().getText(), ctx));
			tokens.add(new JpqlToken("(", ctx));
			tokens.addAll(visit(ctx.string_expression(0)));
			tokens.add(new JpqlToken(",", ctx));
			tokens.addAll(visit(ctx.string_expression(1)));
			if (ctx.arithmetic_expression() != null) {
				tokens.add(new JpqlToken(",", ctx));
				tokens.addAll(visit(ctx.arithmetic_expression(0)));
			}
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.ABS() != null) {

			tokens.add(new JpqlToken(ctx.ABS().getText(), ctx));
			tokens.add(new JpqlToken("(", ctx));
			tokens.addAll(visit(ctx.arithmetic_expression(0)));
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.CEILING() != null) {

			tokens.add(new JpqlToken(ctx.CEILING().getText(), ctx));
			tokens.add(new JpqlToken("(", ctx));
			tokens.addAll(visit(ctx.arithmetic_expression(0)));
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.EXP() != null) {

			tokens.add(new JpqlToken(ctx.EXP().getText(), ctx));
			tokens.add(new JpqlToken("(", ctx));
			tokens.addAll(visit(ctx.arithmetic_expression(0)));
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.FLOOR() != null) {

			tokens.add(new JpqlToken(ctx.FLOOR().getText(), ctx));
			tokens.add(new JpqlToken("(", ctx));
			tokens.addAll(visit(ctx.arithmetic_expression(0)));
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.LN() != null) {

			tokens.add(new JpqlToken(ctx.LN().getText(), ctx));
			tokens.add(new JpqlToken("(", ctx));
			tokens.addAll(visit(ctx.arithmetic_expression(0)));
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.SIGN() != null) {

			tokens.add(new JpqlToken(ctx.SIGN().getText(), ctx));
			tokens.add(new JpqlToken("(", ctx));
			tokens.addAll(visit(ctx.arithmetic_expression(0)));
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.SQRT() != null) {

			tokens.add(new JpqlToken(ctx.SQRT().getText(), ctx));
			tokens.add(new JpqlToken("(", ctx));
			tokens.addAll(visit(ctx.arithmetic_expression(0)));
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.MOD() != null) {

			tokens.add(new JpqlToken(ctx.MOD().getText(), ctx));
			tokens.add(new JpqlToken("(", ctx));
			tokens.addAll(visit(ctx.arithmetic_expression(0)));
			tokens.add(new JpqlToken(",", ctx));
			tokens.addAll(visit(ctx.arithmetic_expression(1)));
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.POWER() != null) {

			tokens.add(new JpqlToken(ctx.POWER().getText(), ctx));
			tokens.add(new JpqlToken("(", ctx));
			tokens.addAll(visit(ctx.arithmetic_expression(0)));
			tokens.add(new JpqlToken(",", ctx));
			tokens.addAll(visit(ctx.arithmetic_expression(1)));
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.ROUND() != null) {

			tokens.add(new JpqlToken(ctx.ROUND().getText(), ctx));
			tokens.add(new JpqlToken("(", ctx));
			tokens.addAll(visit(ctx.arithmetic_expression(0)));
			tokens.add(new JpqlToken(",", ctx));
			tokens.addAll(visit(ctx.arithmetic_expression(1)));
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.SIZE() != null) {

			tokens.add(new JpqlToken(ctx.SIZE().getText(), ctx));
			tokens.add(new JpqlToken("(", ctx));
			tokens.addAll(visit(ctx.collection_valued_path_expression()));
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.INDEX() != null) {

			tokens.add(new JpqlToken(ctx.INDEX().getText(), ctx));
			tokens.add(new JpqlToken("(", ctx));
			tokens.addAll(visit(ctx.identification_variable()));
			tokens.add(new JpqlToken(")", ctx));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitFunctions_returning_datetime(Jpql31Parser.Functions_returning_datetimeContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.CURRENT_DATE() != null) {
			tokens.add(new JpqlToken(ctx.CURRENT_DATE().getText(), ctx));
		} else if (ctx.CURRENT_TIME() != null) {
			tokens.add(new JpqlToken(ctx.CURRENT_TIME().getText(), ctx));
		} else if (ctx.CURRENT_TIMESTAMP() != null) {
			tokens.add(new JpqlToken(ctx.CURRENT_TIMESTAMP().getText(), ctx));
		} else if (ctx.LOCAL() != null) {

			tokens.add(new JpqlToken(ctx.LOCAL().getText(), ctx));

			if (ctx.DATE() != null) {
				tokens.add(new JpqlToken(ctx.DATE().getText(), ctx));
			} else if (ctx.TIME() != null) {
				tokens.add(new JpqlToken(ctx.TIME().getText(), ctx));
			} else if (ctx.DATETIME() != null) {
				tokens.add(new JpqlToken(ctx.DATETIME().getText(), ctx));
			}
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitFunctions_returning_strings(Jpql31Parser.Functions_returning_stringsContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.CONCAT() != null) {

			tokens.add(new JpqlToken(ctx.CONCAT().getText(), ctx, NO_SPACE));
			tokens.add(new JpqlToken("(", ctx, NO_SPACE));
			ctx.string_expression().forEach(stringExpressionContext -> {
				tokens.addAll(visit(stringExpressionContext));
				NOSPACE(tokens);
				tokens.add(new JpqlToken(",", ctx));
			});
			CLIP(tokens);
			NOSPACE(tokens);
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.SUBSTRING() != null) {

			tokens.add(new JpqlToken(ctx.SUBSTRING().getText(), ctx, NO_SPACE));
			tokens.add(new JpqlToken("(", ctx, NO_SPACE));
			tokens.addAll(visit(ctx.string_expression(0)));
			NOSPACE(tokens);
			ctx.arithmetic_expression().forEach(arithmeticExpressionContext -> {
				tokens.addAll(visit(arithmeticExpressionContext));
				NOSPACE(tokens);
				tokens.add(new JpqlToken(",", ctx));
			});
			CLIP(tokens);
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.TRIM() != null) {

			tokens.add(new JpqlToken(ctx.TRIM().getText(), ctx, NO_SPACE));
			tokens.add(new JpqlToken("(", ctx, NO_SPACE));
			if (ctx.trim_specification() != null) {
				tokens.addAll(visit(ctx.trim_specification()));
			}
			if (ctx.trim_character() != null) {
				tokens.addAll(visit(ctx.trim_character()));
			}
			if (ctx.FROM() != null) {
				tokens.add(new JpqlToken(ctx.FROM().getText(), ctx));
			}
			tokens.addAll(visit(ctx.string_expression(0)));
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.LOWER() != null) {

			tokens.add(new JpqlToken(ctx.LOWER().getText(), ctx, NO_SPACE));
			tokens.add(new JpqlToken("(", ctx, NO_SPACE));
			tokens.addAll(visit(ctx.string_expression(0)));
			NOSPACE(tokens);
			tokens.add(new JpqlToken(")", ctx));
		} else if (ctx.UPPER() != null) {

			tokens.add(new JpqlToken(ctx.UPPER().getText(), ctx, NO_SPACE));
			tokens.add(new JpqlToken("(", ctx, NO_SPACE));
			tokens.addAll(visit(ctx.string_expression(0)));
			NOSPACE(tokens);
			tokens.add(new JpqlToken(")", ctx));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitTrim_specification(Jpql31Parser.Trim_specificationContext ctx) {

		if (ctx.LEADING() != null) {
			return List.of(new JpqlToken(ctx.LEADING().getText(), ctx));
		} else if (ctx.TRAILING() != null) {
			return List.of(new JpqlToken(ctx.TRAILING().getText(), ctx));
		} else {
			return List.of(new JpqlToken(ctx.BOTH().getText(), ctx));
		}
	}

	@Override
	public List<JpqlToken> visitFunction_invocation(Jpql31Parser.Function_invocationContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.FUNCTION().getText(), ctx));
		tokens.add(new JpqlToken("(", ctx));
		tokens.addAll(visit(ctx.function_name()));
		ctx.function_arg().forEach(functionArgContext -> {
			tokens.add(new JpqlToken(",", functionArgContext));
			tokens.addAll(visit(functionArgContext));
		});
		tokens.add(new JpqlToken(")", ctx));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitExtract_datetime_field(Jpql31Parser.Extract_datetime_fieldContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.EXTRACT().getText(), ctx));
		tokens.add(new JpqlToken("(", ctx));
		tokens.addAll(visit(ctx.datetime_field()));
		tokens.add(new JpqlToken(ctx.FROM().getText(), ctx));
		tokens.addAll(visit(ctx.datetime_expression()));
		tokens.add(new JpqlToken(")", ctx));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitDatetime_field(Jpql31Parser.Datetime_fieldContext ctx) {
		return visit(ctx.identification_variable());
	}

	@Override
	public List<JpqlToken> visitExtract_datetime_part(Jpql31Parser.Extract_datetime_partContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.EXTRACT().getText(), ctx));
		tokens.add(new JpqlToken("(", ctx));
		tokens.addAll(visit(ctx.datetime_part()));
		tokens.add(new JpqlToken(ctx.FROM().getText(), ctx));
		tokens.addAll(visit(ctx.datetime_expression()));
		tokens.add(new JpqlToken(")", ctx));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitDatetime_part(Jpql31Parser.Datetime_partContext ctx) {
		return visit(ctx.identification_variable());
	}

	@Override
	public List<JpqlToken> visitFunction_arg(Jpql31Parser.Function_argContext ctx) {

		if (ctx.literal() != null) {
			return visit(ctx.literal());
		} else if (ctx.state_valued_path_expression() != null) {
			return visit(ctx.state_valued_path_expression());
		} else if (ctx.input_parameter() != null) {
			return visit(ctx.input_parameter());
		} else {
			return visit(ctx.scalar_expression());
		}
	}

	@Override
	public List<JpqlToken> visitCase_expression(Jpql31Parser.Case_expressionContext ctx) {

		if (ctx.general_case_expression() != null) {
			return visit(ctx.general_case_expression());
		} else if (ctx.simple_case_expression() != null) {
			return visit(ctx.simple_case_expression());
		} else if (ctx.coalesce_expression() != null) {
			return visit(ctx.coalesce_expression());
		} else {
			return visit(ctx.nullif_expression());
		}
	}

	@Override
	public List<JpqlToken> visitGeneral_case_expression(Jpql31Parser.General_case_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.CASE().getText(), ctx));

		ctx.when_clause().forEach(whenClauseContext -> {
			tokens.addAll(visit(whenClauseContext));
		});

		tokens.add(new JpqlToken(ctx.ELSE().getText(), ctx));
		tokens.addAll(visit(ctx.scalar_expression()));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitWhen_clause(Jpql31Parser.When_clauseContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.WHEN().getText(), ctx));
		tokens.addAll(visit(ctx.conditional_expression()));
		tokens.add(new JpqlToken(ctx.THEN().getText(), ctx));
		tokens.addAll(visit(ctx.scalar_expression()));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitSimple_case_expression(Jpql31Parser.Simple_case_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.CASE().getText(), ctx));
		tokens.addAll(visit(ctx.case_operand()));

		ctx.simple_when_clause().forEach(simpleWhenClauseContext -> {
			tokens.addAll(visit(simpleWhenClauseContext));
		});

		tokens.add(new JpqlToken(ctx.ELSE().getText(), ctx));
		tokens.addAll(visit(ctx.scalar_expression()));
		tokens.add(new JpqlToken(ctx.END().getText(), ctx));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitCase_operand(Jpql31Parser.Case_operandContext ctx) {

		if (ctx.state_valued_path_expression() != null) {
			return visit(ctx.state_valued_path_expression());
		} else {
			return visit(ctx.type_discriminator());
		}
	}

	@Override
	public List<JpqlToken> visitSimple_when_clause(Jpql31Parser.Simple_when_clauseContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.WHEN().getText(), ctx));
		tokens.addAll(visit(ctx.scalar_expression(0)));
		tokens.add(new JpqlToken(ctx.THEN().getText(), ctx));
		tokens.addAll(visit(ctx.scalar_expression(1)));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitCoalesce_expression(Jpql31Parser.Coalesce_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.COALESCE().getText(), ctx, NO_SPACE));
		tokens.add(new JpqlToken("(", ctx, NO_SPACE));
		ctx.scalar_expression().forEach(scalarExpressionContext -> {
			tokens.addAll(visit(scalarExpressionContext));
			NOSPACE(tokens);
			tokens.add(new JpqlToken(",", scalarExpressionContext));
		});
		CLIP(tokens);
		tokens.add(new JpqlToken(")", ctx));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitNullif_expression(Jpql31Parser.Nullif_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.add(new JpqlToken(ctx.NULLIF().getText(), ctx));
		tokens.add(new JpqlToken("(", ctx));
		tokens.addAll(visit(ctx.scalar_expression(0)));
		tokens.add(new JpqlToken(",", ctx));
		tokens.addAll(visit(ctx.scalar_expression(1)));
		tokens.add(new JpqlToken(")", ctx));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitTrim_character(Jpql31Parser.Trim_characterContext ctx) {

		if (ctx.CHARACTER() != null) {
			return List.of(new JpqlToken(ctx.CHARACTER().getText(), ctx));
		} else if (ctx.character_valued_input_parameter() != null) {
			return visit(ctx.character_valued_input_parameter());
		} else {
			return List.of();
		}
	}

	@Override
	public List<JpqlToken> visitIdentification_variable(Jpql31Parser.Identification_variableContext ctx) {

		if (ctx.IDENTIFICATION_VARIABLE() != null) {
			return List.of(new JpqlToken(ctx.IDENTIFICATION_VARIABLE().getText(), ctx));
		} else if (ctx.COUNT() != null) {
			return List.of(new JpqlToken(ctx.COUNT().getText(), ctx));
		} else if (ctx.ORDER() != null) {
			return List.of(new JpqlToken(ctx.ORDER().getText(), ctx));
		} else if (ctx.KEY() != null) {
			return List.of(new JpqlToken(ctx.KEY().getText(), ctx));
		} else if (ctx.spel_expression() != null) {
			return visit(ctx.spel_expression());
		} else {
			return List.of();
		}
	}

	@Override
	public List<JpqlToken> visitConstructor_name(Jpql31Parser.Constructor_nameContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.state_field_path_expression()));
		NOSPACE(tokens);

		return tokens;
	}

	@Override
	public List<JpqlToken> visitLiteral(Jpql31Parser.LiteralContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.STRINGLITERAL() != null) {
			tokens.add(new JpqlToken(ctx.STRINGLITERAL().getText(), ctx));
		} else if (ctx.INTLITERAL() != null) {
			tokens.add(new JpqlToken(ctx.INTLITERAL().getText(), ctx));
		} else if (ctx.FLOATLITERAL() != null) {
			tokens.add(new JpqlToken(ctx.FLOATLITERAL().getText(), ctx));
		} else if (ctx.boolean_literal() != null) {
			tokens.addAll(visit(ctx.boolean_literal()));
		} else if (ctx.entity_type_literal() != null) {
			tokens.addAll(visit(ctx.entity_type_literal()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitInput_parameter(Jpql31Parser.Input_parameterContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.INTLITERAL() != null) {

			tokens.add(new JpqlToken("?", ctx, NO_SPACE));
			tokens.add(new JpqlToken(ctx.INTLITERAL().getText(), ctx));
		} else if (ctx.identification_variable() != null) {

			tokens.add(new JpqlToken(":", ctx, NO_SPACE));
			tokens.addAll(visit(ctx.identification_variable()));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitPattern_value(Jpql31Parser.Pattern_valueContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		tokens.addAll(visit(ctx.string_expression()));

		return tokens;
	}

	@Override
	public List<JpqlToken> visitDate_time_timestamp_literal(Jpql31Parser.Date_time_timestamp_literalContext ctx) {
		return List.of(new JpqlToken(ctx.STRINGLITERAL().getText(), ctx));
	}

	@Override
	public List<JpqlToken> visitEntity_type_literal(Jpql31Parser.Entity_type_literalContext ctx) {
		return visit(ctx.identification_variable());
	}

	@Override
	public List<JpqlToken> visitEscape_character(Jpql31Parser.Escape_characterContext ctx) {
		return List.of(new JpqlToken(ctx.CHARACTER().getText(), ctx));
	}

	@Override
	public List<JpqlToken> visitNumeric_literal(Jpql31Parser.Numeric_literalContext ctx) {

		if (ctx.INTLITERAL() != null) {
			return List.of(new JpqlToken(ctx.INTLITERAL().getText(), ctx));
		} else if (ctx.FLOATLITERAL() != null) {
			return List.of(new JpqlToken(ctx.FLOATLITERAL().getText(), ctx));
		} else {
			return List.of();
		}
	}

	@Override
	public List<JpqlToken> visitBoolean_literal(Jpql31Parser.Boolean_literalContext ctx) {

		if (ctx.TRUE() != null) {
			return List.of(new JpqlToken(ctx.TRUE().getText(), ctx));
		} else if (ctx.FALSE() != null) {
			return List.of(new JpqlToken(ctx.FALSE().getText(), ctx));
		} else {
			return List.of();
		}
	}

	@Override
	public List<JpqlToken> visitEnum_literal(Jpql31Parser.Enum_literalContext ctx) {
		return visit(ctx.state_field_path_expression());
	}

	@Override
	public List<JpqlToken> visitString_literal(Jpql31Parser.String_literalContext ctx) {

		if (ctx.CHARACTER() != null) {
			return List.of(new JpqlToken(ctx.CHARACTER().getText(), ctx));
		} else if (ctx.STRINGLITERAL() != null) {
			return List.of(new JpqlToken(ctx.STRINGLITERAL().getText(), ctx));
		} else {
			return List.of();
		}
	}

	@Override
	public List<JpqlToken> visitSingle_valued_embeddable_object_field(
			Jpql31Parser.Single_valued_embeddable_object_fieldContext ctx) {
		return visit(ctx.identification_variable());
	}

	@Override
	public List<JpqlToken> visitSubtype(Jpql31Parser.SubtypeContext ctx) {
		return visit(ctx.identification_variable());
	}

	@Override
	public List<JpqlToken> visitCollection_valued_field(Jpql31Parser.Collection_valued_fieldContext ctx) {
		return visit(ctx.identification_variable());
	}

	@Override
	public List<JpqlToken> visitSingle_valued_object_field(Jpql31Parser.Single_valued_object_fieldContext ctx) {
		return visit(ctx.identification_variable());
	}

	@Override
	public List<JpqlToken> visitState_field(Jpql31Parser.State_fieldContext ctx) {
		return visit(ctx.identification_variable());
	}

	@Override
	public List<JpqlToken> visitCollection_value_field(Jpql31Parser.Collection_value_fieldContext ctx) {
		return visit(ctx.identification_variable());
	}

	@Override
	public List<JpqlToken> visitEntity_name(Jpql31Parser.Entity_nameContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		ctx.identification_variable().forEach(identificationVariableContext -> {
			tokens.addAll(visit(identificationVariableContext));
			tokens.add(new JpqlToken(".", identificationVariableContext));
		});
		CLIP(tokens);

		return tokens;
	}

	@Override
	public List<JpqlToken> visitResult_variable(Jpql31Parser.Result_variableContext ctx) {
		return visit(ctx.identification_variable());
	}

	@Override
	public List<JpqlToken> visitSuperquery_identification_variable(
			Jpql31Parser.Superquery_identification_variableContext ctx) {
		return visit(ctx.identification_variable());
	}

	@Override
	public List<JpqlToken> visitCollection_valued_input_parameter(
			Jpql31Parser.Collection_valued_input_parameterContext ctx) {
		return visit(ctx.input_parameter());
	}

	@Override
	public List<JpqlToken> visitSingle_valued_input_parameter(Jpql31Parser.Single_valued_input_parameterContext ctx) {
		return visit(ctx.input_parameter());
	}

	@Override
	public List<JpqlToken> visitFunction_name(Jpql31Parser.Function_nameContext ctx) {
		return visit(ctx.string_literal());
	}

	@Override
	public List<JpqlToken> visitSpel_expression(Jpql31Parser.Spel_expressionContext ctx) {

		List<JpqlToken> tokens = new ArrayList<>();

		if (ctx.prefix.equals("#{#")) { // #{#entityName}

			tokens.add(new JpqlToken(ctx.prefix.getText(), ctx));
			ctx.identification_variable().forEach(identificationVariableContext -> {
				tokens.addAll(visit(identificationVariableContext));
				tokens.add(new JpqlToken(".", identificationVariableContext));
			});
			CLIP(tokens);
			tokens.add(new JpqlToken("}", ctx));

		} else if (ctx.prefix.equals("#{#[")) { // #{[0]}

			tokens.add(new JpqlToken(ctx.prefix.getText(), ctx));
			tokens.add(new JpqlToken(ctx.INTLITERAL().getText(), ctx));
			tokens.add(new JpqlToken("]}", ctx));

		} else if (ctx.prefix.equals("#{")) {// #{escape([0])} or #{escape('foo')}

			tokens.add(new JpqlToken(ctx.prefix.getText(), ctx));
			tokens.addAll(visit(ctx.identification_variable(0)));
			tokens.add(new JpqlToken("(", ctx));

			if (ctx.string_literal() != null) {
				tokens.addAll(visit(ctx.string_literal()));
			} else if (ctx.INTLITERAL() != null) {

				tokens.add(new JpqlToken("[", ctx));
				tokens.add(new JpqlToken(ctx.INTLITERAL().getText(), ctx));
				tokens.add(new JpqlToken("]", ctx));
			}

			tokens.add(new JpqlToken(")}", ctx));
		}

		return tokens;
	}

	@Override
	public List<JpqlToken> visitCharacter_valued_input_parameter(
			Jpql31Parser.Character_valued_input_parameterContext ctx) {

		if (ctx.CHARACTER() != null) {
			return List.of(new JpqlToken(ctx.CHARACTER().getText(), ctx));
		} else if (ctx.input_parameter() != null) {
			return visit(ctx.input_parameter());
		} else {
			return List.of();
		}
	}
}
