// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;

import java.io.PrintStream;

/**
 * @author Simon Thoresen Hult
 */
public class EchoExpression extends Expression {

    private final PrintStream out;

    public EchoExpression() {
        this(System.out);
    }

    public EchoExpression(PrintStream out) {
        this.out = out;
    }

    public PrintStream getOutputStream() {
        return out;
    }

    @Override
    protected void doExecute(ExecutionContext ctx) {
        out.println(String.valueOf(ctx.getValue()));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        // empty
    }

    @Override
    public DataType requiredInputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public DataType createdOutputType() {
        return null;
    }

    @Override
    public String toString() {
        return "echo";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof EchoExpression)) {
            return false;
        }
        EchoExpression rhs = (EchoExpression)obj;
        if (out != rhs.out) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + out.hashCode();
    }
}
