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
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TensorTransformTestCase extends SearchDefinitionTestCase {

    @Test
    public void requireThatNormalMaxAndMinAreNotReplaced() throws ParseException {
        assertTransformedExpression("max(1.0,2.0)",
                                    "max(1.0,2.0)");
        assertTransformedExpression("min(attribute(double_field),x)",
                                    "min(attribute(double_field),x)");
        assertTransformedExpression("max(attribute(double_field),attribute(double_array_field))",
                                    "max(attribute(double_field),attribute(double_array_field))");
        assertTransformedExpression("min(attribute(tensor_field_1),attribute(double_field))",
                                    "min(attribute(tensor_field_1),attribute(double_field))");
        assertTransformedExpression("reduce(max(attribute(tensor_field_1),attribute(tensor_field_2)),sum)",
                                    "reduce(max(attribute(tensor_field_1),attribute(tensor_field_2)),sum)");
        assertTransformedExpression("min(constant(test_constant_tensor),1.0)",
                                    "min(test_constant_tensor,1.0)");
        assertTransformedExpression("max(constant(base_constant_tensor),1.0)",
                                    "max(base_constant_tensor,1.0)");
        assertTransformedExpression("min(constant(file_constant_tensor),1.0)",
                                    "min(constant(file_constant_tensor),1.0)");
        assertTransformedExpression("max(query(q),1.0)",
                                    "max(query(q),1.0)");
        assertTransformedExpression("max(query(n),1.0)",
                                    "max(query(n),1.0)");
    }

    @Test
    public void requireThatMaxAndMinWithTensorAttributesAreReplaced() throws ParseException {
        assertTransformedExpression("reduce(attribute(tensor_field_1),max,x)",
                                    "max(attribute(tensor_field_1),x)");
        assertTransformedExpression("1+reduce(attribute(tensor_field_1),max,x)",
                                    "1 + max(attribute(tensor_field_1),x)");
        assertTransformedExpression("if(attribute(double_field),1+reduce(attribute(tensor_field_1),max,x),0)",
                                    "if(attribute(double_field),1 + max(attribute(tensor_field_1),x),0)");
        assertTransformedExpression("reduce(max(attribute(tensor_field_1),attribute(tensor_field_2)),max,x)",
                                    "max(max(attribute(tensor_field_1),attribute(tensor_field_2)),x)");
        assertTransformedExpression("reduce(if(attribute(double_field),attribute(tensor_field_2),attribute(tensor_field_2)),max,x)",
                                    "max(if(attribute(double_field),attribute(tensor_field_2),attribute(tensor_field_2)),x)");
        assertTransformedExpression("max(reduce(attribute(tensor_field_1),max,x),x)",
                                    "max(max(attribute(tensor_field_1),x),x)"); // will result in deploy error.
        assertTransformedExpression("reduce(reduce(attribute(tensor_field_2),max,x),max,y)",
                                    "max(max(attribute(tensor_field_2),x),y)");
    }

    @Test
    public void requireThatMaxAndMinWithConstantTensorsAreReplaced() throws ParseException {
        assertTransformedExpression("reduce(constant(test_constant_tensor),max,x)",
                                    "max(test_constant_tensor,x)");
        assertTransformedExpression("reduce(constant(base_constant_tensor),max,x)",
                                    "max(base_constant_tensor,x)");
        assertTransformedExpression("reduce(constant(file_constant_tensor),min,x)",
                                    "min(constant(file_constant_tensor),x)");
    }

    @Test
    public void requireThatMaxAndMinWithTensorExpressionsAreReplaced() throws ParseException {
        assertTransformedExpression("reduce(attribute(double_field)+attribute(tensor_field_1),min,x)",
                                    "min(attribute(double_field) + attribute(tensor_field_1),x)");
        assertTransformedExpression("reduce(attribute(tensor_field_1)*attribute(tensor_field_2),min,x)",
                                    "min(attribute(tensor_field_1) * attribute(tensor_field_2),x)");
        assertTransformedExpression("reduce(join(attribute(tensor_field_1),attribute(tensor_field_2),f(x,y)(x*y)),min,x)"
                , "min(join(attribute(tensor_field_1),attribute(tensor_field_2),f(x,y)(x*y)),x)");
        assertTransformedExpression("min(join(tensor_field_1,tensor_field_2,f(x,y)(x*y)),x)",
                                    "min(join(tensor_field_1,tensor_field_2,f(x,y)(x*y)),x)"); // because tensor fields are not in attribute(...)
        assertTransformedExpression("reduce(join(attribute(tensor_field_1),backend_rank_feature,f(x,y)(x*y)),min,x)",
                                    "min(join(attribute(tensor_field_1),backend_rank_feature,f(x,y)(x*y)),x)");
    }

    @Test
    public void requireThatMaxAndMinWithTensorFromIsReplaced() throws ParseException {
        assertTransformedExpression("reduce(tensorFromLabels(attribute(double_array_field)),max,double_array_field)",
                                    "max(tensorFromLabels(attribute(double_array_field)),double_array_field)");
        assertTransformedExpression("reduce(tensorFromLabels(attribute(double_array_field),x),max,x)",
                                    "max(tensorFromLabels(attribute(double_array_field),x),x)");
        assertTransformedExpression("reduce(tensorFromWeightedSet(attribute(weightedset_field)),max,weightedset_field)",
                                    "max(tensorFromWeightedSet(attribute(weightedset_field)),weightedset_field)");
        assertTransformedExpression("reduce(tensorFromWeightedSet(attribute(weightedset_field),x),max,x)",
                                    "max(tensorFromWeightedSet(attribute(weightedset_field),x),x)");
    }

    @Test
    public void requireThatMaxAndMinWithTensorInQueryIsReplaced() throws ParseException {
        assertTransformedExpression("reduce(query(q),max,x)", "max(query(q),x)");
        assertTransformedExpression("max(query(n),x)", "max(query(n),x)");
    }

    @Test
    public void requireThatMaxAndMinWithTensoresReturnedFromMacrosAreReplaced() throws ParseException {
        assertTransformedExpression("reduce(rankingExpression(returns_tensor),max,x)",
                                    "max(returns_tensor,x)");
        assertTransformedExpression("reduce(rankingExpression(wraps_returns_tensor),max,x)",
                                    "max(wraps_returns_tensor,x)");
        assertTransformedExpression("reduce(rankingExpression(tensor_inheriting),max,x)",
                                    "max(tensor_inheriting,x)");
        assertTransformedExpression("reduce(rankingExpression(returns_tensor_with_arg@),max,x)",
                                    "max(returns_tensor_with_arg(attribute(tensor_field_1)),x)");
    }


    private void assertTransformedExpression(String expected, String original) throws ParseException {
        for (Pair<String, String> rankPropertyExpression : buildSearch(original)) {
            String rankProperty = rankPropertyExpression.getFirst();
            if (rankProperty.equals("rankingExpression(firstphase).rankingScript")) {
                String rankExpression = censorBindingHash(rankPropertyExpression.getSecond().replace(" ",""));
                assertEquals(expected, rankExpression);
                return;
            }
        }
        fail("No 'rankingExpression(firstphase).rankingScript' property produced");
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
