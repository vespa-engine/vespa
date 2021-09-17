// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.ScriptParser;
import com.yahoo.vespa.indexinglanguage.ScriptParserContext;
import com.yahoo.vespa.indexinglanguage.parser.IndexingInput;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public final class StatementExpression extends ExpressionList<Expression> {

    /** The type of the output created by this statement, or null if no output */
    private final DataType outputType;

    public StatementExpression(Expression... lst) {
        this(Arrays.asList(lst));
    }

    public StatementExpression(Iterable<Expression> lst) {
        this(filterList(lst), null);
    }

    private StatementExpression(Iterable<Expression> list, Object unused) {
        super(list, resolveInputType(list));
        outputType = resolveOutputType(list);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setOutputType(createdOutputType());
        for (Expression exp : this) {
            context.execute(exp);
        }
    }

    @Override
    protected void doVerify(VerificationContext context) {
        for (Expression exp : this) {
            context.execute(exp);
        }
    }

    private static DataType resolveInputType(Iterable<Expression> lst) {
        for (Expression exp : lst) {
            DataType type = exp.requiredInputType();
            if (type != null) {
                return type;
            }

            type = exp.createdOutputType();
            if (type != null) {
                return null;
            }
        }
        return null;
    }

    private static DataType resolveOutputType(Iterable<Expression> expressions) {
        DataType lastOutput = null;
        for (var expression : expressions) {
            DataType output = expression.createdOutputType();
            if (output != null)
                lastOutput = output;
        }
        return lastOutput;
    }

    @Override
    public DataType createdOutputType() { return outputType; }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        for (Iterator<Expression> it = iterator(); it.hasNext();) {
            ret.append(it.next());
            if (it.hasNext()) {
                ret.append(" | ");
            }
        }
        return ret.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) && obj instanceof StatementExpression;
    }

    /** Creates an expression with simple lingustics for testing */
    public static StatementExpression fromString(String expression) throws ParseException {
        return fromString(expression, new SimpleLinguistics());
    }

    public static StatementExpression fromString(String expression, Linguistics linguistics) throws ParseException {
        return newInstance(new ScriptParserContext(linguistics).setInputStream(new IndexingInput(expression)));
    }

    public static StatementExpression newInstance(ScriptParserContext config) throws ParseException {
        return ScriptParser.parseStatement(config);
    }

    private static List<Expression> filterList(Iterable<Expression> lst) {
        List<Expression> ret = new LinkedList<>();
        for (Expression exp : lst) {
            if (exp instanceof StatementExpression) {
                ret.addAll(filterList((StatementExpression)exp));
            } else if (exp != null) {
                ret.add(exp);
            }
        }
        return ret;
    }
}
