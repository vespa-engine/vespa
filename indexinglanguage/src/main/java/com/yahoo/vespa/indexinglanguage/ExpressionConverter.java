// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.collections.Pair;
import com.yahoo.vespa.indexinglanguage.expressions.*;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
@SuppressWarnings({ "UnusedDeclaration" })
public abstract class ExpressionConverter implements Cloneable {

    public final Expression convert(Expression exp) {
        if (exp == null) {
            return null;
        }
        if (shouldConvert(exp)) {
            return doConvert(exp);
        }
        if (!(exp instanceof CompositeExpression)) {
            return exp;
        }
        try {
            // The class.getMethod here takes 8% of the cpu time in reading the SSBE application package
            // TODO: Implement double dispatch through visitor instead?
            return (Expression)ExpressionConverter.class.getMethod("innerConvert", exp.getClass()).invoke(this, exp);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new UnsupportedOperationException(exp.getClass().getName(), e);
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            throw t instanceof RuntimeException ? (RuntimeException)t : new RuntimeException(t);
        }
    }

    public Expression innerConvert(ArithmeticExpression exp) {
        return new ArithmeticExpression(convert(exp.getLeftHandSide()),
                                        exp.getOperator(),
                                        convert(exp.getRightHandSide()));
    }

    public Expression innerConvert(CatExpression exp) {
        List<Expression> lst = new LinkedList<>();
        for (Expression innerExp : exp) {
            Expression next = convert(innerExp);
            if (next != null) {
                lst.add(next);
            }
        }
        return new CatExpression(lst);
    }

    public Expression innerConvert(ForEachExpression exp) {
        return new ForEachExpression(convert(exp.getInnerExpression()));
    }

    public Expression innerConvert(GuardExpression exp) {
        return new GuardExpression(convert(exp.getInnerExpression()));
    }

    public Expression innerConvert(IfThenExpression exp) {
        return new IfThenExpression(branch().convert(exp.getLeftHandSide()),
                                    exp.getComparator(),
                                    branch().convert(exp.getRightHandSide()),
                                    branch().convert(exp.getIfTrueExpression()),
                                    branch().convert(exp.getIfFalseExpression()));
    }

    public Expression innerConvert(ParenthesisExpression exp) {
        return new ParenthesisExpression(convert(exp.getInnerExpression()));
    }

    public Expression innerConvert(ScriptExpression exp) {
        List<StatementExpression> lst = new LinkedList<>();
        for (Expression innerExp : exp) {
            StatementExpression next = (StatementExpression)branch().convert(innerExp);
            if (next != null) {
                lst.add(next);
            }
        }
        return new ScriptExpression(lst);
    }

    public Expression innerConvert(SelectInputExpression exp) {
        List<Pair<String, Expression>> cases = new LinkedList<>();
        for (Pair<String, Expression> pair : exp.getCases()) {
            cases.add(new Pair<>(pair.getFirst(), branch().convert(pair.getSecond())));
        }
        return new SelectInputExpression(cases);
    }

    public Expression innerConvert(StatementExpression exp) {
        List<Expression> lst = new LinkedList<>();
        for (Expression innerExp : exp) {
            Expression next = convert(innerExp);
            if (next != null) {
                lst.add(next);
            }
        }
        return new StatementExpression(lst);
    }

    public Expression innerConvert(SwitchExpression exp) {
        Map<String, Expression> cases = new HashMap<>();
        for (Map.Entry<String, Expression> entry : exp.getCases().entrySet()) {
            Expression next = branch().convert(entry.getValue());
            if (next != null) {
                cases.put(entry.getKey(), next);
            }
        }
        return new SwitchExpression(cases, branch().convert(exp.getDefaultExpression()));
    }

    protected ExpressionConverter branch() {
        return this;
    }

    @Override
    @SuppressWarnings("CloneDoesntDeclareCloneNotSupportedException")
    public ExpressionConverter clone() {
        try {
            return (ExpressionConverter)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    protected abstract boolean shouldConvert(Expression exp);

    protected abstract Expression doConvert(Expression exp);
}
