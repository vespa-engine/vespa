// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.ml;

import ai.vespa.models.evaluation.Model;
import ai.vespa.models.evaluation.ModelsEvaluator;
import ai.vespa.models.evaluation.RankProfilesConfigImporter;
import ai.vespa.models.handler.ModelsEvaluationHandler;
import com.yahoo.component.ComponentId;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.filedistribution.fileacquirer.MockFileAcquirer;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests stateless model evaluation (turned on by the "model-evaluation" tag in "container")
 *
 * @author bratseth
 */
public class ModelEvaluationTest {

    /** Tests that we do not load models (which would waste memory) when not requested */
    @Test
    public void testMl_serving_not_activated() {
        Path appDir = Path.fromString("src/test/cfg/application/ml_serving_not_activated");
        try {
            ImportedModelTester tester = new ImportedModelTester("ml_serving", appDir);
            VespaModel model = tester.createVespaModel();
            ApplicationContainerCluster cluster = model.getContainerClusters().get("container");
            assertNull(cluster.getComponentsMap().get(new ComponentId(ModelsEvaluator.class.getName())));

            RankProfilesConfig.Builder b = new RankProfilesConfig.Builder();
            cluster.getConfig(b);
            RankProfilesConfig config = new RankProfilesConfig(b);

            assertEquals(0, config.rankprofile().size());
        }
        finally {
            IOUtils.recursiveDeleteDir(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
        }
    }

    @Test
    public void testMl_serving() throws IOException {
        Path appDir = Path.fromString("src/test/cfg/application/ml_serving");
        Path storedAppDir = appDir.append("copy");
        try {
            ImportedModelTester tester = new ImportedModelTester("ml_serving", appDir);
            assertHasMlModels(tester.createVespaModel());

            // At this point the expression is stored - copy application to another location which do not have a models dir
            storedAppDir.toFile().mkdirs();
            IOUtils.copy(appDir.append("services.xml").toString(), storedAppDir.append("services.xml").toString());
            IOUtils.copyDirectory(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                                  storedAppDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            ImportedModelTester storedTester = new ImportedModelTester("ml_serving", storedAppDir);
            assertHasMlModels(storedTester.createVespaModel());
        }
        finally {
            IOUtils.recursiveDeleteDir(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            IOUtils.recursiveDeleteDir(storedAppDir.toFile());
        }
    }

    private void assertHasMlModels(VespaModel model) {
        ApplicationContainerCluster cluster = model.getContainerClusters().get("container");
        assertNotNull(cluster.getComponentsMap().get(new ComponentId(ModelsEvaluator.class.getName())));

        assertNotNull(cluster.getComponentsMap().get(new ComponentId(ModelsEvaluationHandler.class.getName())));
        assertTrue(cluster.getHandlers().stream()
                .anyMatch(h -> h.getComponentId().toString().equals(ModelsEvaluationHandler.class.getName())));

        RankProfilesConfig.Builder b = new RankProfilesConfig.Builder();
        cluster.getConfig(b);
        RankProfilesConfig config = new RankProfilesConfig(b);
        // System.out.println(config);

        RankingConstantsConfig.Builder cb = new RankingConstantsConfig.Builder();
        cluster.getConfig(cb);
        RankingConstantsConfig constantsConfig = new RankingConstantsConfig(cb);

        assertEquals(4, config.rankprofile().size());
        Set<String> modelNames = config.rankprofile().stream().map(v -> v.name()).collect(Collectors.toSet());
        assertTrue(modelNames.contains("xgboost_2_2"));
        assertTrue(modelNames.contains("mnist_saved"));
        assertTrue(modelNames.contains("mnist_softmax"));
        assertTrue(modelNames.contains("mnist_softmax_saved"));

        // Compare profile content in a denser format than config:
        StringBuilder sb = new StringBuilder();
        for (RankProfilesConfig.Rankprofile.Fef.Property p : findProfile("mnist_saved", config).property())
            sb.append(p.name()).append(": ").append(p.value()).append("\n");
        assertEquals(mnistProfile, sb.toString());

        ModelsEvaluator evaluator = new ModelsEvaluator(new ToleratingMissingConstantFilesRankProfilesConfigImporter(MockFileAcquirer.returnFile(null))
                                                                .importFrom(config, constantsConfig));

        assertEquals(4, evaluator.models().size());

        Model xgboost = evaluator.models().get("xgboost_2_2");
        assertNotNull(xgboost);
        assertNotNull(xgboost.evaluatorOf());
        assertNotNull(xgboost.evaluatorOf("xgboost_2_2"));

        Model tensorflow_mnist = evaluator.models().get("mnist_saved");
        assertNotNull(tensorflow_mnist);
        assertEquals(1, tensorflow_mnist.functions().size());
        assertNotNull(tensorflow_mnist.evaluatorOf("serving_default"));
        assertNotNull(tensorflow_mnist.evaluatorOf("serving_default", "y"));
        assertNotNull(tensorflow_mnist.evaluatorOf("serving_default.y"));
        assertNotNull(evaluator.evaluatorOf("mnist_saved", "serving_default.y"));
        assertNotNull(evaluator.evaluatorOf("mnist_saved", "serving_default", "y"));
        assertEquals(TensorType.fromSpec("tensor(d0[],d1[784])"), tensorflow_mnist.functions().get(0).argumentTypes().get("input"));

        Model onnx_mnist_softmax = evaluator.models().get("mnist_softmax");
        assertNotNull(onnx_mnist_softmax);
        assertEquals(1, onnx_mnist_softmax.functions().size());
        assertNotNull(onnx_mnist_softmax.evaluatorOf());
        assertNotNull(onnx_mnist_softmax.evaluatorOf("default"));
        assertNotNull(onnx_mnist_softmax.evaluatorOf("default", "add"));
        assertNotNull(onnx_mnist_softmax.evaluatorOf("default.add"));
        assertNotNull(evaluator.evaluatorOf("mnist_softmax", "default.add"));
        assertNotNull(evaluator.evaluatorOf("mnist_softmax", "default", "add"));
        assertEquals(TensorType.fromSpec("tensor<float>(d0[],d1[784])"), onnx_mnist_softmax.functions().get(0).argumentTypes().get("Placeholder"));

        Model tensorflow_mnist_softmax = evaluator.models().get("mnist_softmax_saved");
        assertNotNull(tensorflow_mnist_softmax);
        assertEquals(1, tensorflow_mnist_softmax.functions().size());
        assertNotNull(tensorflow_mnist_softmax.evaluatorOf());
        assertNotNull(tensorflow_mnist_softmax.evaluatorOf("serving_default"));
        assertNotNull(tensorflow_mnist_softmax.evaluatorOf("serving_default", "y"));
        assertEquals(TensorType.fromSpec("tensor(d0[],d1[784])"), tensorflow_mnist_softmax.functions().get(0).argumentTypes().get("Placeholder"));
    }

    private final String mnistProfile =
            "rankingExpression(imported_ml_function_mnist_saved_dnn_hidden1_add).rankingScript: join(reduce(join(rename(input, (d0, d1), (d0, d4)), constant(mnist_saved_dnn_hidden1_weights_read), f(a,b)(a * b)), sum, d4), constant(mnist_saved_dnn_hidden1_bias_read), f(a,b)(a + b))\n" +
            "rankingExpression(imported_ml_function_mnist_saved_dnn_hidden1_add).type: tensor(d3[300])\n" +
            "rankingExpression(serving_default.y).rankingScript: join(reduce(join(map(join(reduce(join(join(join(rankingExpression(imported_ml_function_mnist_saved_dnn_hidden1_add), 0.009999999776482582, f(a,b)(a * b)), rankingExpression(imported_ml_function_mnist_saved_dnn_hidden1_add), f(a,b)(max(a,b))), constant(mnist_saved_dnn_hidden2_weights_read), f(a,b)(a * b)), sum, d3), constant(mnist_saved_dnn_hidden2_bias_read), f(a,b)(a + b)), f(a)(1.0507009873554805 * if (a >= 0, a, 1.6732632423543772 * (exp(a) - 1)))), constant(mnist_saved_dnn_outputs_weights_read), f(a,b)(a * b)), sum, d2), constant(mnist_saved_dnn_outputs_bias_read), f(a,b)(a + b))\n" +
            "rankingExpression(serving_default.y).input.type: tensor(d0[],d1[784])\n" +
            "rankingExpression(serving_default.y).type: tensor(d0[],d1[10])\n";

    private RankProfilesConfig.Rankprofile.Fef findProfile(String name, RankProfilesConfig config) {
        for (RankProfilesConfig.Rankprofile profile : config.rankprofile()) {
            if (profile.name().equals(name))
                return profile.fef();
        }
        throw new IllegalArgumentException("No profile named " + name);
    }

    // We don't have function file distribution so just return empty tensor constants
    private static class ToleratingMissingConstantFilesRankProfilesConfigImporter extends RankProfilesConfigImporter {

        public ToleratingMissingConstantFilesRankProfilesConfigImporter(FileAcquirer fileAcquirer) {
            super(fileAcquirer);
        }

        protected Tensor readTensorFromFile(String name, TensorType type, FileReference fileReference) {
            return Tensor.from(type, "{}");
        }

    }

}
