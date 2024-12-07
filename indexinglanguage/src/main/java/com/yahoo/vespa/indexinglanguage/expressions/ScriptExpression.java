// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.Generator;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.ScriptParser;
import com.yahoo.vespa.indexinglanguage.ScriptParserContext;
import com.yahoo.vespa.indexinglanguage.parser.IndexingInput;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
public final class ScriptExpression extends ExpressionList<StatementExpression> {

    public ScriptExpression() {
        this(List.of());
    }

    public ScriptExpression(StatementExpression... statements) {
        this(List.of(statements));
    }

    public ScriptExpression(Collection<? extends StatementExpression> statements) {
        super(statements, resolveInputType(statements));
    }

    @Override
    public ScriptExpression convertChildren(ExpressionConverter converter) {
        return new ScriptExpression(asList().stream()
                                            .map(child -> (StatementExpression)converter.branch().convert(child))
                                            .filter(Objects::nonNull)
                                            .toList());
    }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);
        DataType currentOutput = null;
        for (var expression : expressions())
            currentOutput = expression.setInputType(inputType, context);
        return currentOutput != null ? currentOutput : getOutputType(context);
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(outputType, context);
        DataType currentInput = null;
        for (var expression : expressions())
            currentInput = expression.setOutputType(outputType, context);
        return currentInput != null ? currentInput : getInputType(context);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getCurrentType();
        for (Expression exp : this)
            context.setCurrentType(input).verify(exp);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getCurrentValue();
        for (StatementExpression statement : this) {
            if (context.isComplete() ||
                (statement.getInputFields().isEmpty() || containsAtLeastOneInputFrom(statement.getInputFields(), context))) {
                context.setCurrentValue(input);
                context.execute(statement);
            }
        }
        context.setCurrentValue(input);
    }

    private boolean containsAtLeastOneInputFrom(List<String> inputFields, ExecutionContext context) {
        for (String inputField : inputFields)
            if (context.getFieldValue(inputField) != null)
                return true;
        return false;
    }

    private static DataType resolveInputType(Collection<? extends StatementExpression> list) {
        DataType prev = null;
        for (Expression exp : list) {
            DataType next = exp.requiredInputType();
            if (prev == null) {
                prev = next;
            } else if (next != null && !prev.isAssignableFrom(next)) {
                throw new VerificationException(ScriptExpression.class, "Statements require conflicting input types, " +
                                                                        prev.getName() + " vs " + next.getName());
            }
        }
        return prev;
    }

    @Override
    public DataType createdOutputType() {
        var expressions = asList();
        if (expressions.isEmpty()) return null;
        return (expressions.get(expressions.size() - 1)).createdOutputType();
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
        return newInstance(new ScriptParserContext(linguistics, embedders, Map.of()).setInputStream(new IndexingInput(expression)));
    }

    public static Expression fromString(
            String expression, Linguistics linguistics, Map<String, Embedder> embedders, 
            Map<String, Generator> generators) throws ParseException {
        return newInstance(new ScriptParserContext(linguistics, embedders, generators).setInputStream(new IndexingInput(expression)));
    }

    public static ScriptExpression newInstance(ScriptParserContext config) throws ParseException {
        return ScriptParser.parseScript(config);
    }

}
