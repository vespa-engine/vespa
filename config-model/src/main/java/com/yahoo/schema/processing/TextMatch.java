// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.Stemming;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.ExpressionVisitor;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.ForEachExpression;
import com.yahoo.vespa.indexinglanguage.expressions.IndexExpression;
import com.yahoo.vespa.indexinglanguage.expressions.OutputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SummaryExpression;
import com.yahoo.vespa.indexinglanguage.expressions.TokenizeExpression;
import com.yahoo.vespa.indexinglanguage.linguistics.AnnotatorConfig;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.Set;
import java.util.TreeSet;

/**
 * @author Simon Thoresen Hult
 */
public class TextMatch extends Processor {

    public TextMatch(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (SDField field : schema.allConcreteFields()) {
            if (field.getMatching().getType() != MatchType.TEXT) continue;

            ScriptExpression script = field.getIndexingScript();
            if (script == null) continue;

            DataType fieldType = field.getDataType();
            if (fieldType instanceof CollectionDataType) {
                fieldType = ((CollectionDataType)fieldType).getNestedType();
            }
            if (fieldType != DataType.STRING) continue;

            Set<String> dynamicSummary = new TreeSet<>();
            Set<String> staticSummary = new TreeSet<>();
            new IndexingOutputs(schema, deployLogger, rankProfileRegistry, queryProfiles).findSummaryTo(schema,
                                                                                                        field,
                                                                                                        dynamicSummary,
                                                                                                        staticSummary);
            MyVisitor visitor = new MyVisitor(dynamicSummary);
            visitor.visit(script);
            if ( ! visitor.requiresTokenize) continue;

            ExpressionConverter converter = new MyStringTokenizer(schema, findAnnotatorConfig(schema, field));
            field.setIndexingScript((ScriptExpression)converter.convert(script));
        }
    }

    private AnnotatorConfig findAnnotatorConfig(Schema schema, SDField field) {
        AnnotatorConfig ret = new AnnotatorConfig();
        Stemming activeStemming = field.getStemming();
        if (activeStemming == null) {
            activeStemming = schema.getStemming();
        }
        ret.setStemMode(activeStemming.toStemMode());
        ret.setRemoveAccents(field.getNormalizing().doRemoveAccents());
        if ((field.getMatching() != null) && (field.getMatching().maxLength() != null)) {
            ret.setMaxTokenLength(field.getMatching().maxLength());
        }
        return ret;
    }

    private static class MyVisitor extends ExpressionVisitor {

        final Set<String> dynamicSummaryFields;
        boolean requiresTokenize = false;

        MyVisitor(Set<String> dynamicSummaryFields) {
            this.dynamicSummaryFields = dynamicSummaryFields;
        }

        @Override
        protected void doVisit(Expression exp) {
            if (exp instanceof IndexExpression) {
                requiresTokenize = true;
            }
            if (exp instanceof SummaryExpression &&
                dynamicSummaryFields.contains(((SummaryExpression)exp).getFieldName()))
            {
                requiresTokenize = true;
            }
        }

    }

    private static class MyStringTokenizer extends TypedTransformProvider {

        final AnnotatorConfig annotatorCfg;

        MyStringTokenizer(Schema schema, AnnotatorConfig annotatorCfg) {
            super(TokenizeExpression.class, schema);
            this.annotatorCfg = annotatorCfg;
        }

        @Override
        protected boolean requiresTransform(Expression exp, DataType fieldType) {
            return exp instanceof OutputExpression;
        }

        @Override
        protected Expression newTransform(DataType fieldType) {
            Expression exp = new TokenizeExpression(null, annotatorCfg);
            if (fieldType instanceof CollectionDataType) {
                exp = new ForEachExpression(exp);
            }
            return exp;
        }

    }

}
