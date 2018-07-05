// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.annotation.*;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.process.TokenType;

import static com.yahoo.language.LinguisticsCase.toLowerCase;

/**
 * @author Simon Thoresen Hult
 */
public class ExactExpression extends Expression {

    @Override
    protected void doExecute(ExecutionContext ctx) {
        StringFieldValue input = (StringFieldValue)ctx.getValue();
        if (input.getString().isEmpty()) {
            return;
        }
        StringFieldValue output = input.clone();
        ctx.setValue(output);

        String prev = output.getString();
        String next = toLowerCase(prev);

        SpanList root = new SpanList();
        SpanTree tree = new SpanTree(SpanTrees.LINGUISTICS, root);
        SpanNode node = new Span(0, prev.length());
        tree.annotate(node, new Annotation(AnnotationTypes.TERM,
                                           next.equals(prev) ? null : new StringFieldValue(next)));
        tree.annotate(node, new Annotation(AnnotationTypes.TOKEN_TYPE,
                                           new IntegerFieldValue(TokenType.ALPHABETIC.getValue())));
        root.add(node);
        output.setSpanTree(tree);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        // empty
    }

    @Override
    public DataType requiredInputType() {
        return DataType.STRING;
    }

    @Override
    public DataType createdOutputType() {
        return null;
    }

    @Override
    public String toString() {
        return "exact";
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
