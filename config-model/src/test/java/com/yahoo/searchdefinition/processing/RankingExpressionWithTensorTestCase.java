// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author geirst
 */
public class RankingExpressionWithTensorTestCase {

    private static class SearchFixture {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        Search search;
        SearchFixture(String rankProfiles) throws ParseException {
            SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
            String sdContent = "search test {\n" +
                "  document test {\n" +
                "  }\n" +
                rankProfiles +
                "\n" +
                "}";
            builder.importString(sdContent);
            builder.build();
            search = builder.getSearch();
        }
        public void assertFirstPhaseExpression(String expExpression, String rankProfile) {
            assertEquals(expExpression, getRankProfile(rankProfile).getFirstPhaseRanking().getRoot().toString());
        }
        public void assertSecondPhaseExpression(String expExpression, String rankProfile) {
            assertEquals(expExpression, getRankProfile(rankProfile).getSecondPhaseRanking().getRoot().toString());
        }
        public void assertRankProperty(String expValue, String name, String rankProfile) {
            List<RankProfile.RankProperty> rankPropertyList = getRankProfile(rankProfile).getRankPropertyMap().get(name);
            assertEquals(1, rankPropertyList.size());
            assertEquals(expValue, rankPropertyList.get(0).getValue());
        }
        public void assertMacro(String expExpression, String macroName, String rankProfile) {
            assertEquals(expExpression, getRankProfile(rankProfile).getMacros().get(macroName).getRankingExpression().getRoot().toString());
        }
        private RankProfile getRankProfile(String rankProfile) {
            return rankProfileRegistry.getRankProfile(search, rankProfile).compile();
        }
    }

    @Test
    public void requireThatSingleLineConstantTensorAndTypeCanBeParsed() throws ParseException {
        SearchFixture f = new SearchFixture(
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
        SearchFixture f = new SearchFixture(
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
        SearchFixture f = new SearchFixture(
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
        SearchFixture f = new SearchFixture(
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
        SearchFixture f = new SearchFixture(
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
        SearchFixture f = new SearchFixture(
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
        new SearchFixture(
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
