// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.Stemming;
import com.yahoo.vespa.indexinglanguage.expressions.*;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * The implementation of "gram" matching - splitting the incoming text and the queries into
 * n-grams for matching. This will also validate the gram settings.
 *
 * @author bratseth
 */
public class NGramMatch extends Processor {

    public static final int DEFAULT_GRAM_SIZE = 2;

    public NGramMatch(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (SDField field : schema.allConcreteFields()) {
            if (field.getMatching().getType().equals(MatchType.GRAM))
                implementGramMatch(schema, field, validate);
            else if (validate && field.getMatching().getGramSize() >= 0)
                throw new IllegalArgumentException("gram-size can only be set when the matching mode is 'gram'");
        }
    }

    private void implementGramMatch(Schema schema, SDField field, boolean validate) {
        if (validate && field.doesAttributing() && ! field.doesIndexing())
            throw new IllegalArgumentException("gram matching is not supported with attributes, use 'index' in indexing");

        int n = field.getMatching().getGramSize();
        if (n < 0)
            n = DEFAULT_GRAM_SIZE; // not set - use default gram size
        if (validate && n == 0)
            throw new IllegalArgumentException("Illegal gram size in " + field + ": Must be at least 1");
        field.getNormalizing().inferCodepoint();
        field.setStemming(Stemming.NONE); // not compatible with stemming and normalizing
        field.addQueryCommand("ngram " + n);
        field.setIndexingScript((ScriptExpression)new MyProvider(schema, n).convert(field.getIndexingScript()));
    }

    private static class MyProvider extends TypedTransformProvider {

        final int ngram;

        MyProvider(Schema schema, int ngram) {
            super(NGramExpression.class, schema);
            this.ngram = ngram;
        }

        @Override
        protected boolean requiresTransform(Expression exp, DataType fieldType) {
            return exp instanceof OutputExpression;
        }

        @Override
        protected Expression newTransform(DataType fieldType) {
            Expression exp = new NGramExpression(null, ngram);
            if (fieldType instanceof CollectionDataType)
                exp = new ForEachExpression(exp);
            return exp;
        }

    }

}
