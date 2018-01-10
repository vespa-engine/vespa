// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.tensor.Tensor;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class RankingExpressionWithTensorFlowTestCase {

    private final String modelDirectory = "src/test/integration/tensorflow/mnist_softmax/saved";
    private final String vespaExpression = "join(rename(reduce(join(Placeholder, rename(constant(Variable), (d0, d1), (d1, d3)), f(a,b)(a * b)), sum, d1), d3, d1), rename(constant(Variable_1), d0, d1), f(a,b)(a + b))";

    @Test
    public void testMinimalTensorFlowReference() throws ParseException {
        RankProfileSearchFixture search = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                "    first-phase {\n" +
                "      expression: tensorflow('" + modelDirectory + "')" +
                "    }\n" +
                "  }");
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");

        Tensor variable_1 = search.rankProfile("my_profile").getConstants().get("Variable_1").asTensor();
        assertNotNull("Variable_1 is imported", variable_1);
        assertEquals(10, variable_1.size());

        Tensor variable = search.rankProfile("my_profile").getConstants().get("Variable").asTensor();
        assertNotNull("Variable is imported", variable);
        assertEquals(7840, variable.size());
    }

    @Test
    public void testNestedTensorFlowReference() throws ParseException {
        RankProfileSearchFixture search = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                "    first-phase {\n" +
                "      expression: 5 + sum(tensorflow('" + modelDirectory + "'))" +
                "    }\n" +
                "  }");
        search.assertFirstPhaseExpression("5 + reduce(" + vespaExpression + ", sum)", "my_profile");

        Tensor variable_1 = search.rankProfile("my_profile").getConstants().get("Variable_1").asTensor();
        assertNotNull("Variable_1 is imported", variable_1);
        assertEquals(10, variable_1.size());

        Tensor variable = search.rankProfile("my_profile").getConstants().get("Variable").asTensor();
        assertNotNull("Variable is imported", variable);
        assertEquals(7840, variable.size());
    }

    @Test
    public void testTensorFlowReferenceSpecifyingSignature() throws ParseException {
        RankProfileSearchFixture search = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                "    first-phase {\n" +
                "      expression: tensorflow('" + modelDirectory + "', 'serving_default')" +
                "    }\n" +
                "  }");
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
    }

    @Test
    public void testTensorFlowReferenceSpecifyingSignatureAndOutput() throws ParseException {
        RankProfileSearchFixture search = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                "    first-phase {\n" +
                "      expression: tensorflow('" + modelDirectory + "', 'serving_default', 'y')" +
                "    }\n" +
                "  }");
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
    }

    @Test
    public void testTensorFlowReferenceSpecifyingNonExistingSignature() throws ParseException {
        try {
            RankProfileSearchFixture search = new RankProfileSearchFixture(
                    "  rank-profile my_profile {\n" +
                    "    first-phase {\n" +
                    "      expression: tensorflow('" + modelDirectory + "', 'serving_defaultz')" +
                    "    }\n" +
                    "  }");
            search.assertFirstPhaseExpression(vespaExpression, "my_profile");
            fail("Expecting exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Rank profile 'my_profile' is invalid: Could not import tensorflow model from tensorflow('" +
                         modelDirectory + "','serving_defaultz'): Model does not have the specified signatures 'serving_defaultz'",
                         Exceptions.toMessageString(expected));
        }
    }

    @Test
    public void testTensorFlowReferenceSpecifyingNonExistingOutput() throws ParseException {
        try {
            RankProfileSearchFixture search = new RankProfileSearchFixture(
                    "  rank-profile my_profile {\n" +
                    "    first-phase {\n" +
                    "      expression: tensorflow('" + modelDirectory + "', 'serving_default', 'x')" +
                    "    }\n" +
                    "  }");
            search.assertFirstPhaseExpression(vespaExpression, "my_profile");
            fail("Expecting exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Rank profile 'my_profile' is invalid: Could not import tensorflow model from tensorflow('" +
                         modelDirectory + "','serving_default','x'): Model does not have the specified outputs 'x'",
                         Exceptions.toMessageString(expected));
        }
    }

}
