// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * @author geirst
 */
public class RankingExpressionWithTensorTestCase {

    @Test
    public void requireThatSingleLineConstantTensorAndTypeCanBeParsed() throws ParseException {
        RankProfileSearchFixture f = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                "    first-phase {\n" +
                "      expression: sum(my_tensor)\n" +
                "    }\n" +
                "    constants {\n" +
                "      my_tensor {\n" +
                "        value: { {x:1,y:2}:1, {x:2,y:1}:2 }\n" +
                "        type: tensor(x{},y{})\n" +
                "      }\n" +
                "    }\n" +
                "  }");
        f.assertFirstPhaseExpression("reduce(constant(my_tensor), sum)", "my_profile");
        f.assertRankProperty("{{x:1,y:2}:1.0,{x:2,y:1}:2.0}", "constant(my_tensor).value", "my_profile");
        f.assertRankProperty("tensor(x{},y{})", "constant(my_tensor).type", "my_profile");
    }

    @Test
    public void requireThatMultiLineConstantTensorAndTypeCanBeParsed() throws ParseException {
        RankProfileSearchFixture f = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                "    first-phase {\n" +
                "      expression: sum(my_tensor)\n" +
                "    }\n" +
                "    constants {\n" +
                "      my_tensor {\n" +
                "        value {\n" +
                "          { {x:1,y:2}:1,\n" +
                "            {x:2,y:1}:2 }\n" +
                "        }\n" +
                "        type: tensor(x{},y{})\n" +
                "      }\n" +
                "    }\n" +
                "  }");
        f.assertFirstPhaseExpression("reduce(constant(my_tensor), sum)", "my_profile");
        f.assertRankProperty("{{x:1,y:2}:1.0,{x:2,y:1}:2.0}", "constant(my_tensor).value", "my_profile");
        f.assertRankProperty("tensor(x{},y{})", "constant(my_tensor).type", "my_profile");
    }

    @Test
    public void requireThatConstantTensorsCanBeUsedInSecondPhaseExpression() throws ParseException {
        RankProfileSearchFixture f = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                "    second-phase {\n" +
                "      expression: sum(my_tensor)\n" +
                "    }\n" +
                "    constants {\n" +
                "      my_tensor {\n" +
                "        value: { {x:1}:1 }\n" +
                "      }\n" +
                "    }\n" +
                "  }");
        f.assertSecondPhaseExpression("reduce(constant(my_tensor), sum)", "my_profile");
        f.assertRankProperty("{{x:1}:1.0}", "constant(my_tensor).value", "my_profile");
        f.assertRankProperty("tensor(x{})", "constant(my_tensor).type", "my_profile");
    }

    @Test
    public void requireThatConstantTensorsCanBeUsedInInheritedRankProfile() throws ParseException {
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
        f.assertFirstPhaseExpression("reduce(constant(my_tensor), sum)", "my_profile");
        f.assertRankProperty("{{x:1}:1.0}", "constant(my_tensor).value", "my_profile");
        f.assertRankProperty("tensor(x{})", "constant(my_tensor).type", "my_profile");
    }

    @Test
    public void requireThatConstantTensorsCanBeUsedInMacro() throws ParseException {
        RankProfileSearchFixture f = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                "    macro my_macro() {\n" +
                "      expression: sum(my_tensor)\n" +
                "    }\n" +
                "    first-phase {\n" +
                "      expression: 5.0 + my_macro\n" +
                "    }\n" +
                "    constants {\n" +
                "      my_tensor {\n" +
                "        value: { {x:1}:1 }\n" +
                "      }\n" +
                "    }\n" +
                "  }");
        f.assertFirstPhaseExpression("5.0 + my_macro", "my_profile");
        f.assertMacro("reduce(constant(my_tensor), sum)", "my_macro", "my_profile");
        f.assertRankProperty("{{x:1}:1.0}", "constant(my_tensor).value", "my_profile");
        f.assertRankProperty("tensor(x{})", "constant(my_tensor).type", "my_profile");
    }

    @Test
    public void requireThatCombinationOfConstantTensorsAndConstantValuesCanBeUsed() throws ParseException {
        RankProfileSearchFixture f = new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                "    first-phase {\n" +
                "      expression: my_number_1 + sum(my_tensor) + my_number_2\n" +
                "    }\n" +
                "    constants {\n" +
                "      my_number_1: 3.0\n" +
                "      my_tensor {\n" +
                "        value: { {x:1}:1 }\n" +
                "      }\n" +
                "      my_number_2: 5.0\n" +
                "    }\n" +
                "  }");
        f.assertFirstPhaseExpression("3.0 + reduce(constant(my_tensor), sum) + 5.0", "my_profile");
        f.assertRankProperty("{{x:1}:1.0}", "constant(my_tensor).value", "my_profile");
        f.assertRankProperty("tensor(x{})", "constant(my_tensor).type", "my_profile");
    }

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void requireThatInvalidTensorTypeSpecThrowsException() throws ParseException {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("For constant tensor 'my_tensor' in rank profile 'my_profile': Illegal tensor type spec: Failed parsing element 'x' in type spec 'tensor(x)'");
        new RankProfileSearchFixture(
                "  rank-profile my_profile {\n" +
                "    constants {\n" +
                "      my_tensor {\n" +
                "        value: { {x:1}:1 }\n" +
                "        type: tensor(x)\n" +
                "      }\n" +
                "    }\n" +
                "  }");
    }

}
