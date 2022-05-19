// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.document.Field;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.OutputExpression;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * @author Simon Thoresen Hult
 */
public class IndexingValues extends Processor {

    public IndexingValues(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if ( ! validate) return;

        for (Field field : schema.getDocument().fieldSet()) {
            SDField sdField = (SDField)field;
            if ( ! sdField.isExtraField()) {
                new RequireThatDocumentFieldsAreImmutable(field).convert(sdField.getIndexingScript());
            }
        }
    }

    private class RequireThatDocumentFieldsAreImmutable extends ExpressionConverter {

        final Field field;
        Expression mutatedBy;

        RequireThatDocumentFieldsAreImmutable(Field field) {
            this.field = field;
        }

        @Override
        public ExpressionConverter branch() {
            return clone();
        }

        @Override
        protected boolean shouldConvert(Expression exp) {
            if (exp instanceof OutputExpression && mutatedBy != null) {
                throw newProcessException(schema, field,
                                          "Indexing expression '" + mutatedBy + "' attempts to modify the value of the " +
                                          "document field '" + field.getName() + "'. Use a field outside the document " +
                                          "block instead.");
            }
            if (exp instanceof InputExpression && ((InputExpression)exp).getFieldName().equals(field.getName())) {
                mutatedBy = null;
            } else if (exp.createdOutputType() != null) {
                mutatedBy = exp;
            }
            return false;
        }

        @Override
        protected Expression doConvert(Expression exp) {
            throw new UnsupportedOperationException();
        }
    }
}
