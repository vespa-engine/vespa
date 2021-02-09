// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.expressiontransforms.RankProfileTransformContext;
import com.yahoo.searchdefinition.expressiontransforms.TokenTransformer;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.tensor.Tensor;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class RankingExpressionWithTransformerTokensTestCase {

    @Test
    public void testTokenInputIds() throws Exception {
        String expected = "tensor(d0[1],d1[12]):[101,1,2,102,3,4,5,102,6,7,102,0]";
        String a = "tensor(d0[2]):[1,2]";
        String b = "tensor(d0[3]):[3,4,5]";
        String c = "tensor(d0[2]):[6,7]";
        String expression = "tokenInputIds(12, a, b, c)";
        Tensor result = evaluateExpression(expression, a, b, c);
        assertEquals(Tensor.from(expected), result);
    }

    @Test
    public void testTokenTypeIds() throws Exception {
        String expected = "tensor(d0[1],d1[10]):[0,0,0,0,1,1,1,1,0,0]";
        String a = "tensor(d0[2]):[1,2]";
        String b = "tensor(d0[3]):[3,4,5]";
        String expression = "tokenTypeIds(10, a, b)";
        Tensor result = evaluateExpression(expression, a, b);
        assertEquals(Tensor.from(expected), result);
    }

    @Test
    public void testAttentionMask() throws Exception {
        String expected = "tensor(d0[1],d1[10]):[1,1,1,1,1,1,1,1,0,0]";
        String a = "tensor(d0[2]):[1,2]";
        String b = "tensor(d0[3]):[3,4,5]";
        String expression = "tokenAttentionMask(10, a, b)";
        Tensor result = evaluateExpression(expression, a, b);
        assertEquals(Tensor.from(expected), result);
    }

    private Tensor evaluateExpression(String expression, String a, String b) throws Exception {
        return evaluateExpression(expression, a, b, null, null);
    }

    private Tensor evaluateExpression(String expression, String a, String b, String c) throws Exception {
        return evaluateExpression(expression, a, b, c, null);
    }

    private Tensor evaluateExpression(String expression, String a, String b, String c, String d) throws Exception {
        MapContext context = new MapContext();
        if (a != null) context.put("a", new TensorValue(Tensor.from(a)));
        if (b != null) context.put("b", new TensorValue(Tensor.from(b)));
        if (c != null) context.put("c", new TensorValue(Tensor.from(c)));
        if (d != null) context.put("d", new TensorValue(Tensor.from(d)));
        var transformContext = createTransformContext();
        var rankingExpression = new RankingExpression(expression);
        var transformed = new TokenTransformer().transform(rankingExpression, transformContext);
        for (var entry : transformContext.rankProfile().getFunctions().entrySet()) {
            context.put(entry.getKey(), entry.getValue().function().getBody().evaluate(context).asDouble());
        }
        return transformed.evaluate(context).asTensor();
    }

    private RankProfileTransformContext createTransformContext() throws ParseException {
        MockApplicationPackage application = (MockApplicationPackage) MockApplicationPackage.createEmpty();
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        QueryProfileRegistry queryProfileRegistry = application.getQueryProfiles();
        String sdContent = "search test {\n" +
                "  document test {}\n" +
                "  rank-profile my_profile inherits default {}\n" +
                "}";
        SearchBuilder searchBuilder = new SearchBuilder(application, rankProfileRegistry, queryProfileRegistry);
        searchBuilder.importString(sdContent);
        searchBuilder.build();
        Search search = searchBuilder.getSearch();
        RankProfile rp = rankProfileRegistry.get(search, "my_profile");
        return new RankProfileTransformContext(rp, queryProfileRegistry, Collections.EMPTY_MAP, null, Collections.EMPTY_MAP, Collections.EMPTY_MAP);
    }

}
