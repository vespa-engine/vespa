// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.types.TensorFieldType;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.TensorType;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class RankingExpressionTypeResolverTestCase {

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
            assertEquals("In search definition 'test', rank profile 'my_rank_profile': The first-phase expression is invalid: An if expression must produce compatible types in both alternatives, but the 'true' type is tensor(x[],y[]) while the 'false' type is tensor(z[10])" +
                         "\n'true' branch: attribute(a)" +
                         "\n'false' branch: attribute(b)",
                         Exceptions.toMessageString(expected));
        }
    }

    @Test
    public void testFunctionInvocationTypes() throws Exception {
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
                "    function macro1(attribute_to_use) {",
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
                builder.getRankProfileRegistry().get(builder.getSearch(), "my_rank_profile");
        assertEquals(TensorType.fromSpec("tensor(x[],y[])"),
                     summaryFeatures(profile).get("macro1(a)").type(profile.typeContext(builder.getQueryProfileRegistry())));
        assertEquals(TensorType.fromSpec("tensor(z[10])"),
                     summaryFeatures(profile).get("macro1(b)").type(profile.typeContext(builder.getQueryProfileRegistry())));
    }

    @Test
    public void testTensorFunctionInvocationTypes_Nested() throws Exception {
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
                "    function return_a() {",
                "      expression: return_first(attribute(a), attribute(b))",
                "    }",
                "    function return_b() {",
                "      expression: return_second(attribute(a), attribute(b))",
                "    }",
                "    function return_first(e1, e2) {",
                "      expression: e1",
                "    }",
                "    function return_second(e1, e2) {",
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
                builder.getRankProfileRegistry().get(builder.getSearch(), "my_rank_profile");
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
        InspectableDeployLogger logger = new InspectableDeployLogger();
        SearchBuilder builder = new SearchBuilder();
        builder.importString(joinLines(
                "search test {",
                "  document test { ",
                "    field anyfield type double {" +
                "      indexing: attribute",
                "    }",
                "  }",
                "  rank-profile my_rank_profile {",
                "    first-phase {",
                "      expression: query(foo) + f() + sum(attribute(anyfield))",
                "    }",
                "    function f() {",
                "      expression: query(bar) + query(baz)",
                "    }",
                "  }",
                "}"
        ), logger);
        builder.build(true, logger);
        String message = logger.findMessage("The following query features");
        assertNull(message);
    }

    @Test
    public void undeclaredQueryFeaturesAreAcceptedWithWarningWhenUsingTensors() throws Exception {
        InspectableDeployLogger logger = new InspectableDeployLogger();
        SearchBuilder builder = new SearchBuilder();
        builder.importString(joinLines(
                "search test {",
                "  document test { ",
                "    field anyfield type tensor(d[2]) {",
                "      indexing: attribute",
                "    }",
                "  }",
                "  rank-profile my_rank_profile {",
                "    first-phase {",
                "      expression: query(foo) + f() + sum(attribute(anyfield))",
                "    }",
                "    function f() {",
                "      expression: query(bar) + query(baz)",
                "    }",
                "  }",
                "}"
        ), logger);
        builder.build(true, logger);
        String message = logger.findMessage("The following query features");
        assertNotNull(message);
        assertEquals("WARNING: The following query features are not declared in query profile types and " +
                     "will be interpreted as scalars, not tensors: [query(bar), query(baz), query(foo)]",
                     message);
    }

    @Test
    public void noWarningWhenUsingTensorsWhenQueryFeaturesAreDeclared() throws Exception {
        InspectableDeployLogger logger = new InspectableDeployLogger();
        SearchBuilder builder = new SearchBuilder();
        QueryProfileType myType = new QueryProfileType("mytype");
        myType.addField(new FieldDescription("rank.feature.query(foo)",
                                             new TensorFieldType(TensorType.fromSpec("tensor(d[2])"))),
                        builder.getQueryProfileRegistry().getTypeRegistry());
        myType.addField(new FieldDescription("rank.feature.query(bar)",
                                             new TensorFieldType(TensorType.fromSpec("tensor(d[2])"))),
                        builder.getQueryProfileRegistry().getTypeRegistry());
        myType.addField(new FieldDescription("rank.feature.query(baz)",
                                             new TensorFieldType(TensorType.fromSpec("tensor(d[2])"))),
                        builder.getQueryProfileRegistry().getTypeRegistry());
        builder.getQueryProfileRegistry().getTypeRegistry().register(myType);
        builder.importString(joinLines(
                "search test {",
                "  document test { ",
                "    field anyfield type tensor(d[2]) {",
                "      indexing: attribute",
                "    }",
                "  }",
                "  rank-profile my_rank_profile {",
                "    first-phase {",
                "      expression: sum(query(foo) + f() + sum(attribute(anyfield)))",
                "    }",
                "    function f() {",
                "      expression: query(bar) + query(baz)",
                "    }",
                "  }",
                "}"
        ), logger);
        builder.build(true, logger);
        String message = logger.findMessage("The following query features");
        assertNull(message);
    }

    private Map<String, ReferenceNode> summaryFeatures(RankProfile profile) {
        return profile.getSummaryFeatures().stream().collect(Collectors.toMap(f -> f.toString(), f -> f));
    }

    private static class InspectableDeployLogger implements DeployLogger {

        private List<String> messages = new ArrayList<>();

        @Override
        public void log(Level level, String message) {
            messages.add(level + ": " + message);
        }

        /** Returns the first message containing the given string, or null if none */
        public String findMessage(String substring) {
            return messages.stream().filter(message -> message.contains(substring)).findFirst().orElse(null);
        }

    }

}
