// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.io.IOUtils;
import com.yahoo.searchdefinition.RankingConstant;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.TypedBinaryFormat;
import com.yahoo.yolean.Exceptions;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class RankingExpressionWithTensorFlowTestCase {

    // The "../" is to escape the "models/" element prepended to the path
    private final String modelDirectory = "../src/test/integration/tensorflow/mnist_softmax/saved";
    private final String vespaExpression = "join(rename(reduce(join(Placeholder, rename(constant(Variable), (d0, d1), (d1, d3)), f(a,b)(a * b)), sum, d1), d3, d1), rename(constant(Variable_1), d0, d1), f(a,b)(a + b))";

    @After
    public void removeGeneratedConstantTensorFiles() {
        IOUtils.recursiveDeleteDir(new File(modelDirectory.substring(3), "constants"));
        IOUtils.recursiveDeleteDir(new File(modelDirectory.substring(3), "expressions"));
    }

    @Test
    public void testMinimalTensorFlowReference() throws ParseException {
        RankProfileSearchFixture search = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                "    first-phase {\n" +
                "      expression: tensorflow('" + modelDirectory + "')" +
                "    }\n" +
                "  }");
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
        assertConstant(10, "Variable_1", search);
        assertConstant(7840, "Variable", search);
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
        assertConstant(10, "Variable_1", search);
        assertConstant(7840, "Variable", search);
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
            assertEquals("Rank profile 'my_profile' is invalid: Could not use tensorflow model from tensorflow('" +
                         modelDirectory + "','serving_defaultz'): Model does not have the specified signature 'serving_defaultz'",
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
            assertEquals("Rank profile 'my_profile' is invalid: Could not use tensorflow model from tensorflow('" +
                         modelDirectory + "','serving_default','x'): Model does not have the specified output 'x'",
                         Exceptions.toMessageString(expected));
        }
    }

    private void assertConstant(int expectedSize, String name, RankProfileSearchFixture search) {
        try {
            TensorValue constant = (TensorValue)search.rankProfile("my_profile").getConstants().get(name); // Old way. TODO: Remove
            if (constant == null) { // New way
                File constantFile = new File(modelDirectory.substring(3) + "/constants", name + ".tbf");
                RankingConstant rankingConstant = search.search().getRankingConstants().get(name);
                assertEquals(name, rankingConstant.getName());
                assertEquals(constantFile.getAbsolutePath(), rankingConstant.getFileName());
                assertTrue("Constant file has been written", constantFile.exists());
                Tensor deserializedConstant = TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(IOUtils.readFileBytes(constantFile)));
                assertEquals(expectedSize, deserializedConstant.size());
            } else { // Old way. TODO: Remove
                assertNotNull(name + " is imported", constant);
                assertEquals(expectedSize, constant.asTensor().size());
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
