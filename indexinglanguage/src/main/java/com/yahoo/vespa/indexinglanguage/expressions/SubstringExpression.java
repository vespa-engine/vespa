// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.StringFieldValue;

/**
 * @author Simon Thoresen Hult
 */
public class SubstringExpression extends Expression {

    private final int from;
    private final int to;

    public SubstringExpression(int from, int to) {
        if (from < 0 || to < 0 || to < from) {
            throw new IndexOutOfBoundsException();
        }
        this.from = from;
        this.to = to;
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }

    @Override
    protected void doExecute(ExecutionContext ctx) {
        String input = String.valueOf(ctx.getValue());
        int len = input.length();
        if (from >= len) {
            input = "";
        } else if (to >= len) {
            input = input.substring(from);
        } else {
            input = input.substring(from, to);
        }
        ctx.setValue(new StringFieldValue(input));
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
        return "substring " + from + " " + to;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SubstringExpression)) {
            return false;
        }
        SubstringExpression rhs = (SubstringExpression)obj;
        if (from != rhs.from) {
            return false;
        }
        if (to != rhs.to) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() +
               Integer.valueOf(from).hashCode() +
               Integer.valueOf(to).hashCode();
    }
}
