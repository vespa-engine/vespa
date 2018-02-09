// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.Field;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.OutputExpression;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * @author Simon Thoresen Hult
 */
public class IndexingValues extends Processor {

    public IndexingValues(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process() {
        for (Field field : search.getDocument().fieldSet()) {
            SDField sdField = (SDField)field;
            if (!sdField.isExtraField()) {
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
                throw newProcessException(search, field,
                                          "Indexing expression '" + mutatedBy + "' modifies the value of the " +
                                          "document field '" + field.getName() + "'. This is no longer supported -- " +
                                          "declare such fields outside the document.");
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
