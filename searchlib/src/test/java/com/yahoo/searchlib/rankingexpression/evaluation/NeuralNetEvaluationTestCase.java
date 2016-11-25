// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.tensor.MapTensor;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
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
        String input = "{ {x:1}:0, {x:2}:1 }";

        String firstLayerWeights = "{ {x:1,h:1}:1, {x:1,h:2}:1, {x:2,h:1}:1, {x:2,h:2}:1 }";
        String firstLayerBias = "{ {h:1}:-0.5, {h:2}:-1.5 }";
        String firstLayerInput = "sum(" + input + "*" + firstLayerWeights + ", x) + " + firstLayerBias;
        String firstLayerOutput = "min(1.0, max(0.0, 0.5 + " + firstLayerInput + "))"; // non-linearity, "poor man's sigmoid"
        assertEvaluates("{ {h:1}:1.0, {h:2}:0.0} }", firstLayerOutput);
        String secondLayerWeights = "{ {h:1,y:1}:1, {h:2,y:1}:-1 }";
        String secondLayerBias = "{ {y:1}:-0.5 }";
        String secondLayerInput = "sum(" + firstLayerOutput + "*" + secondLayerWeights + ", h) + " + secondLayerBias;
        String secondLayerOutput = "min(1.0, max(0.0, 0.5 + " + secondLayerInput + "))"; // non-linearity, "poor man's sigmoid"
        assertEvaluates("{ {y:1}:1 }", secondLayerOutput);
    }

    private RankingExpression assertEvaluates(String tensorValue, String expressionString) {
        return assertEvaluates(new TensorValue(MapTensor.from(tensorValue)), expressionString, new MapContext());
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
