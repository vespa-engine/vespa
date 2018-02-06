// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.collections.Pair;
import com.yahoo.component.ComponentId;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.types.QueryProfileTypeRegistry;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.searchdefinition.derived.AttributeFields;
import com.yahoo.searchdefinition.derived.RawRankProfile;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class TensorTransformTestCase extends SearchDefinitionTestCase {

    @Test
    public void requireThatNormalMaxAndMinAreNotReplaced() throws ParseException {
        assertContainsExpression("max(1.0,2.0)", "max(1.0,2.0)");
        assertContainsExpression("min(attribute(double_field),x)", "min(attribute(double_field),x)");
        assertContainsExpression("max(attribute(double_field),attribute(double_array_field))", "max(attribute(double_field),attribute(double_array_field))");
        assertContainsExpression("min(attribute(tensor_field_1),attribute(double_field))", "min(attribute(tensor_field_1),attribute(double_field))");
        assertContainsExpression("max(attribute(tensor_field_1),attribute(tensor_field_2))", "max(attribute(tensor_field_1),attribute(tensor_field_2))");
        assertContainsExpression("min(test_constant_tensor,1.0)", "min(constant(test_constant_tensor),1.0)");
        assertContainsExpression("max(base_constant_tensor,1.0)", "max(constant(base_constant_tensor),1.0)");
        assertContainsExpression("min(constant(file_constant_tensor),1.0)", "min(constant(file_constant_tensor),1.0)");
        assertContainsExpression("max(query(q),1.0)", "max(query(q),1.0)");
        assertContainsExpression("max(query(n),1.0)", "max(query(n),1.0)");
    }

    @Test
    public void requireThatMaxAndMinWithTensorAttributesAreReplaced() throws ParseException {
        assertContainsExpression("max(attribute(tensor_field_1),x)", "reduce(attribute(tensor_field_1),max,x)");
        assertContainsExpression("1 + max(attribute(tensor_field_1),x)", "1+reduce(attribute(tensor_field_1),max,x)");
        assertContainsExpression("if(attribute(double_field),1 + max(attribute(tensor_field_1),x),0)", "if(attribute(double_field),1+reduce(attribute(tensor_field_1),max,x),0)");
        assertContainsExpression("max(max(attribute(tensor_field_1),attribute(tensor_field_2)),x)", "reduce(max(attribute(tensor_field_1),attribute(tensor_field_2)),max,x)");
        assertContainsExpression("max(if(attribute(double_field),attribute(tensor_field_1),attribute(tensor_field_2)),x)", "reduce(if(attribute(double_field),attribute(tensor_field_1),attribute(tensor_field_2)),max,x)");
        assertContainsExpression("max(max(attribute(tensor_field_1),x),x)", "max(reduce(attribute(tensor_field_1),max,x),x)"); // will result in deploy error.
        assertContainsExpression("max(max(attribute(tensor_field_2),x),y)", "reduce(reduce(attribute(tensor_field_2),max,x),max,y)");
    }

    @Test
    public void requireThatMaxAndMinWithConstantTensorsAreReplaced() throws ParseException {
        assertContainsExpression("max(test_constant_tensor,x)", "reduce(constant(test_constant_tensor),max,x)");
        assertContainsExpression("max(base_constant_tensor,x)", "reduce(constant(base_constant_tensor),max,x)");
        assertContainsExpression("min(constant(file_constant_tensor),x)", "reduce(constant(file_constant_tensor),min,x)");
    }

    @Test
    public void requireThatMaxAndMinWithTensorExpressionsAreReplaced() throws ParseException {
        assertContainsExpression("min(attribute(double_field) + attribute(tensor_field_1),x)", "reduce(attribute(double_field)+attribute(tensor_field_1),min,x)");
        assertContainsExpression("min(attribute(tensor_field_1) * attribute(tensor_field_2),x)", "reduce(attribute(tensor_field_1)*attribute(tensor_field_2),min,x)");
        assertContainsExpression("min(join(attribute(tensor_field_1),attribute(tensor_field_2),f(x,y)(x*y)),x)", "reduce(join(attribute(tensor_field_1),attribute(tensor_field_2),f(x,y)(x*y)),min,x)");
        assertContainsExpression("min(join(tensor_field_1,tensor_field_2,f(x,y)(x*y)),x)", "min(join(tensor_field_1,tensor_field_2,f(x,y)(x*y)),x)"); // because tensor fields are not in attribute(...)
        assertContainsExpression("min(join(attribute(tensor_field_1),backend_rank_feature,f(x,y)(x*y)),x)", "min(join(attribute(tensor_field_1),backend_rank_feature,f(x,y)(x*y)),x)");
    }

    @Test
    public void requireThatMaxAndMinWithTensorFromIsReplaced() throws ParseException {
        assertContainsExpression("max(tensorFromLabels(attribute(double_array_field)),double_array_field)", "reduce(tensorFromLabels(attribute(double_array_field)),max,double_array_field)");
        assertContainsExpression("max(tensorFromLabels(attribute(double_array_field),x),x)", "reduce(tensorFromLabels(attribute(double_array_field),x),max,x)");
        assertContainsExpression("max(tensorFromWeightedSet(attribute(weightedset_field)),weightedset_field)", "reduce(tensorFromWeightedSet(attribute(weightedset_field)),max,weightedset_field)");
        assertContainsExpression("max(tensorFromWeightedSet(attribute(weightedset_field),x),x)", "reduce(tensorFromWeightedSet(attribute(weightedset_field),x),max,x)");
    }

    @Test
    public void requireThatMaxAndMinWithTensorInQueryIsReplaced() throws ParseException {
        assertContainsExpression("max(query(q),x)", "reduce(query(q),max,x)");
        assertContainsExpression("max(query(n),x)", "max(query(n),x)");
    }

    @Test
    public void requireThatMaxAndMinWithTensoresReturnedFromMacrosAreReplaced() throws ParseException {
        assertContainsExpression("max(returns_tensor,x)", "reduce(rankingExpression(returns_tensor),max,x)");
        assertContainsExpression("max(wraps_returns_tensor,x)", "reduce(rankingExpression(wraps_returns_tensor),max,x)");
        assertContainsExpression("max(tensor_inheriting,x)", "reduce(rankingExpression(tensor_inheriting),max,x)");
        assertContainsExpression("max(returns_tensor_with_arg(attribute(tensor_field_1)),x)", "reduce(rankingExpression(returns_tensor_with_arg@),max,x)");
    }


    private void assertContainsExpression(String expr, String transformedExpression) throws ParseException {
        assertTrue("Expected expression '" + transformedExpression + "' found",
                   containsExpression(expr, transformedExpression));
    }

    private boolean containsExpression(String expr, String transformedExpression) throws ParseException {
        for (Pair<String, String> rankPropertyExpression : buildSearch(expr)) {
            String rankProperty = rankPropertyExpression.getFirst();
            if (rankProperty.equals("rankingExpression(firstphase).rankingScript")) {
                String rankExpression = censorBindingHash(rankPropertyExpression.getSecond().replace(" ",""));
                return rankExpression.equals(transformedExpression);
            }
        }
        return false;
    }

    private List<Pair<String, String>> buildSearch(String expression) throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        QueryProfileRegistry queryProfiles = setupQueryProfileTypes();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry, queryProfiles);
        builder.importString(
                "search test {\n" +
                "    document test { \n" +
                "        field double_field type double { \n" +
                "            indexing: summary | attribute \n" +
                "        }\n" +
                "        field double_array_field type array<double> { \n" +
                "            indexing: summary | attribute \n" +
                "        }\n" +
                "        field weightedset_field type weightedset<double> { \n" +
                "            indexing: summary | attribute \n" +
                "        }\n" +
                "        field tensor_field_1 type tensor(x{}) { \n" +
                "            indexing: summary | attribute \n" +
                "            attribute: tensor(x{}) \n" +
                "        }\n" +
                "        field tensor_field_2 type tensor(x[3],y[3]) { \n" +
                "            indexing: summary | attribute \n" +
                "            attribute: tensor(x[3],y[3]) \n" +
                "        }\n" +
                "    }\n" +
                "    constant file_constant_tensor {\n" +
                "        file: constants/tensor.json\n" +
                "        type: tensor(x{})\n" +
                "    }\n" +
                "    rank-profile base {\n" +
                "        constants {\n" +
                "            base_constant_tensor {\n" +
                "                value: { {x:0}:0 }\n" +
                "            }\n" +
                "        }\n" +
                "        macro base_tensor() {\n" +
                "            expression: constant(base_constant_tensor)\n" +
                "        }\n" +
                "    }\n" +
                "    rank-profile test inherits base {\n" +
                "        constants {\n" +
                "            test_constant_tensor {\n" +
                "                value: { {x:0}:1 }\n" +
                "            }\n" +
                "        }\n" +
                "        macro returns_tensor_with_arg(arg1) {\n" +
                "            expression: 2.0 * arg1\n" +
                "        }\n" +
                "        macro wraps_returns_tensor() {\n" +
                "            expression: returns_tensor\n" +
                "        }\n" +
                "        macro returns_tensor() {\n" +
                "            expression: attribute(tensor_field_2)\n" +
                "        }\n" +
                "        macro tensor_inheriting() {\n" +
                "            expression: base_tensor\n" +
                "        }\n" +
                "        first-phase {\n" +
                "            expression: " + expression + "\n" +
                "        }\n" +
                "    }\n" +
                "}\n");
        builder.build(new BaseDeployLogger());
        Search s = builder.getSearch();
        RankProfile test = rankProfileRegistry.getRankProfile(s, "test").compile(queryProfiles);
        List<Pair<String, String>> testRankProperties = new RawRankProfile(test,
                                                                           queryProfiles,
                                                                           new AttributeFields(s)).configProperties();
        return testRankProperties;
    }

    private static QueryProfileRegistry setupQueryProfileTypes() {
        QueryProfileRegistry registry = new QueryProfileRegistry();
        QueryProfileTypeRegistry typeRegistry = registry.getTypeRegistry();
        QueryProfileType type = new QueryProfileType(new ComponentId("testtype"));
        type.addField(new FieldDescription("ranking.features.query(q)",
                FieldType.fromString("tensor(x{})", typeRegistry)), typeRegistry);
        type.addField(new FieldDescription("ranking.features.query(n)",
                FieldType.fromString("integer", typeRegistry)), typeRegistry);
        typeRegistry.register(type);
        return registry;
    }

    private String censorBindingHash(String s) {
        StringBuilder b = new StringBuilder();
        boolean areInHash = false;
        for (int i = 0; i < s.length() ; i++) {
            char current = s.charAt(i);
            if ( ! Character.isLetterOrDigit(current)) // end of hash
                areInHash = false;
            if ( ! areInHash)
                b.append(current);
            if (current == '@') // start of hash
                areInHash = true;
        }
        return b.toString();
    }

}
