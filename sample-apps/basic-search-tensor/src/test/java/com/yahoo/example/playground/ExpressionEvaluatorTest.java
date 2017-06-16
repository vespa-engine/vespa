// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.example.playground;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.Tensor;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExpressionEvaluatorTest {

    @Test
    public void requireThatTensorExpressionCanBeEvaluated() throws ParseException {
        MapContext context = new MapContext();
        context.put("t1", new TensorValue(Tensor.from("tensor(x{}):{{x:0}:1,{x:1}:2}")));
        context.put("t2", new TensorValue(Tensor.from("tensor(x{}):{{x:0}:2,{x:1}:3}")));

        Value v = ExpressionEvaluator.evaluate("t1 * t2", context);

        assertTrue(v instanceof TensorValue);
        assertEquals("tensor(x{})", ((TensorValue)v).asTensor().type().toString());
        assertEquals("{{x:0}:2.0,{x:1}:6.0}", v.toString());
    }

    @Test
    public void requireThatNonTensorExpressionsCanBeEvaluated() throws ParseException {
        MapContext context = new MapContext();
        context.put("d1", new DoubleValue(3.0));
        context.put("d2", new DoubleValue(4.0));

        Value v = ExpressionEvaluator.evaluate("d1 * d2", context);

        assertTrue(v instanceof DoubleValue);
        assertEquals("12.0", v.toString());
    }

    @Test
    public void requireThatJsonFormatCanBeEvaluated() throws IOException {
        String evaluateJson = String.join("\n",
                "{",
                "   \"expression\": \"t1 * t2\",",
                "   \"arguments\": [",
                "       {",
                "           \"name\": \"t1\",",
                "           \"type\": \"tensor(x[])\",",
                "           \"value\": \"{{x:0}:1,{x:1}:2}\"",
                "       },",
                "       {",
                "           \"name\": \"t2\",",
                "           \"type\": \"tensor(x[])\",",
                "           \"value\": \"{{x:0}:2,{x:1}:3}\"",
                "       }",
                "   ]",
                "}");
        String expectedJson = String.join("\n",
                "{",
                "   \"type\": \"tensor(x[])\",",
                "   \"value\": {",
                "       \"literal\": \"{{x:0}:2.0,{x:1}:6.0}\",",
                "       \"cells\": [{\"address\":{\"x\":\"0\"},\"value\":2.0},{\"address\":{\"x\":\"1\"},\"value\":6.0}]",
                "   }",
                "}");

        String resultJson = ExpressionEvaluator.evaluate(evaluateJson);

        ObjectMapper m = new ObjectMapper();
        JsonNode result = m.readTree(resultJson);
        JsonNode expected = m.readTree(expectedJson);

        assertEquals(expected.get("type").asText(), result.get("type").asText());
        assertEquals(expected.get("value").get("literal").asText(), result.get("value").get("literal").asText());
    }

}



