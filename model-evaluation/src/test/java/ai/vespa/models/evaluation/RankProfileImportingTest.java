// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class RankProfileImportingTest {

    @Test
    public void testImportingRankExpressions() {
        ModelTester tester = new ModelTester("src/test/resources/config/rankexpression/");

        assertEquals(18, tester.models().size());

        Model macros = tester.models().get("macros");
        assertEquals("macros", macros.name());
        assertEquals(4, macros.functions().size());
        tester.assertFunction("fourtimessum", "4 * (var1 + var2)", macros);
        tester.assertFunction("firstphase", "match + fieldMatch(title) + rankingExpression(myfeature)", macros);
        tester.assertFunction("secondphase", "rankingExpression(fourtimessum@5cf279212355b980.67f1e87166cfef86)", macros);
        tester.assertFunction("myfeature",
                              "70 * fieldMatch(title).completeness * pow(0 - fieldMatch(title).earliness,2) + " +
                              "30 * pow(0 - fieldMatch(description).earliness,2)",
                              macros);
        assertEquals(4, macros.referencedFunctions().size());
        tester.assertBoundFunction("rankingExpression(fourtimessum@5cf279212355b980.67f1e87166cfef86)",
                                   "4 * (match + rankBoost)", macros);
    }

}
