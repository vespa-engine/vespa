// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.searchdefinition.Index;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.BooleanIndexDefinition;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.expressions.*;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates the predicate fields.
 *
 * @author Lester Solbakken
 */
public class PredicateProcessor extends Processor {

    public PredicateProcessor(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        for (SDField field : search.allConcreteFields()) {
            if (field.getDataType() == DataType.PREDICATE) {
                if (validate && field.doesIndexing()) {
                    fail(search, field, "Use 'attribute' instead of 'index'. This will require a refeed if you have upgraded.");
                }
                if (field.doesAttributing()) {
                    Attribute attribute = field.getAttributes().get(field.getName());
                    for (Index index : field.getIndices().values()) {
                        BooleanIndexDefinition booleanDefinition = index.getBooleanIndexDefiniton();
                        if (validate && (booleanDefinition == null ||  ! booleanDefinition.hasArity())) {
                            fail(search, field, "Missing arity value in predicate field.");
                        }
                        if (validate && (booleanDefinition.getArity() < 2)) {
                            fail(search, field, "Invalid arity value in predicate field, must be greater than 1.");
                        }
                        double threshold = booleanDefinition.getDensePostingListThreshold();
                        if (validate && (threshold <= 0 || threshold > 1)) {
                            fail(search, field, "Invalid dense-posting-list-threshold value in predicate field. " +
                                                "Value must be in range (0..1].");
                        }

                        attribute.setArity(booleanDefinition.getArity());
                        attribute.setLowerBound(booleanDefinition.getLowerBound());
                        attribute.setUpperBound(booleanDefinition.getUpperBound());

                        attribute.setDensePostingListThreshold(threshold);
                        addPredicateOptimizationIlScript(field, booleanDefinition);
                    }
                    DocumentSummary summary = search.getSummary("attributeprefetch");
                    if (summary != null) {
                        summary.remove(attribute.getName());
                    }
                    for (SummaryField summaryField : search.getSummaryFields(field).values()) {
                        summaryField.setTransform(SummaryTransform.NONE);
                    }
                }
            } else if (validate && field.getDataType().getPrimitiveType() == DataType.PREDICATE) {
                fail(search, field, "Collections of predicates are not allowed.");
            } else if (validate && field.getDataType() == DataType.RAW && field.doesIndexing()) {
                fail(search, field, "Indexing of RAW fields is not supported.");
            } else if (validate) {
                // if field is not a predicate, disallow predicate-related index parameters
                for (Index index : field.getIndices().values()) {
                    if (index.getBooleanIndexDefiniton() != null) {
                        BooleanIndexDefinition def = index.getBooleanIndexDefiniton();
                        if (def.hasArity()) {
                            fail(search, field, "Arity parameter is used only for predicate type fields.");
                        } else if (def.hasLowerBound() || def.hasUpperBound()) {
                            fail(search, field, "Parameters lower-bound and upper-bound are used only for predicate type fields.");
                        } else if (def.hasDensePostingListThreshold()) {
                            fail(search, field, "Parameter dense-posting-list-threshold is used only for predicate type fields.");
                        }
                    }
                }
            }
        }
    }

    private void addPredicateOptimizationIlScript(SDField field, BooleanIndexDefinition booleanIndexDefiniton) {
        Expression script = field.getIndexingScript();
        if (script == null) return;

        script = new StatementExpression(makeSetPredicateVariablesScript(booleanIndexDefiniton), script);

        ExpressionConverter converter = new PredicateOutputTransformer(search);
        field.setIndexingScript(new ScriptExpression((StatementExpression)converter.convert(script)));
    }

    private Expression makeSetPredicateVariablesScript(BooleanIndexDefinition options) {
        List<Expression> expressions = new ArrayList<>();
        expressions.add(new SetValueExpression(new IntegerFieldValue(options.getArity())));
        expressions.add(new SetVarExpression("arity"));
        if (options.hasLowerBound()) {
            expressions.add(new SetValueExpression(new LongFieldValue(options.getLowerBound())));
            expressions.add(new SetVarExpression("lower_bound"));
        }
        if (options.hasUpperBound()) {
            expressions.add(new SetValueExpression(new LongFieldValue(options.getUpperBound())));
            expressions.add(new SetVarExpression("upper_bound"));
        }
        return new StatementExpression(expressions);
    }

    private static class PredicateOutputTransformer extends TypedTransformProvider {

        PredicateOutputTransformer(Search search) {
            super(OptimizePredicateExpression.class, search);
        }

        @Override
        protected boolean requiresTransform(Expression exp, DataType fieldType) {
            return exp instanceof OutputExpression && fieldType == DataType.PREDICATE;
        }

        @Override
        protected Expression newTransform(DataType fieldType) {
            return new OptimizePredicateExpression();
        }

    }

}
