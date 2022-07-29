// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author geirst
 */
public class RankingExpressionWithTensorTestCase {

    @Test
    void requireThatSingleLineConstantMappedTensorCanBeParsed() throws ParseException {
        RankProfileSearchFixture f = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                        "    first-phase {\n" +
                        "      expression: sum(my_tensor)\n" +
                        "    }\n" +
                        "    constants {\n" +
                        "      my_tensor tensor(x{},y{}):{ {x:1,y:2}:1, {x:2,y:1}:2 }\n" +
                        "    }\n" +
                        "  }");
        f.compileRankProfile("my_profile");
        f.assertFirstPhaseExpression("reduce(constant(my_tensor), sum)", "my_profile");
        f.assertRankProperty("tensor(x{},y{}):{{x:1,y:2}:1.0, {x:2,y:1}:2.0}", "constant(my_tensor).value", "my_profile");
        f.assertRankProperty("tensor(x{},y{})", "constant(my_tensor).type", "my_profile");
    }

    @Test
    void requireThatSingleLineConstantIndexedTensorCanBeParsed() throws ParseException {
        RankProfileSearchFixture f = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                        "    first-phase {\n" +
                        "      expression: sum(my_tensor)\n" +
                        "    }\n" +
                        "    constants {\n" +
                        "      my_tensor tensor(x[3]):{ {x:0}:1, {x:1}:2, {x:2}:3 }\n" +
                        "    }\n" +
                        "  }");
        f.compileRankProfile("my_profile");
        f.assertFirstPhaseExpression("reduce(constant(my_tensor), sum)", "my_profile");
        f.assertRankProperty("tensor(x[3]):[1.0, 2.0, 3.0]", "constant(my_tensor).value", "my_profile");
        f.assertRankProperty("tensor(x[3])", "constant(my_tensor).type", "my_profile");
    }

    @Test
    void requireThatSingleLineConstantIndexedTensorShortFormCanBeParsed() throws ParseException {
        RankProfileSearchFixture f = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                        "    first-phase {\n" +
                        "      expression: sum(my_tensor)\n" +
                        "    }\n" +
                        "    constants {\n" +
                        "      my_tensor tensor(x[3]):[1, 2, 3]\n" +
                        "    }\n" +
                        "  }");
        f.compileRankProfile("my_profile");
        f.assertFirstPhaseExpression("reduce(constant(my_tensor), sum)", "my_profile");
        f.assertRankProperty("tensor(x[3]):[1.0, 2.0, 3.0]", "constant(my_tensor).value", "my_profile");
        f.assertRankProperty("tensor(x[3])", "constant(my_tensor).type", "my_profile");
    }

    @Test
    void requireConstantTensorCanBeReferredViaConstantFeature() throws ParseException {
        RankProfileSearchFixture f = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                        "    first-phase {\n" +
                        "      expression: sum(constant(my_tensor))\n" +
                        "    }\n" +
                        "    constants {\n" +
                        "      my_tensor tensor(x{},y{}):{{x:1,y:2}:1, {x:2,y:1}:2}\n" +
                        "    }\n" +
                        "  }");
        f.compileRankProfile("my_profile");
        f.assertFirstPhaseExpression("reduce(constant(my_tensor), sum)", "my_profile");
        f.assertRankProperty("tensor(x{},y{}):{{x:1,y:2}:1.0, {x:2,y:1}:2.0}", "constant(my_tensor).value", "my_profile");
        f.assertRankProperty("tensor(x{},y{})", "constant(my_tensor).type", "my_profile");
    }

    @Test
    void requireThatMultiLineConstantTensorAndTypeCanBeParsed() throws ParseException {
        RankProfileSearchFixture f = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                        "    first-phase {\n" +
                        "      expression: sum(my_tensor)\n" +
                        "    }\n" +
                        "    constants {\n" +
                        "      my_tensor tensor(x{},y{}):\n" +
                        "          { {x:1,y:2}:1,\n" +
                        "            {x:2,y:1}:2 }\n" +
                        "    }\n" +
                        "  }");
        f.compileRankProfile("my_profile");
        f.assertFirstPhaseExpression("reduce(constant(my_tensor), sum)", "my_profile");
        f.assertRankProperty("tensor(x{},y{}):{{x:1,y:2}:1.0, {x:2,y:1}:2.0}", "constant(my_tensor).value", "my_profile");
        f.assertRankProperty("tensor(x{},y{})", "constant(my_tensor).type", "my_profile");
    }

    @Test
    void requireThatConstantTensorsCanBeUsedInSecondPhaseExpression() throws ParseException {
        RankProfileSearchFixture f = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                        "    second-phase {\n" +
                        "      expression: sum(my_tensor)\n" +
                        "    }\n" +
                        "    constants {\n" +
                        "      my_tensor tensor(x{}):{ {x:1}:1 }\n" +
                        "    }\n" +
                        "  }");
        f.compileRankProfile("my_profile");
        f.assertSecondPhaseExpression("reduce(constant(my_tensor), sum)", "my_profile");
        f.assertRankProperty("tensor(x{}):{1:1.0}", "constant(my_tensor).value", "my_profile");
        f.assertRankProperty("tensor(x{})", "constant(my_tensor).type", "my_profile");
    }

    @Test
    void requireThatConstantTensorsCanBeUsedInInheritedRankProfile() throws ParseException {
        RankProfileSearchFixture f = new RankProfileSearchFixture(
                "  rank-profile parent {\n" +
                        "    constants {\n" +
                        "      my_tensor {\n" +
                        "        value: { {x:1}:1 }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "  rank-profile my_profile inherits parent {\n" +
                        "    first-phase {\n" +
                        "      expression: sum(my_tensor)\n" +
                        "    }\n" +
                        "  }");
        f.compileRankProfile("my_profile");
        f.assertFirstPhaseExpression("reduce(constant(my_tensor), sum)", "my_profile");
        f.assertRankProperty("tensor(x{}):{1:1.0}", "constant(my_tensor).value", "my_profile");
        f.assertRankProperty("tensor(x{})", "constant(my_tensor).type", "my_profile");
    }

    @Test
    void requireThatConstantTensorsCanBeUsedInFunction() throws ParseException {
        RankProfileSearchFixture f = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                        "    function my_macro() {\n" +
                        "      expression: sum(my_tensor)\n" +
                        "    }\n" +
                        "    first-phase {\n" +
                        "      expression: 5.0 + my_macro\n" +
                        "    }\n" +
                        "    constants {\n" +
                        "      my_tensor tensor(x{}):{ {x:1}:1 }\n" +
                        "    }\n" +
                        "  }");
        f.compileRankProfile("my_profile");
        f.assertFirstPhaseExpression("5.0 + my_macro", "my_profile");
        f.assertFunction("reduce(constant(my_tensor), sum)", "my_macro", "my_profile");
        f.assertRankProperty("tensor(x{}):{1:1.0}", "constant(my_tensor).value", "my_profile");
        f.assertRankProperty("tensor(x{})", "constant(my_tensor).type", "my_profile");
    }

    @Test
    void requireThatCombinationOfConstantTensorsAndConstantValuesCanBeUsed() throws ParseException {
        RankProfileSearchFixture f = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                        "    first-phase {\n" +
                        "      expression: my_number_1 + sum(my_tensor) + my_number_2\n" +
                        "    }\n" +
                        "    constants {\n" +
                        "      my_number_1 double: 3.0\n" +
                        "      my_tensor tensor(x{}):{ {x:1}:1 }\n" +
                        "      my_number_2 double: 5.0\n" +
                        "    }\n" +
                        "  }");
        f.compileRankProfile("my_profile");
        f.assertFirstPhaseExpression("3.0 + reduce(constant(my_tensor), sum) + 5.0", "my_profile");
        f.assertRankProperty("tensor(x{}):{1:1.0}", "constant(my_tensor).value", "my_profile");
        f.assertRankProperty("tensor(x{})", "constant(my_tensor).type", "my_profile");
    }

    @Test
    void requireThatInvalidTensorTypeSpecThrowsException() throws ParseException {
        try {
            RankProfileSearchFixture f = new RankProfileSearchFixture(
                    "  rank-profile my_profile {\n" +
                            "    constants {\n" +
                            "      my_tensor tensor(x):{ {x:1}:1 }\n" +
                            "    }\n" +
                            "  }");
            f.compileRankProfile("my_profile");
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertStartsWith("Type of constant(my_tensor): Illegal tensor type spec: A tensor type spec must be on the form",
                    e.getMessage());
        }
    }

    private void assertStartsWith(String prefix, String string) {
        assertEquals(prefix, string.substring(0, Math.min(prefix.length(), string.length())));
    }

}
