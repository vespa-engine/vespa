// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.ml;

import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import ai.vespa.models.evaluation.Model;
import ai.vespa.models.evaluation.ModelsEvaluator;
import ai.vespa.models.handler.ModelsEvaluationHandler;
import com.yahoo.component.ComponentId;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.filedistribution.fileacquirer.MockFileAcquirer;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests stateless model evaluation (turned on by the "model-evaluation" tag in "container")
 *
 * @author bratseth
 */
public class ModelEvaluationTest {

    /** Tests that we do not load models (which would waste memory) when not requested */
    @Test
    void testMl_serving_not_activated() {
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
    void testMl_serving() throws IOException {
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        Path appDir = Path.fromString("src/test/cfg/application/ml_serving");
        Path storedAppDir = appDir.append("copy");
        try {
            ImportedModelTester tester = new ImportedModelTester("ml_serving", appDir);
            assertHasMlModels(tester.createVespaModel(), appDir);

            // At this point the expression is stored - copy application to another location which do not have a models dir
            storedAppDir.toFile().mkdirs();
            IOUtils.copy(appDir.append("services.xml").toString(), storedAppDir.append("services.xml").toString());
            IOUtils.copyDirectory(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                    storedAppDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            ImportedModelTester storedTester = new ImportedModelTester("ml_serving", storedAppDir);
            assertHasMlModels(storedTester.createVespaModel(), appDir);
        }
        finally {
            IOUtils.recursiveDeleteDir(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            IOUtils.recursiveDeleteDir(storedAppDir.toFile());
        }
    }

    private void assertHasMlModels(VespaModel model, Path appDir) {
        ApplicationContainerCluster cluster = model.getContainerClusters().get("container");
        assertNotNull(cluster.getComponentsMap().get(new ComponentId(ModelsEvaluator.class.getName())));

        assertNotNull(cluster.getComponentsMap().get(new ComponentId(ModelsEvaluationHandler.class.getName())));
        assertTrue(cluster.getHandlers().stream()
                .anyMatch(h -> h.getComponentId().toString().equals(ModelsEvaluationHandler.class.getName())));

        RankProfilesConfig.Builder b = new RankProfilesConfig.Builder();
        cluster.getConfig(b);
        RankProfilesConfig config = new RankProfilesConfig(b);

        RankingConstantsConfig.Builder cb = new RankingConstantsConfig.Builder();
        cluster.getConfig(cb);
        RankingConstantsConfig constantsConfig = new RankingConstantsConfig(cb);

        RankingExpressionsConfig.Builder ce = new RankingExpressionsConfig.Builder();
        cluster.getConfig(ce);
        RankingExpressionsConfig expressionsConfig = ce.build();

        OnnxModelsConfig.Builder ob = new OnnxModelsConfig.Builder();
        cluster.getConfig(ob);
        OnnxModelsConfig onnxModelsConfig = new OnnxModelsConfig(ob);

        assertEquals(5, config.rankprofile().size());
        Set<String> modelNames = config.rankprofile().stream().map(v -> v.name()).collect(Collectors.toSet());
        assertTrue(modelNames.contains("xgboost_2_2"));
        assertTrue(modelNames.contains("lightgbm_regression"));
        assertTrue(modelNames.contains("add_mul"));
        assertTrue(modelNames.contains("small_constants_and_functions"));
        assertTrue(modelNames.contains("sqrt"));

        // Compare profile content in a denser format than config:
        StringBuilder sb = new StringBuilder();
        for (RankProfilesConfig.Rankprofile.Fef.Property p : findProfile("small_constants_and_functions", config).property())
            sb.append(p.name()).append(": ").append(p.value()).append("\n");
        assertEquals(profile, sb.toString());

        Map<String, File> fileMap = new HashMap<>();
        for (OnnxModelsConfig.Model onnxModel : onnxModelsConfig.model()) {
            fileMap.put(onnxModel.fileref().value(), appDir.append(onnxModel.fileref().value()).toFile());
        }
        FileAcquirer fileAcquirer = MockFileAcquirer.returnFiles(fileMap);
        ModelsEvaluator evaluator = new ModelsEvaluator(config, constantsConfig, expressionsConfig, onnxModelsConfig, fileAcquirer);

        assertEquals(5, evaluator.models().size());

        Model xgboost = evaluator.models().get("xgboost_2_2");
        assertNotNull(xgboost);
        assertNotNull(xgboost.evaluatorOf());
        assertNotNull(xgboost.evaluatorOf("xgboost_2_2"));

        Model lightgbm = evaluator.models().get("lightgbm_regression");
        assertNotNull(lightgbm);
        assertNotNull(lightgbm.evaluatorOf());
        assertNotNull(lightgbm.evaluatorOf("lightgbm_regression"));

        Model add_mul = evaluator.models().get("add_mul");
        assertNotNull(add_mul);
        assertEquals(2, add_mul.functions().size());
        assertNotNull(add_mul.evaluatorOf("output1"));
        assertNotNull(add_mul.evaluatorOf("output2"));
        assertNotNull(add_mul.evaluatorOf("default.output1"));
        assertNotNull(add_mul.evaluatorOf("default.output2"));
        assertNotNull(add_mul.evaluatorOf("default", "output1"));
        assertNotNull(add_mul.evaluatorOf("default", "output2"));
        assertNotNull(evaluator.evaluatorOf("add_mul", "output1"));
        assertNotNull(evaluator.evaluatorOf("add_mul", "output2"));
        assertNotNull(evaluator.evaluatorOf("add_mul", "default.output1"));
        assertNotNull(evaluator.evaluatorOf("add_mul", "default.output2"));
        assertNotNull(evaluator.evaluatorOf("add_mul", "default", "output1"));
        assertNotNull(evaluator.evaluatorOf("add_mul", "default", "output2"));
        assertEquals(TensorType.fromSpec("tensor<float>(d0[1])"), add_mul.functions().get(0).getArgumentType("input1"));
        assertEquals(TensorType.fromSpec("tensor<float>(d0[1])"), add_mul.functions().get(0).getArgumentType("input2"));

        Model sqrt = evaluator.models().get("sqrt");
        assertNotNull(sqrt);
        assertEquals(1, sqrt.functions().size());
        assertNotNull(sqrt.evaluatorOf());
        assertNotNull(sqrt.evaluatorOf("out_layer_1_1"));  // converted from "out/layer/1:1"
        assertNotNull(evaluator.evaluatorOf("sqrt"));
        assertNotNull(evaluator.evaluatorOf("sqrt", "out_layer_1_1"));
        assertEquals(TensorType.fromSpec("tensor<float>(d0[1])"), sqrt.functions().get(0).getArgumentType("input"));
    }

    private final String profile =
            "rankingExpression(output).rankingScript: onnx(small_constants_and_functions).output\n" +
            "rankingExpression(output).type: tensor<float>(d0[3])\n";

    private RankProfilesConfig.Rankprofile.Fef findProfile(String name, RankProfilesConfig config) {
        for (RankProfilesConfig.Rankprofile profile : config.rankprofile()) {
            if (profile.name().equals(name))
                return profile.fef();
        }
        throw new IllegalArgumentException("No profile named " + name);
    }

}
