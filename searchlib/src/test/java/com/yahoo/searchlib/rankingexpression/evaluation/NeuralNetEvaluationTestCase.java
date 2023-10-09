// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.Tensor;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Tests evaluating neural nets expressed as tensors
 *
 * @author bratseth
 */
public class NeuralNetEvaluationTestCase {

    /** "XOR" neural network, separate expression per layer */
    @Test
    public void testPerLayerExpression() {
        String input = "{ {x:1}:0, {x:2}:1 }"; // tensor0
        String firstLayerWeights = "{ {x:1,h:1}:1, {x:1,h:2}:1, {x:2,h:1}:1, {x:2,h:2}:1 }"; // tensor1
        String firstLayerBias = "{ {h:1}:-0.5, {h:2}:-1.5 }"; // tensor2
        String firstLayerInput = "sum(tensor0 * tensor1, x) + tensor2";
        String firstLayerOutput = "min(1.0, max(0.0, 0.5 + " + firstLayerInput + "))"; // non-linearity, "poor man's sigmoid"
        assertEvaluates("{ {h:1}:1.0, {h:2}:0.0} }", firstLayerOutput, input, firstLayerWeights, firstLayerBias);
        String secondLayerWeights = "{ {h:1,y:1}:1, {h:2,y:1}:-1 }"; // tensor3
        String secondLayerBias = "{ {y:1}:-0.5 }"; // tensor4
        String secondLayerInput = "sum(" + firstLayerOutput + "* tensor3, h) + tensor4";
        String secondLayerOutput = "min(1.0, max(0.0, 0.5 + " + secondLayerInput + "))"; // non-linearity, "poor man's sigmoid"
        assertEvaluates("{ {y:1}:1 }", secondLayerOutput, input, firstLayerWeights, firstLayerBias, secondLayerWeights, secondLayerBias);
    }

    private RankingExpression assertEvaluates(String expectedTensor, String expressionString, String ... tensorArguments) {
        MapContext context = new MapContext();
        int argumentIndex = 0;
        for (String tensorArgument : tensorArguments)
            context.put("tensor" + (argumentIndex++), new TensorValue(Tensor.from(tensorArgument)));
        return assertEvaluates(new TensorValue(Tensor.from(expectedTensor)), expressionString, context);
    }

    private RankingExpression assertEvaluates(Value value, String expressionString, Context context) {
        try {
            RankingExpression expression = new RankingExpression(expressionString);
            assertEquals(expression.toString(), value, expression.evaluate(context));
            return expression;
        }
        catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

}
