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
        MapContext context = defaultContext.thawedCopy();
        int argumentIndex = 0;
        for (String tensorArgument : tensorArguments)
            context.put("tensor" + (argumentIndex++), new TensorValue(Tensor.from(typeFrom(tensorArgument), tensorArgument)));
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
    private TensorType typeFrom(String argument) {
        // Tensor already have logic for this ...
        Tensor tensor = Tensor.from(argument);
        return tensor.type();
    }

}
