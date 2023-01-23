// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.ScriptParser;
import com.yahoo.vespa.indexinglanguage.ScriptParserContext;
import com.yahoo.vespa.indexinglanguage.parser.IndexingInput;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
public final class ScriptExpression extends ExpressionList<StatementExpression> {

    public ScriptExpression() {
        this(Collections.emptyList());
    }

    public ScriptExpression(StatementExpression... lst) {
        this(Arrays.asList(lst));
    }

    public ScriptExpression(Collection<? extends StatementExpression> lst) {
        super(lst, resolveInputType(lst));
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getValue();
        for (StatementExpression statement : this) {
            if (context.isComplete() ||
                (statement.getInputFields().isEmpty() || containsAtLeastOneInputFrom(statement.getInputFields(), context))) {
                context.setValue(input).execute(statement);
            }
        }
        context.setValue(input);
    }

    private boolean containsAtLeastOneInputFrom(List<String> inputFields, ExecutionContext context) {
        for (String inputField : inputFields)
            if (context.getInputValue(inputField) != null)
                return true;
        return false;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getValueType();
        for (Expression exp : this) {
            context.setValueType(input).execute(exp);
        }
        context.setValueType(input);
    }

    private static DataType resolveInputType(Collection<? extends StatementExpression> list) {
        DataType prev = null;
        for (Expression exp : list) {
            DataType next = exp.requiredInputType();
            if (prev == null) {
                prev = next;
            } else if (next != null && !prev.isAssignableFrom(next)) {
                throw new VerificationException(ScriptExpression.class, "Statements require conflicting input types, " +
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
        return fromString(expression, new SimpleLinguistics(), Embedder.throwsOnUse.asMap());
    }

    public static ScriptExpression fromString(String expression, Linguistics linguistics, Map<String, Embedder> embedders) throws ParseException {
        return newInstance(new ScriptParserContext(linguistics, embedders).setInputStream(new IndexingInput(expression)));
    }

    public static ScriptExpression newInstance(ScriptParserContext config) throws ParseException {
        return ScriptParser.parseScript(config);
    }

}
