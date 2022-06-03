// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.schema.Index;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.BooleanIndexDefinition;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.OptimizePredicateExpression;
import com.yahoo.vespa.indexinglanguage.expressions.OutputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SetValueExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SetVarExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import static com.yahoo.prelude.fastsearch.VespaBackEndSearcher.SORTABLE_ATTRIBUTES_SUMMARY_CLASS;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates the predicate fields.
 *
 * @author Lester Solbakken
 */
public class PredicateProcessor extends Processor {

    public PredicateProcessor(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (SDField field : schema.allConcreteFields()) {
            if (field.getDataType() == DataType.PREDICATE) {
                if (validate && field.doesIndexing()) {
                    fail(schema, field, "Use 'attribute' instead of 'index'. This will require a refeed if you have upgraded.");
                }
                if (field.doesAttributing()) {
                    Attribute attribute = field.getAttributes().get(field.getName());
                    for (Index index : field.getIndices().values()) {
                        BooleanIndexDefinition booleanDefinition = index.getBooleanIndexDefiniton();
                        if (validate && (booleanDefinition == null ||  ! booleanDefinition.hasArity())) {
                            fail(schema, field, "Missing arity value in predicate field.");
                        }
                        if (validate && (booleanDefinition.getArity() < 2)) {
                            fail(schema, field, "Invalid arity value in predicate field, must be greater than 1.");
                        }
                        double threshold = booleanDefinition.getDensePostingListThreshold();
                        if (validate && (threshold <= 0 || threshold > 1)) {
                            fail(schema, field, "Invalid dense-posting-list-threshold value in predicate field. " +
                                                "Value must be in range (0..1].");
                        }

                        attribute.setArity(booleanDefinition.getArity());
                        attribute.setLowerBound(booleanDefinition.getLowerBound());
                        attribute.setUpperBound(booleanDefinition.getUpperBound());

                        attribute.setDensePostingListThreshold(threshold);
                        addPredicateOptimizationIlScript(field, booleanDefinition);
                    }
                    DocumentSummary summary = schema.getSummariesInThis().get(SORTABLE_ATTRIBUTES_SUMMARY_CLASS);
                    if (summary != null) {
                        summary.remove(attribute.getName());
                    }
                    for (SummaryField summaryField : schema.getSummaryFields(field)) {
                        summaryField.setTransform(SummaryTransform.NONE);
                    }
                }
            } else if (validate && field.getDataType().getPrimitiveType() == DataType.PREDICATE) {
                fail(schema, field, "Collections of predicates are not allowed.");
            } else if (validate && field.getDataType() == DataType.RAW && field.doesIndexing()) {
                fail(schema, field, "Indexing of RAW fields is not supported.");
            } else if (validate) {
                // if field is not a predicate, disallow predicate-related index parameters
                for (Index index : field.getIndices().values()) {
                    if (index.getBooleanIndexDefiniton() != null) {
                        BooleanIndexDefinition def = index.getBooleanIndexDefiniton();
                        if (def.hasArity()) {
                            fail(schema, field, "Arity parameter is used only for predicate type fields.");
                        } else if (def.hasLowerBound() || def.hasUpperBound()) {
                            fail(schema, field, "Parameters lower-bound and upper-bound are used only for predicate type fields.");
                        } else if (def.hasDensePostingListThreshold()) {
                            fail(schema, field, "Parameter dense-posting-list-threshold is used only for predicate type fields.");
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

        ExpressionConverter converter = new PredicateOutputTransformer(schema);
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

        PredicateOutputTransformer(Schema schema) {
            super(OptimizePredicateExpression.class, schema);
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
