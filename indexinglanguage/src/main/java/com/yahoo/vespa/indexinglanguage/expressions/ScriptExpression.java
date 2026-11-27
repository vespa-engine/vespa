// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Chunker;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.FieldGenerator;
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
        super(statements);
    }

    @Override
    public ScriptExpression convertChildren(ExpressionConverter converter) {
        return new ScriptExpression(asList().stream()
                                            .map(child -> (StatementExpression)converter.branch().convert(child))
                                            .filter(Objects::nonNull)
                                            .toList());
    }

    @Override
    public boolean isMutating() {
        var expressions = asList();
        if (expressions.isEmpty()) return false;
        return (expressions.get(expressions.size() - 1)).isMutating();
    }

    @Override
    public boolean requiresInput() {
        return expressions().stream().anyMatch(statement -> statement.requiresInput());
    }

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        verifyMultipartInputsAreConsumed();
        super.setInputType(inputType, context);
        DataType currentOutput = null;
        for (var expression : expressions())
            currentOutput = expression.setInputType(inputType, context);
        return currentOutput != null ? currentOutput : getOutputType(context);
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        verifyMultipartInputsAreConsumed();
        super.setOutputType(outputType, context);
        DataType currentInput = null;
        // The output of the last statement is used
        if (! expressions().isEmpty())
            currentInput = expressions().get(expressions().size() - 1).setOutputType(outputType, context);
        return currentInput != null ? currentInput : getInputType(context);
    }

    @Override
    protected void doResolve(TypeContext context) {
        verifyMultipartInputsAreConsumed();
        for (Expression exp : this)
            context.resolve(exp);
    }

    /** A script discards the value of each statement, so a multipart input which gives that value is never consumed. */
    private void verifyMultipartInputsAreConsumed() {
        for (var statement : expressions()) {
            MultipartExpression multipart = multipartGivingValueOf(statement);
            if (multipart != null)
                throw new VerificationException(multipart, "A multipart input must be followed by an expression " +
                                                            "which consumes it, such as embed");
        }
    }

    /** Returns the multipart input whose value becomes the value of the given expression, or null if there is none. */
    private static MultipartExpression multipartGivingValueOf(Expression expression) {
        if (expression instanceof MultipartExpression multipart)
            return multipart;
        if (expression instanceof ParenthesisExpression parenthesis)
            return multipartGivingValueOf(parenthesis.getInnerExpression());
        if (expression instanceof StatementExpression statement && ! statement.expressions().isEmpty())
            return multipartGivingValueOf(statement.expressions().get(statement.expressions().size() - 1));
        if (expression instanceof ChoiceExpression choice) {
            for (var alternative : choice.expressions()) {
                MultipartExpression multipart = multipartGivingValueOf(alternative);
                if (multipart != null) return multipart;
            }
        }
        return null;
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

    /** Creates an expression with simple linguistics for testing */
    public static ScriptExpression fromString(String expression) throws ParseException {
        return fromString(expression, new SimpleLinguistics(), Map.of(), Embedder.throwsOnUse.asMap(), Map.of());
    }

    public static ScriptExpression fromString(String expression,
                                              Linguistics linguistics,
                                              Map<String, Chunker> chunkers,
                                              Map<String, Embedder> embedders,
                                              Map<String, FieldGenerator> generators) throws ParseException {
        return newInstance(new ScriptParserContext(linguistics, chunkers, embedders, generators).setInputStream(new IndexingInput(expression)));
    }

    public static ScriptExpression newInstance(ScriptParserContext config) throws ParseException {
        return ScriptParser.parseScript(config);
    }

}
