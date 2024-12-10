// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;

/**
 * Returns the current execution value, that is the value passed to this expression.
 * Referring to this explicitly is useful e.g to concatenate it to some other string:
 * ... | input foo . " " . _ | ...
 *
 * @author bratseth
 */
public final class ExecutionValueExpression extends Expression {

    @Override
    protected void doExecute(ExecutionContext context) {
        // Noop: Set the output execution value to the current execution value
    }

    @Override
    public DataType createdOutputType() { return UnresolvedDataType.INSTANCE; }

    @Override
    public String toString() { return "_"; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ExecutionValueExpression;
    }

    @Override
    public int hashCode() {
        return 9875876;
    }

}
