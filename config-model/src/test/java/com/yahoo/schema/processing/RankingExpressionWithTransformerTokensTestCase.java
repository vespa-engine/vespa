// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.expressiontransforms.RankProfileTransformContext;
import com.yahoo.schema.expressiontransforms.TokenTransformer;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.tensor.Tensor;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RankingExpressionWithTransformerTokensTestCase {

    @Test
    void testTokenInputIds() throws Exception {
        String expected = "tensor(d0[1],d1[12]):[101,1,2,102,3,4,5,102,6,7,102,0]";
        String a = "tensor(d0[2]):[1,2]";
        String b = "tensor(d0[3]):[3,4,5]";
        String c = "tensor(d0[2]):[6,7]";
        String expression = "tokenInputIds(12, a, b, c)";
        Tensor result = evaluateExpression(expression, a, b, c);
        assertEquals(Tensor.from(expected), result);
    }

    @Test
    void testTokenTypeIds() throws Exception {
        String expected = "tensor(d0[1],d1[10]):[0,0,0,0,1,1,1,1,0,0]";
        String a = "tensor(d0[2]):[1,2]";
        String b = "tensor(d0[3]):[3,4,5]";
        String expression = "tokenTypeIds(10, a, b)";
        Tensor result = evaluateExpression(expression, a, b);
        assertEquals(Tensor.from(expected), result);
    }

    @Test
    void testAttentionMask() throws Exception {
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
        ApplicationBuilder schemaBuilder = new ApplicationBuilder(application, new MockFileRegistry(), new BaseDeployLogger(), new TestProperties(), rankProfileRegistry, queryProfileRegistry);
        schemaBuilder.addSchema(sdContent);
        schemaBuilder.build(true);
        Schema schema = schemaBuilder.getSchema();
        RankProfile rp = rankProfileRegistry.get(schema, "my_profile");
        return new RankProfileTransformContext(rp, queryProfileRegistry, Collections.emptyMap(), null, Collections.emptyMap(), Collections.emptyMap());
    }

}
