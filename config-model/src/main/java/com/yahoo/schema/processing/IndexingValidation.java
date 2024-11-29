// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.expressions.AttributeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.FieldTypeAdapter;
import com.yahoo.vespa.indexinglanguage.expressions.IndexExpression;
import com.yahoo.vespa.indexinglanguage.expressions.OutputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SummaryExpression;
import com.yahoo.vespa.indexinglanguage.expressions.VerificationContext;
import com.yahoo.vespa.indexinglanguage.expressions.VerificationException;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import com.yahoo.yolean.Exceptions;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Simon Thoresen Hult
 */
public class IndexingValidation extends Processor {

    IndexingValidation(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        VerificationContext context = new VerificationContext(new MyAdapter(schema));
        for (SDField field : schema.allConcreteFields()) {
            ScriptExpression script = field.getIndexingScript();
            try {
                script.verify(context);
                MyConverter converter = new MyConverter();
                for (StatementExpression exp : script) {
                    converter.convert(exp); // TODO: stop doing this explicitly when visiting a script does not branch
                }
            } catch (VerificationException e) {
                fail(schema, field, Exceptions.toMessageString(e));
            }
        }
    }

    private static class MyConverter extends ExpressionConverter {

        final Set<String> outputs = new HashSet<>();
        final Set<String> prevNames = new HashSet<>();

        @Override
        public ExpressionConverter branch() {
            MyConverter ret = new MyConverter();
            ret.outputs.addAll(outputs);
            ret.prevNames.addAll(prevNames);
            return ret;
        }

        @Override
        protected boolean shouldConvert(Expression expression) {
            if (expression instanceof OutputExpression) {
                String fieldName = ((OutputExpression)expression).getFieldName();
                if (outputs.contains(fieldName) && !prevNames.contains(fieldName)) {
                    throw new VerificationException(expression, "Attempting to assign conflicting values to field '" +
                                                                fieldName + "'");
                }
                outputs.add(fieldName);
                prevNames.add(fieldName);
            }
            if (expression.createdOutputType() != null) {
                prevNames.clear();
            }
            return false;
        }

        @Override
        protected Expression doConvert(Expression exp) {
            throw new UnsupportedOperationException();
        }
    }

    private static class MyAdapter implements FieldTypeAdapter {

        final Schema schema;

        MyAdapter(Schema schema) {
            this.schema = schema;
        }

        @Override
        public DataType getInputType(Expression exp, String fieldName) {
            SDField field = schema.getDocumentField(fieldName);
            if (field == null) {
                throw new VerificationException(exp, "Input field '" + fieldName + "' not found.");
            }
            return field.getDataType();
        }

        @Override
        public void tryOutputType(Expression expression, String fieldName, DataType valueType) {
            String fieldDesc;
            DataType fieldType;
            if (expression instanceof AttributeExpression) {
                Attribute attribute = schema.getAttribute(fieldName);
                if (attribute == null) {
                    throw new VerificationException(expression, "Attribute '" + fieldName + "' not found.");
                }
                fieldDesc = "attribute";
                fieldType = attribute.getDataType();
            } else if (expression instanceof IndexExpression) {
                SDField field = schema.getConcreteField(fieldName);
                if (field == null) {
                    throw new VerificationException(expression, "Index field '" + fieldName + "' not found.");
                }
                fieldDesc = "index field";
                fieldType = field.getDataType();
            } else if (expression instanceof SummaryExpression) {
                SummaryField field = schema.getSummaryField(fieldName);
                if (field == null) {
                    // Use document field if summary field is not found
                    SDField sdField = schema.getConcreteField(fieldName);
                    if (sdField != null && sdField.doesSummarying()) {
                        fieldDesc = "document field";
                        fieldType = sdField.getDataType();
                    } else {
                        throw new VerificationException(expression, "Summary field '" + fieldName + "' not found.");
                    }
                } else {
                    fieldDesc = "summary field";
                    fieldType = field.getDataType();
                }
            } else {
                throw new UnsupportedOperationException();
            }
            if ( ! fieldType.isAssignableFrom(valueType))
                throw new VerificationException(expression, "Can not assign " + valueType.getName() + " to " + fieldDesc +
                                                            " '" + fieldName + "' which is " + fieldType.getName() + ".");
        }

    }

}
