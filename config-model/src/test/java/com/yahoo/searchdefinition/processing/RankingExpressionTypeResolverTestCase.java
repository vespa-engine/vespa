// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
                    "    field a type tensor(x[10],y[3]) {",
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
            assertEquals("In search definition 'test', rank profile 'my_rank_profile': The first-phase expression must produce a double (a tensor with no dimensions), but produces tensor(x[10],y[3])",
                         Exceptions.toMessageString(expected));
        }
    }


    @Test
    public void tensorFirstPhaseFromConstantMustProduceDouble() throws Exception {
        try {
            SearchBuilder builder = new SearchBuilder();
            builder.importString(joinLines(
                    "search test {",
                    "  document test { ",
                    "    field a type tensor(d0[3]) {",
                    "      indexing: attribute",
                    "    }",
                    "  }",
                    "  rank-profile my_rank_profile {",
                    "    function my_func() {",
                    "      expression: x_tensor*2.0",
                    "    }",
                    "    function inline other_func() {",
                    "      expression: z_tensor+3.0",
                    "    }",
                    "    first-phase {",
                    "      expression: reduce(attribute(a),sum,d0)+y_tensor+my_func+other_func",
                    "    }",
                    "    constants {",
                    "      x_tensor {",
                    "        type: tensor(x{})",
                    "        value: { {x:bar}:17 }",
                    "      }",
                    "      y_tensor {",
                    "        type: tensor(y{})",
                    "        value: { {y:foo}:42 }",
                    "      }",
                    "      z_tensor {",
                    "        type: tensor(z{})",
                    "        value: { {z:qux}:666 }",
                    "      }",
                    "    }",
                    "  }",
                    "}"
            ));
            builder.build();
            fail("Expected exception");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("In search definition 'test', rank profile 'my_rank_profile': The first-phase expression must produce a double (a tensor with no dimensions), but produces tensor(x{},y{},z{})",
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
                    "    field a type tensor(x[10],y[3]) {",
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
            assertEquals("In search definition 'test', rank profile 'my_rank_profile': The second-phase expression must produce a double (a tensor with no dimensions), but produces tensor(x[10],y[3])",
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
                    "    field a type tensor(x[10],y[5]) {",
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
            assertEquals("In search definition 'test', rank profile 'my_rank_profile': The first-phase expression is invalid: An if expression must produce compatible types in both alternatives, but the 'true' type is tensor(x[10],y[5]) while the 'false' type is tensor(z[10])" +
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
                "    field a type tensor(x[10],y[3]) {",
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
        assertEquals(TensorType.fromSpec("tensor(x[10],y[3])"),
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
                "    field a type tensor(x[10],y[1]) {",
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
        assertEquals(TensorType.fromSpec("tensor(x[10],y[1])"),
                     summaryFeatures(profile).get("return_a").type(profile.typeContext(builder.getQueryProfileRegistry())));
        assertEquals(TensorType.fromSpec("tensor(z[10])"),
                     summaryFeatures(profile).get("return_b").type(profile.typeContext(builder.getQueryProfileRegistry())));
    }

    @Test
    public void testAttributeInvocationViaBoundIdentifier() throws Exception {
        SearchBuilder builder = new SearchBuilder();
        builder.importString(joinLines(
                                 "search newsarticle {",
                                 "    document newsarticle {",
                                 "        field title type string {",
                                 "            indexing {",
                                 "                input title | index",
                                 "            }",
                                 "            weight: 30",
                                 "        }",
                                 "        field usstaticrank type int {",
                                 "            indexing: summary | attribute",
                                 "        }",
                                 "        field eustaticrank type int {",
                                 "            indexing: summary | attribute",
                                 "        }",
                                 "    }",
                                 "    rank-profile default {",
                                 "        macro newsboost() { ",
                                 "            expression: 200 * matches(title)",
                                 "        }",
                                 "        macro commonboost(mystaticrank) { ",
                                 "            expression: attribute(mystaticrank) + newsboost",
                                 "        }",
                                 "        macro commonfirstphase(mystaticrank) { ",
                                 "            expression: nativeFieldMatch(title) + commonboost(mystaticrank) ",
                                 "        }",
                                 "        first-phase { expression: commonfirstphase(usstaticrank) }",
                                 "    }",
                                 "    rank-profile eurank inherits default {",
                                 "        first-phase { expression: commonfirstphase(eustaticrank) }",
                                 "    }",
                                 "}"));
        builder.build();
        RankProfile profile = builder.getRankProfileRegistry().get(builder.getSearch(), "eurank");
    }

    @Test
    public void testTensorFunctionInvocationTypes_NestedSameName() throws Exception {
        SearchBuilder builder = new SearchBuilder();
        builder.importString(joinLines(
                "search test {",
                "  document test { ",
                "    field a type tensor(x[10],y[1]) {",
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
                "      expression: just_return(e1)",
                "    }",
                "    function just_return(e1) {",
                "      expression: e1",
                "    }",
                "    function return_second(e1, e2) {",
                "      expression: return_first(e2+0, e1)",
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
        assertEquals(TensorType.fromSpec("tensor(x[10],y[1])"),
                     summaryFeatures(profile).get("return_a").type(profile.typeContext(builder.getQueryProfileRegistry())));
        assertEquals(TensorType.fromSpec("tensor(z[10])"),
                     summaryFeatures(profile).get("return_b").type(profile.typeContext(builder.getQueryProfileRegistry())));
    }

    @Test
    public void testTensorFunctionInvocationTypes_viaFuncWithExpr() throws Exception {
        SearchBuilder builder = new SearchBuilder();
        builder.importString(joinLines(
                                 "search test {",
                                 "    document test {",
                                 "        field t1 type tensor<float>(y{}) { indexing: attribute | summary }",
                                 "        field t2 type tensor<float>(x{}) { indexing: attribute | summary }",
                                 "    }",
                                 "    rank-profile test {",
                                 "        function my_func(t) { expression: sum(t, x) + 1 }",
                                 "        function test_func_via_func_with_expr() { expression: call_func_with_expr( attribute(t1), attribute(t2) ) }",
                                 "        function call_func_with_expr(a, b) { expression: my_func( a * b ) }",
                                 "        summary-features { test_func_via_func_with_expr }",
                                 "    }",
                                 "}"));
        builder.build();
        RankProfile profile = builder.getRankProfileRegistry().get(builder.getSearch(), "test");
        assertEquals(TensorType.fromSpec("tensor<float>(y{})"),
                     summaryFeatures(profile).get("test_func_via_func_with_expr").type(profile.typeContext(builder.getQueryProfileRegistry())));
    }

    @Test
    public void importedFieldsAreAvailable() throws Exception {
        SearchBuilder builder = new SearchBuilder();
        builder.importString(joinLines(
                "search parent {",
                "  document parent {",
                "    field a type tensor(x[5],y[1000]) {",
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
        SearchBuilder builder = new SearchBuilder(logger);
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
        ));
        builder.build(true);
        String message = logger.findMessage("The following query features");
        assertNull(message);
    }

    @Test
    public void undeclaredQueryFeaturesAreAcceptedWithWarningWhenUsingTensors() throws Exception {
        InspectableDeployLogger logger = new InspectableDeployLogger();
        SearchBuilder builder = new SearchBuilder(logger);
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
        ));
        builder.build(true);
        String message = logger.findMessage("The following query features");
        assertNotNull(message);
        assertEquals("WARNING: The following query features used in 'my_rank_profile' are not declared in query profile types and " +
                     "will be interpreted as scalars, not tensors: [query(bar), query(baz), query(foo)]",
                     message);
    }

    @Test
    public void noWarningWhenUsingTensorsWhenQueryFeaturesAreDeclared() throws Exception {
        InspectableDeployLogger logger = new InspectableDeployLogger();
        SearchBuilder builder = new SearchBuilder(logger);
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
        ));
        builder.build(true);
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
