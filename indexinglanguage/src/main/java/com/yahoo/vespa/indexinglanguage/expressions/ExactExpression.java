// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.annotation.Annotation;
import com.yahoo.document.annotation.AnnotationTypes;
import com.yahoo.document.annotation.Span;
import com.yahoo.document.annotation.SpanList;
import com.yahoo.document.annotation.SpanNode;
import com.yahoo.document.annotation.SpanTree;
import com.yahoo.document.annotation.SpanTrees;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.process.TokenType;
import com.yahoo.vespa.indexinglanguage.linguistics.AnnotatorConfig;

import java.util.OptionalInt;

import static com.yahoo.language.LinguisticsCase.toLowerCase;

/**
 * @author Simon Thoresen Hult
 */
public final class ExactExpression extends Expression {

    private int maxTokenLength;

    private ExactExpression(OptionalInt maxTokenLength) {
        super(DataType.STRING);
        this.maxTokenLength = maxTokenLength.isPresent() ? maxTokenLength.getAsInt() : AnnotatorConfig.getDefaultMaxTokenLength();
    }

    public ExactExpression() {
        this(OptionalInt.empty());;
    }

    public ExactExpression(int maxTokenLength) {
        this(OptionalInt.of(maxTokenLength));
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        StringFieldValue input = (StringFieldValue) context.getValue();
        if (input.getString().isEmpty()) return;

        StringFieldValue output = input.clone();
        context.setValue(output);

        String prev = output.getString();
        String next = toLowerCase(prev);

        SpanTree tree = output.getSpanTree(SpanTrees.LINGUISTICS);
        if (next.length() > maxTokenLength) {
            if (tree != null) {
                output.removeSpanTree(SpanTrees.LINGUISTICS);
            }
            return;
        }
        SpanList root;
        if (tree == null) {
            root = new SpanList();
            tree = new SpanTree(SpanTrees.LINGUISTICS, root);
            output.setSpanTree(tree);
        }
        else {
            root = (SpanList)tree.getRoot();
        }
        SpanNode node = new Span(0, prev.length());
        tree.annotate(node, new Annotation(AnnotationTypes.TERM,
                                           next.equals(prev) ? null : new StringFieldValue(next)));
        tree.annotate(node, new Annotation(AnnotationTypes.TOKEN_TYPE,
                                           new IntegerFieldValue(TokenType.ALPHABETIC.getValue())));
        root.add(node);
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
    public String toString()
    {
        StringBuilder ret = new StringBuilder();
        ret.append("exact");
        if (maxTokenLength != AnnotatorConfig.getDefaultMaxTokenLength()) {
            ret.append(" max-token-length:" + maxTokenLength);
        }
        return ret.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ExactExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
