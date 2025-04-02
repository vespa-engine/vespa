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

import java.util.Objects;

import static com.yahoo.language.LinguisticsCase.toLowerCase;

/**
 * @author Simon Thoresen Hult
 */
public final class ExactExpression extends Expression {

    private final AnnotatorConfig config;

    public ExactExpression(AnnotatorConfig config) {
        this.config = config;
    }

    @Override
    public boolean isMutating() { return false; }

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        return super.setInputType(inputType, DataType.STRING, context);
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        return super.setOutputType(DataType.STRING, outputType, null, context);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        StringFieldValue input = (StringFieldValue) context.getCurrentValue();
        if (input.getString().isEmpty()) return;

        StringFieldValue output = input.clone();
        context.setCurrentValue(output);

        String prev = output.getString();
        String next = config.getLowercase() ? toLowerCase(prev) : prev;

        SpanTree tree = output.getSpanTree(SpanTrees.LINGUISTICS);
        if (next.length() > config.getMaxTokenLength()) {
            if (tree != null)
                output.removeSpanTree(SpanTrees.LINGUISTICS);
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
    public String toString() {
        return "exact" + config.parameterString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ExactExpression other)) return false;
        if (!config.equals(other.config)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), config);
    }

}
