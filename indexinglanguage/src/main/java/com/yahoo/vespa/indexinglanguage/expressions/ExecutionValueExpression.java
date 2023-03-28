package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.FieldPath;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Returns the current execution value, that is the value passed to this expression.
 * Referring to this explicitly is useful e.g to concatenate it to some other string:
 * ... | input foo . " " . _ | ...
 *
 * @author bratseth
 */
public final class ExecutionValueExpression extends Expression {

    public ExecutionValueExpression() {
        super(null);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        // Noop: Set the output execution value to the current execution value
    }

    @Override
    protected void doVerify(VerificationContext context) {}

    @Override
    public DataType createdOutputType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    public String toString() {
        return "_";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ExecutionValueExpression;
    }

    @Override
    public int hashCode() {
        return 9875876;
    }

}
