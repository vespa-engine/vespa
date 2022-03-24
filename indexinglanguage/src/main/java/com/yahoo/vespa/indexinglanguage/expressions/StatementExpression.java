// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.ScriptParser;
import com.yahoo.vespa.indexinglanguage.ScriptParserContext;
import com.yahoo.vespa.indexinglanguage.parser.IndexingInput;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
public final class StatementExpression extends ExpressionList<Expression> {

    /** The name of the (last) output field tthis statement will write to, or null if none */
    private String outputField;

    public StatementExpression(Expression... lst) {
        this(Arrays.asList(lst));
    }

    public StatementExpression(Iterable<Expression> lst) {
        this(filterList(lst), null);
    }

    private StatementExpression(Iterable<Expression> list, Object unused) {
        super(list, resolveInputType(list));
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        for (Expression exp : this) {
            context.execute(exp);
        }
    }

    @Override
    protected void doVerify(VerificationContext context) {
        for (Expression expression : this) {
            if (expression instanceof OutputExpression)
                outputField = ((OutputExpression)expression).getFieldName();
        }
        if (outputField != null)
            context.setOutputField(outputField);
        for (Expression expression : this)
            context.execute(expression);
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
        return fromString(expression, new SimpleLinguistics(), Embedder.throwsOnUse.asMap());
    }

    public static StatementExpression fromString(String expression, Linguistics linguistics, Map<String, Embedder> embedders) throws ParseException {
        return newInstance(new ScriptParserContext(linguistics, embedders).setInputStream(new IndexingInput(expression)));
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
