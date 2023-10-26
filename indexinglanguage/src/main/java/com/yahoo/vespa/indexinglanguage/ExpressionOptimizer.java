// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.vespa.indexinglanguage.expressions.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Optimizes expressions by removing expressions that have no effect.
 * Typical examples are statements that come before a statement that
 * generates a new execution value without regard for the existing one.
 */
public class ExpressionOptimizer extends ExpressionConverter {

    @Override
    protected boolean shouldConvert(Expression exp) {
        return exp instanceof StatementExpression;
    }

    @Override
    protected Expression doConvert(Expression exp) {
        return optimizeStatement((StatementExpression) exp);
    }

    private Expression optimizeStatement(StatementExpression statement) {
        List<Expression> expressionList = new ArrayList<>();
        List<Expression> candidateList = new ArrayList<>();
        for (Expression exp : statement) {
            if (ignoresInput(exp)) {
                candidateList.clear();
            }
            candidateList.add(convert(exp));
            if (hasSideEffect(exp)) {
                expressionList.addAll(candidateList);
                candidateList.clear();
            }
        }
        expressionList.addAll(candidateList);
        return new StatementExpression(expressionList);
    }

    static boolean hasSideEffect(Expression exp) {
        HasSideEffectVisitor visitor = new HasSideEffectVisitor();
        visitor.visit(exp);
        return visitor.hasSideEffect;
    }

    private static class HasSideEffectVisitor extends ExpressionVisitor {

        boolean hasSideEffect = false;

        @Override
        protected void doVisit(Expression exp) {
            hasSideEffect |= exp instanceof OutputExpression ||
                             exp instanceof SetVarExpression ||
                             exp instanceof EchoExpression;
        }
    }

    static boolean ignoresInput(Expression exp) {
        if (exp instanceof SwitchExpression || exp instanceof ScriptExpression) {
            return false;  // Switch and script never ignores input.
        }
        if (exp instanceof CompositeExpression) {
            return new IgnoresInputVisitor().ignoresInput(exp);
        }
        if (exp instanceof RandomExpression) {
            return ((RandomExpression)exp).getMaxValue() != null;
        }
        return exp instanceof InputExpression ||
               exp instanceof NowExpression ||
               exp instanceof ConstantExpression ||
               exp instanceof HostNameExpression ||
               exp instanceof GetVarExpression;
    }

    private static class IgnoresInputVisitor extends ExpressionConverter {
        private boolean ignoresInput = true;
        private Expression root = null;

        public boolean ignoresInput(Expression root) {
            this.root = root;
            convert(root);
            return ignoresInput;
        }

        @Override
        protected boolean shouldConvert(Expression exp) {
            if (!ignoresInput) {
                return true;  // Answer found, skip ahead
            }
            if (exp == root) {
                return false;  // Skip root, check children
            }
            if (exp instanceof StatementExpression) {
                for (Expression expression : (StatementExpression) exp) {
                    if (ExpressionOptimizer.ignoresInput(expression)) {
                        return true;  // Skip children
                    }
                }
                ignoresInput = false;
                return true;  // Answer found, skip children
            }
            ignoresInput &= ExpressionOptimizer.ignoresInput(exp);
            return true;  // Children already checked. Skip them.
        }

        @Override
        protected Expression doConvert(Expression exp) {
            return exp;
        }

    }

}
