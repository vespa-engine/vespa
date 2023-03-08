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

    @Test
    public void testImportingSimpleGlobalPhase() {
        ModelTester tester = new ModelTester("src/test/resources/config/dotproduct/");
        assertEquals(1, tester.models().size());
        Model m = tester.models().get("default");
        assertEquals("default", m.name());
        assertEquals(1, m.functions().size());
        tester.assertFunction("globalphase", "reduce(attribute(aa) * query(zz), sum)", m);
        var f = m.functions().get(0);
        assertEquals("globalphase", f.getName());
        assertEquals(2, f.arguments().size());
        assertEquals("tensor(d0[3])", f.getArgumentType("query(zz)").toString());
        assertEquals("tensor(d0[3])", f.getArgumentType("attribute(aa)").toString());
        var rt = f.returnType();
        assertEquals(true, rt.isPresent());
        assertEquals("tensor()", rt.get().toString());
    }

    @Test
    public void testImportingExpressionsAsArguments() {
        ModelTester tester = new ModelTester("src/test/resources/config/expressions-as-arguments/");
        assertEquals(3, tester.models().size());
    }

    @Test
    public void testImportingWithMacros() {
        ModelTester tester = new ModelTester("src/test/resources/config/ranking-macros/");
        assertEquals(5, tester.models().size());
    }

    @Test
    public void testImportingAdvancedGlobalPhase() {
        ModelTester tester = new ModelTester("src/test/resources/config/advanced-global-phase/");
        assertEquals(6, tester.models().size());
        Model m = tester.models().get("global_phase");
        assertEquals("global_phase", m.name());
        var func = m.function("globalphase");
        assertEquals("globalphase", func.getName());
        var args = func.argumentTypes();
        assertEquals(2, args.size());
        assertEquals("tensor(d0[2])", args.get("attribute(doc_vec)").toString());
        assertEquals("tensor(d0[2])", args.get("query(query_vec)").toString());
    }
}
