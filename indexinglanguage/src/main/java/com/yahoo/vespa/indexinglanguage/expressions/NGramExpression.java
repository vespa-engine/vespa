// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.annotation.AnnotationTypes;
import com.yahoo.document.annotation.Span;
import com.yahoo.document.annotation.SpanList;
import com.yahoo.document.annotation.SpanTree;
import com.yahoo.document.annotation.SpanTrees;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.GramSplitter;
import com.yahoo.language.process.TokenType;
import com.yahoo.vespa.indexinglanguage.linguistics.LinguisticsAnnotator;

import java.util.Iterator;

/**
 * A filter which splits incoming text into n-grams.
 *
 * @author bratseth
 */
public final class NGramExpression extends Expression {

    private final Linguistics linguistics;
    private final int gramSize;

    /**
     * Creates an executable ngram expression
     *
     * @param linguistics the gram splitter to use, or null if this is used for representation and will not be executed
     * @param gramSize the gram size
     */
    public NGramExpression(Linguistics linguistics, int gramSize) {
        super(DataType.STRING);
        this.linguistics = linguistics;
        this.gramSize = gramSize;
    }

    public Linguistics getLinguistics() {
        return linguistics;
    }

    public int getGramSize() {
        return gramSize;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        StringFieldValue input = (StringFieldValue) context.getValue();
        if (input.getSpanTree(SpanTrees.LINGUISTICS) != null) {
            // This expression is already executed for this input instance
            return;
        }
        StringFieldValue output = input.clone();
        context.setValue(output);

        SpanList spanList = output.setSpanTree(new SpanTree(SpanTrees.LINGUISTICS)).spanList();
        int lastPosition = 0;
        for (Iterator<GramSplitter.Gram> it = linguistics.getGramSplitter().split(output.getString(), gramSize); it.hasNext();) {
            GramSplitter.Gram gram = it.next();
            // if there is a gap before this gram, then annotate the gram as punctuation
            // (technically it may be of various types, but it does not matter - we just
            // need to annotate it somehow (as a non-term) to make sure it is added to the summary)
            if (lastPosition < gram.getStart()) {
                typedSpan(lastPosition, gram.getStart() - lastPosition, TokenType.PUNCTUATION, spanList);
            }

            // annotate gram as a word term
            String gramString = gram.extractFrom(output.getString());
            typedSpan(gram.getStart(), gram.getCodePointCount(), TokenType.ALPHABETIC, spanList).
                    annotate(LinguisticsAnnotator.lowerCaseTermAnnotation(gramString, gramString));

            lastPosition = gram.getStart() + gram.getCodePointCount();
        }
        // handle punctuation at the end
        if (lastPosition < output.toString().length()) {
            typedSpan(lastPosition, output.toString().length() - lastPosition, TokenType.PUNCTUATION, spanList);
        }
    }

    private Span typedSpan(int from, int length, TokenType tokenType, SpanList spanList) {
        return (Span)spanList.span(from, length).annotate(AnnotationTypes.TOKEN_TYPE, tokenType.getValue());
    }

    @Override
    protected void doVerify(VerificationContext context) {
        // empty
    }

    @Override
    public DataType createdOutputType() {
        return null;
    }

    @Override
    public String toString() {
        return "ngram " + gramSize;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NGramExpression rhs)) return false;

        if (linguistics == null) {
            if (rhs.linguistics != null) return false;
        } else if (rhs.linguistics != null) {
            if (linguistics.getClass() != rhs.linguistics.getClass()) return false;
        } else {
            return false;
        }
        if (gramSize != rhs.gramSize) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + gramSize;
    }

}
