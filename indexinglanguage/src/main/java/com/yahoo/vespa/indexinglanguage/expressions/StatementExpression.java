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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public class StatementExpression extends ExpressionList<Expression> {

    public StatementExpression(Expression... lst) {
        this(Arrays.asList(lst));
    }

    public StatementExpression(Iterable<Expression> lst) {
        super(filterList(lst));
    }

    @Override
    protected void doExecute(ExecutionContext ctx) {
        for (Expression exp : this) {
            ctx.execute(exp);
        }
    }

    @Override
    protected void doVerify(VerificationContext context) {
        for (Expression exp : this) {
            context.execute(exp);
        }
    }

    @Override
    public DataType requiredInputType() {
        for (Expression exp : this) {
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

    @Override
    public DataType createdOutputType() {
        for (int i = size(); --i >= 0; ) {
            DataType type = get(i).createdOutputType();
            if (type != null) {
                return type;
            }
        }
        return null;
    }

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
