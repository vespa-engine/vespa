// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;

/**
 * @author Simon Thoresen Hult
 */
public abstract class CompositeExpression extends Expression {

    protected CompositeExpression(DataType inputType) {
        super(inputType);
    }

    protected static String toScriptBlock(Expression exp) {
        if (exp instanceof ScriptExpression) {
            return exp.toString();
        }
        if (exp instanceof StatementExpression) {
            return new ScriptExpression((StatementExpression)exp).toString();
        }
        return new ScriptExpression(new StatementExpression(exp)).toString();
    }

}
