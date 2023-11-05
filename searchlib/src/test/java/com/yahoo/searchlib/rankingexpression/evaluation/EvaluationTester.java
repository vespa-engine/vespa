// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class EvaluationTester {

    private MapContext defaultContext;

    public EvaluationTester() {
        Map<String, Value> bindings = new HashMap<>();
        bindings.put("zero", DoubleValue.frozen(0d));
        bindings.put("one", DoubleValue.frozen(1d));
        bindings.put("one_half", DoubleValue.frozen(0.5d));
        bindings.put("a_quarter", DoubleValue.frozen(0.25d));
        bindings.put("foo", StringValue.frozen("foo"));
        defaultContext = new MapContext(bindings);
    }

    public RankingExpression assertEvaluates(String expectedTensor, String expressionString, String ... tensorArguments) {
        assertEvaluates(expectedTensor, expressionString, false, tensorArguments);
        return assertEvaluates(expectedTensor, expressionString, true, tensorArguments);
    }

    // TODO: Test both bound and unbound indexed
    public RankingExpression assertEvaluates(String expectedTensor, String expressionString, boolean mappedTensors,
                                             String ... tensorArgumentStrings) {
        MapContext context = defaultContext.thawedCopy();
        int argumentIndex = 0;
        for (String argumentString : tensorArgumentStrings) {
            Tensor argument;
            if (argumentString.startsWith("tensor")) // explicitly decided type
                argument = Tensor.from(argumentString);
            else // use mappedTensors+dimensions in tensor to decide type
                argument = Tensor.from(typeFrom(argumentString, mappedTensors), argumentString);
            context.put("tensor" + (argumentIndex++), new TensorValue(argument));
        }
        return assertEvaluates(new TensorValue(Tensor.from(expectedTensor)), expressionString, context,
                               mappedTensors ? "Mapped tensors" : "Indexed tensors");
    }

    public RankingExpression assertEvaluates(Value value, String expressionString) {
        return assertEvaluates(value, expressionString, defaultContext, "");
    }

    public RankingExpression assertEvaluates(double value, String expressionString) {
        return assertEvaluates(value, expressionString, defaultContext);
    }

    public RankingExpression assertEvaluates(boolean value, String expressionString) {
        return assertEvaluates(value, expressionString, defaultContext);
    }

    public RankingExpression assertEvaluates(double value, String expressionString, Context context) {
        return assertEvaluates(new DoubleValue(value), expressionString, context, "");
    }

    public RankingExpression assertEvaluates(boolean value, String expressionString, Context context) {
        return assertEvaluates(new BooleanValue(value), expressionString, context, "");
    }

    public RankingExpression assertEvaluates(Value value, String expressionString, Context context, String explanation) {
        try {
            RankingExpression expression = new RankingExpression(expressionString);
            if ( ! explanation.isEmpty())
                explanation = explanation + ": ";
            var result = expression.evaluate(context);
            assertEquals(explanation + expression, value, result);
            assertEquals(value.type().valueType(), result.type().valueType());
            var root = expression.getRoot();
            String asString = root.toString();
            try {
                expression = new RankingExpression(asString);
                result = expression.evaluate(context);
                assertEquals(explanation + expressionString + " -> " + asString, value, result);
                assertEquals(value.type().valueType(), result.type().valueType());
            } catch (Exception e) {
                System.err.println("toString() failure, " + expressionString + " -> " + asString);
                System.err.println("root: " + root.getClass());
                if (root instanceof TensorFunctionNode tfn) {
                    System.err.println("root func: " + tfn.function().getClass());
                }
                throw new RuntimeException(e);
            }
            return expression;
        }
        catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    /** Create a tensor type from a tensor string which may or may not contain type info */
    private TensorType typeFrom(String argument, boolean mappedTensors) {
        Tensor tensor = Tensor.from(argument); // Create tensor just to get the dimensions
        if (mappedTensors) {
            return tensor.type(); // implicit type is mapped by default
        }
        else { // convert to indexed
            TensorType.Builder builder = new TensorType.Builder();
            for (TensorType.Dimension dimension : tensor.type().dimensions())
                builder.indexed(dimension.name());
            return builder.build();
        }
    }

}
