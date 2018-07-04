// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.ScriptParser;
import com.yahoo.vespa.indexinglanguage.ScriptParserContext;
import com.yahoo.vespa.indexinglanguage.parser.IndexingInput;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author Simon Thoresen Hult
 */
public class ScriptExpression extends ExpressionList<StatementExpression> {

    public ScriptExpression() {
        super();
    }

    public ScriptExpression(StatementExpression... lst) {
        super(Arrays.asList(lst));
    }

    public ScriptExpression(Collection<? extends StatementExpression> lst) {
        super(lst);
    }

    @Override
    protected void doExecute(ExecutionContext ctx) {
        FieldValue input = ctx.getValue();
        for (Expression exp : this) {
            ctx.setValue(input).execute(exp);
        }
        ctx.setValue(input);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getValue();
        for (Expression exp : this) {
            context.setValue(input).execute(exp);
        }
        context.setValue(input);
    }

    @Override
    public DataType requiredInputType() {
        DataType prev = null;
        for (Expression exp : this) {
            DataType next = exp.requiredInputType();
            if (prev == null) {
                prev = next;
            } else if (next != null && !prev.isAssignableFrom(next)) {
                throw new VerificationException(this, "Statements require conflicting input types, " +
                                                      prev.getName() + " vs " + next.getName() + ".");
            }
        }
        return prev;
    }

    @Override
    public DataType createdOutputType() {
        return null;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append("{ ");
        for (Iterator<StatementExpression> it = iterator(); it.hasNext();) {
            ret.append(it.next()).append(";");
            if (it.hasNext()) {
                ret.append(" ");
            }
        }
        ret.append(" }");
        return ret.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) && obj instanceof ScriptExpression;
    }

    /** Creates an expression with simple lingustics for testing */
    public static ScriptExpression fromString(String expression) throws ParseException {
        return fromString(expression, new SimpleLinguistics());
    }

    public static ScriptExpression fromString(String expression, Linguistics linguistics) throws ParseException {
        return newInstance(new ScriptParserContext(linguistics).setInputStream(new IndexingInput(expression)));
    }

    public static ScriptExpression newInstance(ScriptParserContext config) throws ParseException {
        return ScriptParser.parseScript(config);
    }
}
