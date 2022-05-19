// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.ExpressionVisitor;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * This processor modifies all indexing scripts so that they input the value of the owning field by default. It also
 * ensures that all fields used as input exist.
 *
 * @author Simon Thoresen Hult
 */
public class IndexingInputs extends Processor {

    public IndexingInputs(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (SDField field : schema.allConcreteFields()) {
            ScriptExpression script = field.getIndexingScript();
            if (script == null) continue;

            String fieldName = field.getName();
            script = (ScriptExpression)new DefaultToCurrentField(fieldName).convert(script);
            script = (ScriptExpression)new EnsureInputExpression(fieldName).convert(script);
            if (validate)
                new VerifyInputExpression(schema, field).visit(script);

            field.setIndexingScript(script);
        }
    }

    private static class DefaultToCurrentField extends ExpressionConverter {

        final String fieldName;

        DefaultToCurrentField(String fieldName) {
            this.fieldName = fieldName;
        }

        @Override
        protected boolean shouldConvert(Expression exp) {
            return exp instanceof InputExpression && ((InputExpression)exp).getFieldName() == null;
        }

        @Override
        protected Expression doConvert(Expression exp) {
            return new InputExpression(fieldName);
        }
    }

    private static class EnsureInputExpression extends ExpressionConverter {

        final String fieldName;

        EnsureInputExpression(String fieldName) {
            this.fieldName = fieldName;
        }

        @Override
        protected boolean shouldConvert(Expression exp) {
            return exp instanceof StatementExpression;
        }

        @Override
        protected Expression doConvert(Expression exp) {
            if (exp.requiredInputType() != null) {
                return new StatementExpression(new InputExpression(fieldName), exp);
            } else {
                return exp;
            }
        }
    }

    private class VerifyInputExpression extends ExpressionVisitor {

        private final Schema schema;
        private final SDField field;

        public VerifyInputExpression(Schema schema, SDField field) {
            this.schema = schema;
            this.field = field;
        }

        @Override
        protected void doVisit(Expression exp) {
            if ( ! (exp instanceof InputExpression)) return;
            String inputField = ((InputExpression)exp).getFieldName();
            if (schema.getField(inputField).hasFullIndexingDocprocRights()) return;

            fail(schema, field, "Indexing script refers to field '" + inputField + "' which does not exist " +
                                "in document type '" + schema.getDocument().getName() + "', and is not a mutable attribute.");
        }
    }
}
