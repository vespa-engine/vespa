// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.*;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Matching;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.Stemming;
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

    public NGramMatch(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        for (SDField field : search.allConcreteFields()) {
            if (field.getMatching().getType().equals(Matching.Type.GRAM))
                implementGramMatch(search, field, validate);
            else if (validate && field.getMatching().getGramSize() >= 0)
                throw new IllegalArgumentException("gram-size can only be set when the matching mode is 'gram'");
        }
    }

    private void implementGramMatch(Search search, SDField field, boolean validate) {
        if (validate && field.doesAttributing())
            throw new IllegalArgumentException("gram matching is not supported with attributes, use 'index' not 'attribute' in indexing");

        int n = field.getMatching().getGramSize();
        if (n < 0)
            n=DEFAULT_GRAM_SIZE; // not set - use default gram size
        if (validate && n == 0)
            throw new IllegalArgumentException("Illegal gram size in " + field + ": Must be at least 1");
        field.getNormalizing().inferCodepoint();
        field.setStemming(Stemming.NONE); // not compatible with stemming and normalizing
        field.addQueryCommand("ngram " + n);
        field.setIndexingScript((ScriptExpression)new MyProvider(search, n).convert(field.getIndexingScript()));
    }

    private static class MyProvider extends TypedTransformProvider {

        final int ngram;

        MyProvider(Search search, int ngram) {
            super(NGramExpression.class, search);
            this.ngram = ngram;
        }

        @Override
        protected boolean requiresTransform(Expression exp, DataType fieldType) {
            return exp instanceof OutputExpression;
        }

        @Override
        protected Expression newTransform(DataType fieldType) {
            Expression exp = new NGramExpression(null, ngram);
            if (fieldType instanceof CollectionDataType) {
                exp = new ForEachExpression(exp);
            }
            return exp;
        }

    }

}
