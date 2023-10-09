// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.ml;

import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import ai.vespa.models.evaluation.FunctionEvaluator;
import ai.vespa.models.evaluation.Model;
import ai.vespa.models.evaluation.ModelsEvaluator;
import com.yahoo.component.ComponentId;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.filedistribution.fileacquirer.MockFileAcquirer;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.tensor.Tensor;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests stateless model evaluation (turned on by the "model-evaluation" tag in "container")
 * for ONNX models.
 *
 * @author lesters
 */
public class StatelessOnnxEvaluationTest {

    @Test
    void testStatelessOnnxModelNameCollision() {
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        Path appDir = Path.fromString("src/test/cfg/application/onnx_name_collision");
        try {
            ImportedModelTester tester = new ImportedModelTester("onnx", appDir);
            VespaModel model = tester.createVespaModel();
            ApplicationContainerCluster cluster = model.getContainerClusters().get("container");
            RankProfilesConfig.Builder b = new RankProfilesConfig.Builder();
            cluster.getConfig(b);
            RankProfilesConfig config = new RankProfilesConfig(b);
            assertEquals(2, config.rankprofile().size());

            Set<String> modelNames = config.rankprofile().stream().map(v -> v.name()).collect(Collectors.toSet());
            assertTrue(modelNames.contains("foobar"));
            assertTrue(modelNames.contains("barfoo"));
        } finally {
            IOUtils.recursiveDeleteDir(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
        }
    }

    @Test
    void testStatelessOnnxModelEvaluation() throws Exception {
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        Path appDir = Path.fromString("src/test/cfg/application/onnx");
        Path storedAppDir = appDir.append("copy");
        try {
            ImportedModelTester tester = new ImportedModelTester("onnx_rt", appDir, new DeployState.Builder());
            assertModelEvaluation(tester.createVespaModel(), appDir, false);

            // At this point the expression is stored - copy application to another location which does not have a models dir
            storedAppDir.toFile().mkdirs();
            IOUtils.copy(appDir.append("services.xml").toString(), storedAppDir.append("services.xml").toString());
            IOUtils.copyDirectory(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                                  storedAppDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            IOUtils.copyDirectory(appDir.append(ApplicationPackage.SCHEMAS_DIR).toFile(),
                                  storedAppDir.append(ApplicationPackage.SCHEMAS_DIR).toFile());
            ImportedModelTester storedTester = new ImportedModelTester("onnx_rt", storedAppDir);
            assertModelEvaluation(storedTester.createVespaModel(), appDir, false);

        } finally {
            IOUtils.recursiveDeleteDir(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            IOUtils.recursiveDeleteDir(storedAppDir.toFile());
        }
    }

    @Test
    void testStatelessOnnxModelEvaluationWithGpu() {
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        NodeResources resources = new NodeResources(4, 16, 125, 10,
                                                    NodeResources.DiskSpeed.fast, NodeResources.StorageType.local,
                                                    NodeResources.Architecture.x86_64,
                                                    new NodeResources.GpuResources(1, 16));
        InMemoryProvisioner provisioner = new InMemoryProvisioner(6, resources, false);
        DeployState.Builder deployStateBuilder = new DeployState.Builder().modelHostProvisioner(provisioner)
                                                                          .properties(new TestProperties().setMultitenant(true).setHostedVespa(true));
        Path appDir = Path.fromString("src/test/cfg/application/onnx");
        try {
            ImportedModelTester tester = new ImportedModelTester("onnx_rt", appDir, deployStateBuilder);
            assertModelEvaluation(tester.createVespaModel(), appDir, true);
        } finally {
            IOUtils.recursiveDeleteDir(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
        }
    }

    private void assertModelEvaluation(VespaModel model, Path appDir, boolean shouldRequireGpu) {
        ApplicationContainerCluster cluster = model.getContainerClusters().get("container");
        assertNotNull(cluster.getComponentsMap().get(new ComponentId(ModelsEvaluator.class.getName())));

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

        assertEquals(1, config.rankprofile().size());
        Set<String> modelNames = config.rankprofile().stream().map(v -> v.name()).collect(Collectors.toSet());
        assertTrue(modelNames.contains("mul"));

        OnnxModelsConfig.Model mulModel = onnxModelsConfig.model().get(0);
        assertEquals(2, mulModel.stateless_intraop_threads());
        assertEquals(-1, mulModel.stateless_interop_threads());
        assertEquals("", mulModel.stateless_execution_mode());
        assertEquals(shouldRequireGpu, mulModel.gpu_device_required());
        assertEquals(0, mulModel.gpu_device());

        // This is actually how ModelsEvaluator is injected
        Map<String, File> fileMap = new HashMap<>();
        for (OnnxModelsConfig.Model onnxModel : onnxModelsConfig.model()) {
            fileMap.put(onnxModel.fileref().value(), appDir.append(onnxModel.fileref().value()).toFile());
        }
        FileAcquirer fileAcquirer = MockFileAcquirer.returnFiles(fileMap);
        try {
            ModelsEvaluator modelsEvaluator = new ModelsEvaluator(config, constantsConfig, expressionsConfig, onnxModelsConfig, fileAcquirer);
            assertEquals(1, modelsEvaluator.models().size());

            Model mul = modelsEvaluator.models().get("mul");
            FunctionEvaluator evaluator = mul.evaluatorOf();  // or "default.output" - or actually use name of model output

            Tensor input1 = Tensor.from("tensor<float>(d0[1]):[2]");
            Tensor input2 = Tensor.from("tensor<float>(d0[1]):[3]");
            Tensor output = evaluator.bind("input1", input1).bind("input2", input2).evaluate();
            assertEquals(6.0, output.sum().asDouble(), 1e-9);
        } catch (IllegalArgumentException e) {
            boolean gotWantedException = false;
            for (Throwable cause = e; shouldRequireGpu && cause != null; cause = cause.getCause()) {
                if (cause.getMessage().equals("GPU device is required, but CUDA initialization failed")) {
                    gotWantedException = true;
                    break;
                }
            }
            if (!gotWantedException) {
                throw e;
            }
        }
    }

}
