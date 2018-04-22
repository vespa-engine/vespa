// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.expression;

import com.yahoo.vespa.objects.Deserializer;
import com.yahoo.vespa.objects.Identifiable;
import com.yahoo.vespa.objects.ObjectVisitor;
import com.yahoo.vespa.objects.Serializer;

import java.io.Serializable;

/**
 * This is the base class for all expression node types. There is no execution logic implemented in Java, since that all
 * happens in the C++ backend. This class hierarchy is for <b>building</b> the expression tree to pass to the backend.
 *
 * @author baldersheim
 * @author Simon Thoresen
 */
public abstract class ExpressionNode extends Identifiable implements Serializable {

    public static final int classId = registerClass(0x4000 + 40, ExpressionNode.class);

    /**
     * Prepare expression for execution.
     */
    public void prepare() {
        onPrepare();
    }

    /**
     * Execute expression.
     *
     * @return true if successful, false if not.
     */
    public boolean execute() {
        return onExecute();
    }

    /**
     * Give an argument to this expression and store the result.
     *
     * @param arg    Argument to use for expression.
     * @param result Node to contain the result.
     */
    protected void executeIterative(final ResultNode arg, ResultNode result) {
        onArgument(arg, result);
    }

    protected boolean onExecute() {
        throw new RuntimeException("Class " + this.getClass().getName() + " does not implement onExecute().");
    }

    protected void onPrepare() {
        throw new RuntimeException("Class " + this.getClass().getName() + " does not implement onPrepare().");
    }

    protected void onArgument(final ResultNode arg, ResultNode result) {
        throw new RuntimeException("Class " + this.getClass().getName() + " does not implement onArgument().");
    }

    @Override
    protected int onGetClassId() {
        return classId;
    }

    @Override
    protected void onSerialize(Serializer buf) {
        super.onSerialize(buf);
    }

    @Override
    protected void onDeserialize(Deserializer buf) {
        super.onDeserialize(buf);
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
    }

    @Override
    public ExpressionNode clone() {
        return (ExpressionNode)super.clone();
    }

    @Override
    public final boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        if (!equalsExpression((ExpressionNode)obj)) {
            return false;
        }
        return true;
    }

    protected abstract boolean equalsExpression(ExpressionNode obj);

    /**
     * Get the result of this expression.
     *
     * @return the result as a ResultNode.
     */
    abstract public ResultNode getResult();
}
