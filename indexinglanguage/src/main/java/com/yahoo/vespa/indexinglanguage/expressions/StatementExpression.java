// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.TextGenerator;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.ScriptParser;
import com.yahoo.vespa.indexinglanguage.ScriptParserContext;
import com.yahoo.vespa.indexinglanguage.parser.IndexingInput;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An indexing statement consisting of a list of indexing expressions, e.g "input foo | index | attribute".
 *
 * @author Simon Thoresen Hult
 */
public final class StatementExpression extends ExpressionList<Expression> {

    /** The names of the fields consumed by this. */
    private final List<String> inputFields;

    public StatementExpression(Expression... list) {
        this(Arrays.asList(list)); // TODO: Can contain null - necessary ?
    }

    public StatementExpression(Iterable<Expression> list) {
        this(filterList(list), null);
    }

    private StatementExpression(Iterable<Expression> list, Object unused) {
        super(list);
        inputFields = List.copyOf(InputExpression.InputFieldNameExtractor.runOn(this));
    }

    @Override
    public boolean isMutating() {
        return expressions().stream().anyMatch(expression -> expression.isMutating());
    }

    /** Returns the input fields which are (perhaps optionally) consumed by some expression in this statement. */
    public List<String> getInputFields() { return inputFields; }

    @Override
    public StatementExpression convertChildren(ExpressionConverter converter) {
        return new StatementExpression(asList().stream()
                                               .map(converter::convert)
                                               .filter(Objects::nonNull)
                                               .toList());
    }

    @Override
    public DataType setInputType(DataType input, VerificationContext context) {
        super.setInputType(input, context);
        resolveBackwards(context);
        return resolveForwards(context);
    }

    @Override
    public DataType setOutputType(DataType output, VerificationContext context) {
        super.setOutputType(output, context);
        resolveForwards(context);
        return resolveBackwards(context);
    }

    // Result input and output types:
    // Some expressions can only determine their input from their output, and others only their output from
    // their input. Therefore, we try resolving in both directions, which should always meet up to produce
    // uniquely determined inputs and outputs of all expressions.
    // forward:

    /** Resolves types forward and returns the final output, or null if resolution could not progress to the end. */
    private DataType resolveForwards(VerificationContext context) {
        var inputType = getInputType(context);
        for (var expression : expressions()) {
            inputType = expression.setInputType(inputType, context);
            if (inputType == null) break;
        }
        return inputType;
    }

    /** Resolves types backwards and returns the required input, or null if resolution could not progress to the start. */
    private DataType resolveBackwards(VerificationContext context) {
        int i = expressions().size();
        var outputType = getOutputType(context); // A nested statement; output imposed from above
        if (outputType == null) // otherwise the last expression will be an output deciding the type
            outputType = expressions().get(--i).getInputType(context);
        while (--i >= 0)
            outputType = expressions().get(i).setOutputType(outputType, context);
        return outputType;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        if (expressions().isEmpty()) return;

        String outputField = outputFieldName();
        if (outputField != null)
            context.setOutputField(outputField);

        resolveForwards(context);
        resolveBackwards(context);

        for (Expression expression : expressions())
            context.verify(expression);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        for (Expression expression : this) {
            context.execute(expression);
        }
    }

    private String outputFieldName() {
        for (Expression expression : this) {
            if (expression instanceof OutputExpression output)
                return output.getFieldName();
        }
        return null;
    }

    @Override
    public DataType createdOutputType() {
        for (int i = size(); --i >= 0; ) {
            DataType type = get(i).createdOutputType();
            if (type != null) return type;
        }
        return null;
    }

    @Override
    public String toString() {
        return asList().stream().map(Expression::toString).collect(Collectors.joining(" | "));
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
        return newInstance(new ScriptParserContext(linguistics, embedders, Map.of()).setInputStream(new IndexingInput(expression)));
    }

    public static StatementExpression fromString(
            String expression, Linguistics linguistics, Map<String, Embedder> embedders, 
            Map<String, TextGenerator> generators) throws ParseException {
        return newInstance(new ScriptParserContext(linguistics, embedders, generators).setInputStream(new IndexingInput(expression)));
    }

    public static StatementExpression newInstance(ScriptParserContext config) throws ParseException {
        return ScriptParser.parseStatement(config);
    }

    private static List<Expression> filterList(Iterable<Expression> list) {
        List<Expression> filtered = new ArrayList<>();
        for (Expression expression : list) {
            if (expression instanceof StatementExpression statement) {
                filtered.addAll(filterList(statement));
            } else if (expression != null) {
                filtered.add(expression);
            }
        }
        return filtered;
    }

}
