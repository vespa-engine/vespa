// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.TensorType;
import com.yahoo.yolean.Exceptions;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class RankingExpressionTypeValidatorTestCase {

    @Test
    public void tensorFirstPhaseMustProduceDouble() throws Exception {
        try {
            SearchBuilder builder = new SearchBuilder();
            builder.importString(joinLines(
                    "search test {",
                    "  document test { ",
                    "    field a type tensor(x[],y[]) {",
                    "      indexing: attribute",
                    "    }",
                    "  }",
                    "  rank-profile my_rank_profile {",
                    "    first-phase {",
                    "      expression: attribute(a)",
                    "    }",
                    "  }",
                    "}"
            ));
            builder.build();
            fail("Expected exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("In search definition 'test', rank profile 'my_rank_profile': The first-phase expression must produce a double (a tensor with no dimensions), but produces tensor(x[],y[])",
                         Exceptions.toMessageString(expected));
        }
    }

    @Test
    public void tensorSecondPhaseMustProduceDouble() throws Exception {
        try {
            SearchBuilder builder = new SearchBuilder();
            builder.importString(joinLines(
                    "search test {",
                    "  document test { ",
                    "    field a type tensor(x[],y[]) {",
                    "      indexing: attribute",
                    "    }",
                    "  }",
                    "  rank-profile my_rank_profile {",
                    "    first-phase {",
                    "      expression: sum(attribute(a))",
                    "    }",
                    "    second-phase {",
                    "      expression: attribute(a)",
                    "    }",
                    "  }",
                    "}"
            ));
            builder.build();
            fail("Expected exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("In search definition 'test', rank profile 'my_rank_profile': The second-phase expression must produce a double (a tensor with no dimensions), but produces tensor(x[],y[])",
                         Exceptions.toMessageString(expected));
        }
    }

    @Test
    public void tensorConditionsMustHaveTypeCompatibleBranches() throws Exception {
        try {
            SearchBuilder searchBuilder = new SearchBuilder();
            searchBuilder.importString(joinLines(
                    "search test {",
                    "  document test { ",
                    "    field a type tensor(x[],y[]) {",
                    "      indexing: attribute",
                    "    }",
                    "    field b type tensor(z[10]) {",
                    "      indexing: attribute",
                    "    }",
                    "  }",
                    "  rank-profile my_rank_profile {",
                    "    first-phase {",
                    "      expression: sum(if(1>0, attribute(a), attribute(b)))",
                    "    }",
                    "  }",
                    "}"
            ));
            searchBuilder.build();
            fail("Expected exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("In search definition 'test', rank profile 'my_rank_profile': The first-phase expression is invalid: An if expression must produce compatible types in both alternatives, but the 'true' type is tensor(x[],y[]) while the 'false' type is tensor(z[10])",
                         Exceptions.toMessageString(expected));
        }
    }

    @Test
    public void testMacroInvocationTypes() throws Exception {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(joinLines(
                "search test {",
                "  document test { ",
                "    field a type tensor(x[],y[]) {",
                "      indexing: attribute",
                "    }",
                "    field b type tensor(z[10]) {",
                "      indexing: attribute",
                "    }",
                "  }",
                "  rank-profile my_rank_profile {",
                "    macro macro1(attribute_to_use) {",
                "      expression: attribute(attribute_to_use)",
                "    }",
                "    summary-features {",
                "      macro1(a)",
                "      macro1(b)",
                "    }",
                "  }",
                "}"
        ));
        builder.build();
        RankProfile profile =
                builder.getRankProfileRegistry().getRankProfile(builder.getSearch(), "my_rank_profile");
        assertEquals(TensorType.fromSpec("tensor(x[],y[])"),
                     summaryFeatures(profile).get("macro1(a)").type(profile.typeContext(builder.getQueryProfileRegistry())));
        assertEquals(TensorType.fromSpec("tensor(z[10])"),
                     summaryFeatures(profile).get("macro1(b)").type(profile.typeContext(builder.getQueryProfileRegistry())));
    }

    @Test
    public void testTensorMacroInvocationTypes_Nested() throws Exception {
        SearchBuilder builder = new SearchBuilder();
        builder.importString(joinLines(
                "search test {",
                "  document test { ",
                "    field a type tensor(x[],y[]) {",
                "      indexing: attribute",
                "    }",
                "    field b type tensor(z[10]) {",
                "      indexing: attribute",
                "    }",
                "  }",
                "  rank-profile my_rank_profile {",
                "    macro return_a() {",
                "      expression: return_first(attribute(a), attribute(b))",
                "    }",
                "    macro return_b() {",
                "      expression: return_second(attribute(a), attribute(b))",
                "    }",
                "    macro return_first(e1, e2) {",
                "      expression: e1",
                "    }",
                "    macro return_second(e1, e2) {",
                "      expression: return_first(e2, e1)",
                "    }",
                "    summary-features {",
                "      return_a",
                "      return_b",
                "    }",
                "  }",
                "}"
        ));
        builder.build();
        RankProfile profile =
                builder.getRankProfileRegistry().getRankProfile(builder.getSearch(), "my_rank_profile");
        assertEquals(TensorType.fromSpec("tensor(x[],y[])"),
                     summaryFeatures(profile).get("return_a").type(profile.typeContext(builder.getQueryProfileRegistry())));
        assertEquals(TensorType.fromSpec("tensor(z[10])"),
                     summaryFeatures(profile).get("return_b").type(profile.typeContext(builder.getQueryProfileRegistry())));
    }

    @Test
    public void importedFieldsAreAvailable() throws Exception {
        SearchBuilder builder = new SearchBuilder();
        builder.importString(joinLines(
                "search parent {",
                "  document parent {",
                "    field a type tensor(x[],y[]) {",
                "      indexing: attribute",
                "    }",
                "  }",
                "}"
        ));
        builder.importString(joinLines(
                "search child {",
                "  document child { ",
                "    field ref type reference<parent> {",
                        "indexing: attribute | summary",
                "    }",
                "  }",
                "  import field ref.a as imported_a {}",
                "  rank-profile my_rank_profile {",
                "    first-phase {",
                "      expression: sum(attribute(imported_a))",
                "    }",
                "  }",
                "}"
        ));
        builder.build();
    }

    @Test
    public void undeclaredQueryFeaturesAreAccepted() throws Exception {
        SearchBuilder builder = new SearchBuilder();
        builder.importString(joinLines(
                "search test {",
                "  document test { ",
                "  }",
                "  rank-profile my_rank_profile {",
                "    first-phase {",
                "      expression: query(foo)",
                "    }",
                "  }",
                "}"
        ));
        builder.build();
    }

    private Map<String, ReferenceNode> summaryFeatures(RankProfile profile) {
        return profile.getSummaryFeatures().stream().collect(Collectors.toMap(f -> f.toString(), f -> f));
    }

}
