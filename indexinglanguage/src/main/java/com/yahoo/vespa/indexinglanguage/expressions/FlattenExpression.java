// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.annotation.*;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;

import java.util.*;

/**
 * @author Simon Thoresen Hult
 */
public class FlattenExpression extends Expression {

    @Override
    protected void doExecute(ExecutionContext ctx) {
        StringFieldValue input = (StringFieldValue)ctx.getValue();
        SpanTree tree = input.getSpanTree(SpanTrees.LINGUISTICS);
        Map<Integer, List<String>> map = new HashMap<>();
        for (Annotation anno : tree) {
            SpanNode span = anno.getSpanNode();
            if (span == null) {
                continue;
            }
            if (anno.getType() != AnnotationTypes.TERM) {
                continue;
            }
            FieldValue val = anno.getFieldValue();
            String str;
            if (val instanceof StringFieldValue) {
                str = ((StringFieldValue)val).getString();
            } else {
                str = input.getString().substring(span.getFrom(), span.getTo());
            }
            Integer pos = span.getTo();
            List<String> entry = map.get(pos);
            if (entry == null) {
                entry = new LinkedList<>();
                map.put(pos, entry);
            }
            entry.add(str);
        }
        String inputVal = String.valueOf(input);
        StringBuilder output = new StringBuilder();
        for (int i = 0, len = inputVal.length(); i <= len; ++i) {
            List<String> entry = map.get(i);
            if (entry != null) {
                Collections.sort(entry);
                output.append(entry);
            }
            if (i < len) {
                output.append(inputVal.charAt(i));
            }
        }
        ctx.setValue(new StringFieldValue(output.toString()));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValue(createdOutputType());
    }

    @Override
    public DataType requiredInputType() {
        return DataType.STRING;
    }

    @Override
    public DataType createdOutputType() {
        return DataType.STRING;
    }

    @Override
    public String toString() {
        return "flatten";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FlattenExpression;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
