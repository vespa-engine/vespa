// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.expressions.*;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.*;

/**
 * This processor modifies all indexing scripts so that they output to the owning field by default. It also prevents
 * any output expression from writing to any field except for the owning field. Finally, for <code>SummaryExpression</code>,
 * this processor expands to write all appropriate summary fields.
 *
 * @author Simon Thoresen Hult
 */
public class IndexingOutputs extends Processor {

    public IndexingOutputs(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (SDField field : schema.allConcreteFields()) {
            ScriptExpression script = field.getIndexingScript();
            if (script == null) continue;

            Set<String> summaryFields = new TreeSet<>();
            findSummaryTo(schema, field, summaryFields, summaryFields);
            MyConverter converter = new MyConverter(schema, field, summaryFields, validate);
            field.setIndexingScript((ScriptExpression)converter.convert(script));
        }
    }

    public void findSummaryTo(Schema schema, SDField field, Set<String> dynamicSummary, Set<String> staticSummary) {
        var summaryFields = schema.getSummaryFields(field);
        if (summaryFields.isEmpty()) {
            fillSummaryToFromField(field, dynamicSummary, staticSummary);
        } else {
            fillSummaryToFromSearch(schema, field, summaryFields, dynamicSummary, staticSummary);
        }
    }

    private void fillSummaryToFromSearch(Schema schema, SDField field, List<SummaryField> summaryFields,
                                         Set<String> dynamicSummary, Set<String> staticSummary) {
        for (SummaryField summaryField : summaryFields) {
            fillSummaryToFromSummaryField(schema, field, summaryField, dynamicSummary, staticSummary);
        }
    }

    private void fillSummaryToFromSummaryField(Schema schema, SDField field, SummaryField summaryField,
                                               Set<String> dynamicSummary, Set<String> staticSummary) {
        SummaryTransform summaryTransform = summaryField.getTransform();
        String summaryName = summaryField.getName();
        if (summaryTransform.isDynamic() && summaryField.getSourceCount() > 2) {
            // Avoid writing to summary fields that have more than a single input field, as that is handled by the
            // summary rewriter in the search core.
            return;
        }
        if (summaryTransform.isDynamic()) {
            DataType fieldType = field.getDataType();
            if (!DynamicSummaryTransformUtils.summaryFieldIsPopulatedBySourceField(fieldType)) {
                if (!DynamicSummaryTransformUtils.isSupportedType(fieldType)) {
                    warn(schema, field, "Dynamic summaries are only supported for fields of type " +
                            "string and array<string>, ignoring summary field '" + summaryField.getName() +
                            "' for sd field '" + field.getName() + "' of type " +
                            fieldType.getName() + ".");
                }
                return;
            }
            dynamicSummary.add(summaryName);
        } else if (summaryTransform != SummaryTransform.ATTRIBUTE) {
            staticSummary.add(summaryName);
        }
    }

    private static void fillSummaryToFromField(SDField field, Set<String> dynamicSummary, Set<String> staticSummary) {
        for (SummaryField summaryField : field.getSummaryFields().values()) {
            String summaryName = summaryField.getName();
            if (summaryField.getTransform().isDynamic()) {
                dynamicSummary.add(summaryName);
            } else {
                staticSummary.add(summaryName);
            }
        }
    }

    private class MyConverter extends ExpressionConverter {

        final Schema schema;
        final Field field;
        final Set<String> summaryFields;
        final boolean validate;

        MyConverter(Schema schema, Field field, Set<String> summaryFields, boolean validate) {
            this.schema = schema;
            this.field = field;
            this.summaryFields = summaryFields.isEmpty() ? Collections.singleton(field.getName()) : summaryFields;
            this.validate = validate;
        }

        @Override
        protected boolean shouldConvert(Expression exp) {
            if ( ! (exp instanceof OutputExpression)) {
                return false;
            }
            String fieldName = ((OutputExpression)exp).getFieldName();
            if (fieldName == null) {
                return true; // inject appropriate field name
            }
            if ( validate && ! fieldName.equals(field.getName())) {
                fail(schema, field, "Indexing expression '" + exp + "' attempts to write to a field other than '" +
                                    field.getName() + "'.");
            }
            return false;
        }

        @Override
        protected Expression doConvert(Expression exp) {
            List<Expression> ret = new LinkedList<>();
            if (exp instanceof AttributeExpression) {
                ret.add(new AttributeExpression(field.getName()));
            } else if (exp instanceof IndexExpression) {
                ret.add(new IndexExpression(field.getName()));
            } else if (exp instanceof SummaryExpression) {
                for (String fieldName : summaryFields) {
                    ret.add(new SummaryExpression(fieldName));
                }
            } else {
                throw new UnsupportedOperationException(exp.getClass().getName());
            }
            return new StatementExpression(ret);
        }

    }

}
