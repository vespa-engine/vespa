package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class EvaluationTester {

    private double tolerance = 0.000001;
    private MapContext defaultContext;

    public EvaluationTester() {
        Map<String, Value> bindings = new HashMap<String, Value>();
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

    public RankingExpression assertEvaluates(String expectedTensor, String expressionString, boolean mappedTensors, 
                                             String ... tensorArgumentStrings) {
        MapContext context = defaultContext.thawedCopy();
        int argumentIndex = 0;
        for (String argumentString : tensorArgumentStrings) {
            Tensor argument;
            if (argumentString.startsWith("tensor(")) // explicitly decided type
                argument = Tensor.from(argumentString);
            else // use mappedTensors+dimensions in tensor to decide type
                argument = Tensor.from(typeFrom(argumentString, mappedTensors), argumentString);
            context.put("tensor" + (argumentIndex++), new TensorValue(argument));
        }
        return assertEvaluates(new TensorValue(Tensor.from(expectedTensor)), expressionString, context);
    }

    public RankingExpression assertEvaluates(Value value, String expressionString) {
        return assertEvaluates(value, expressionString, defaultContext);
    }

    public RankingExpression assertEvaluates(double value, String expressionString) {
        return assertEvaluates(value, expressionString, defaultContext);
    }

    public RankingExpression assertEvaluates(double value, String expressionString, Context context) {
        return assertEvaluates(new DoubleValue(value), expressionString, context);
    }

    public RankingExpression assertEvaluates(Value value, String expressionString, Context context) {
        try {
            RankingExpression expression = new RankingExpression(expressionString);
            assertEquals(expression.toString(), value, expression.evaluate(context));
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
                builder.indexedUnbound(dimension.name());
            return builder.build();
        }
    }

}
