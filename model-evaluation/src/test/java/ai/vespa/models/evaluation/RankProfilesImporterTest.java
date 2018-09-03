// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.config.subscription.FileSource;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import org.junit.Test;

import java.io.File;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests instantiating models from rank-profiles configs.
 *
 * @author bratseth
 */
public class RankProfilesImporterTest {

    @Test
    public void testImportingModels() {
        String configPath = "src/test/resources/config/models/rank-profiles.cfg";
        RankProfilesConfig config = new ConfigGetter<>(new FileSource(new File(configPath)), RankProfilesConfig.class).getConfig("");
        Map<String, Model> models = new RankProfilesConfigImporter().importFrom(config);
        assertEquals(4, models.size());

        Model xgboost = models.get("xgboost_2_2");
        assertFunction("xgboost_2_2",
                       "(optimized sum of condition trees of size 192 bytes)",
                       xgboost);

        Model onnxMnistSoftmax = models.get("mnist_softmax");
        assertFunction("default.add",
                       "join(reduce(join(rename(Placeholder, (d0, d1), (d0, d2)), constant(mnist_softmax_Variable), f(a,b)(a * b)), sum, d2), constant(mnist_softmax_Variable_1), f(a,b)(a + b))",
                       onnxMnistSoftmax);

        Model tfMnistSoftmax = models.get("mnist_softmax_saved");
        assertFunction("serving_default.y",
                       "join(reduce(join(rename(Placeholder, (d0, d1), (d0, d2)), constant(mnist_softmax_saved_layer_Variable_read), f(a,b)(a * b)), sum, d2), constant(mnist_softmax_saved_layer_Variable_1_read), f(a,b)(a + b))",
                       tfMnistSoftmax);

        Model tfMnist = models.get("mnist_saved");
        assertFunction("serving_default.y",
                       "join(reduce(join(map(join(reduce(join(join(join(rankingExpression(imported_ml_macro_mnist_saved_dnn_hidden1_add), 0.009999999776482582, f(a,b)(a * b)), rankingExpression(imported_ml_macro_mnist_saved_dnn_hidden1_add), f(a,b)(max(a,b))), constant(mnist_saved_dnn_hidden2_weights_read), f(a,b)(a * b)), sum, d3), constant(mnist_saved_dnn_hidden2_bias_read), f(a,b)(a + b)), f(a)(1.050701 * if (a >= 0, a, 1.673263 * (exp(a) - 1)))), constant(mnist_saved_dnn_outputs_weights_read), f(a,b)(a * b)), sum, d2), constant(mnist_saved_dnn_outputs_bias_read), f(a,b)(a + b))",
                       tfMnist);
    }

    @Test
    public void testImportingRankExpressions() {
        String configPath = "src/test/resources/config/rankexpression/rank-profiles.cfg";
        RankProfilesConfig config = new ConfigGetter<>(new FileSource(new File(configPath)), RankProfilesConfig.class).getConfig("");
        Map<String, Model> models = new RankProfilesConfigImporter().importFrom(config);
        assertEquals(18, models.size());

        Model macros = models.get("macros");
        assertEquals("macros", macros.name());
        assertEquals(4, macros.functions().size());
        assertFunction("fourtimessum", "4 * (var1 + var2)", macros);
        assertFunction("firstphase", "match + fieldMatch(title) + rankingExpression(myfeature)", macros);
        assertFunction("secondphase", "rankingExpression(fourtimessum@5cf279212355b980.67f1e87166cfef86)", macros);
        assertFunction("myfeature",
                       "70 * fieldMatch(title).completeness * pow(0 - fieldMatch(title).earliness,2) + " +
                       "30 * pow(0 - fieldMatch(description).earliness,2)",
                       macros);
        assertEquals(4, macros.referencedFunctions().size());
        assertBoundFunction("rankingExpression(fourtimessum@5cf279212355b980.67f1e87166cfef86)",
                            "4 * (match + rankBoost)", macros);
    }

    private void assertFunction(String name, String expression, Model model) {
        assertNotNull("Model is present in config", model);
        ExpressionFunction function = model.function(name);
        assertNotNull("Function '" + name + "' is in " + model, function);
        assertEquals(name, function.getName());
        assertEquals(expression, function.getBody().getRoot().toString());
    }

    private void assertBoundFunction(String name, String expression, Model model) {
        ExpressionFunction function = model.referencedFunctions().get(FunctionReference.fromSerial(name).get());
        assertNotNull("Function '" + name + "' is present", function);
        assertEquals(name, function.getName());
        assertEquals(expression, function.getBody().getRoot().toString());
    }

}
