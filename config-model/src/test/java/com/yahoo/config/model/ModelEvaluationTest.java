// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import ai.vespa.models.evaluation.Model;
import ai.vespa.models.evaluation.ModelsEvaluator;
import ai.vespa.models.evaluation.RankProfilesConfigImporter;
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
import com.yahoo.vespa.model.container.ContainerCluster;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class ModelEvaluationTest {

    @Test
    public void testMl_serving() throws SAXException, IOException {
        Path appDir = Path.fromString("src/test/cfg/application/ml_serving");
        Path storedAppDir = appDir.append("copy");
        try {
            ApplicationPackageTester tester = ApplicationPackageTester.create(appDir.toString());
            VespaModel model = new VespaModel(tester.app());
            assertHasMlModels(model);

            // At this point the expression is stored - copy application to another location which do not have a models dir
            storedAppDir.toFile().mkdirs();
            IOUtils.copy(appDir.append("services.xml").toString(), storedAppDir.append("services.xml").toString());
            IOUtils.copyDirectory(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                                  storedAppDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            ApplicationPackageTester storedTester = ApplicationPackageTester.create(storedAppDir.toString());
            VespaModel storedModel = new VespaModel(storedTester.app());
            assertHasMlModels(storedModel);
        }
        finally {
            IOUtils.recursiveDeleteDir(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            IOUtils.recursiveDeleteDir(storedAppDir.toFile());
        }
    }

    /** Tests that we do not load models (which will waste memory) when not requested */
    @Test
    public void testMl_serving_not_activated() throws SAXException, IOException {
        Path appDir = Path.fromString("src/test/cfg/application/ml_serving_not_activated");
        ApplicationPackageTester tester = ApplicationPackageTester.create(appDir.toString());
        VespaModel model = new VespaModel(tester.app());
        ContainerCluster cluster = model.getContainerClusters().get("container");
        assertNull(cluster.getComponentsMap().get(new ComponentId(ModelsEvaluator.class.getName())));

        RankProfilesConfig.Builder b = new RankProfilesConfig.Builder();
        cluster.getConfig(b);
        RankProfilesConfig config = new RankProfilesConfig(b);

        assertEquals(0, config.rankprofile().size());
    }

    private void assertHasMlModels(VespaModel model) {
        ContainerCluster cluster = model.getContainerClusters().get("container");
        assertNotNull(cluster.getComponentsMap().get(new ComponentId(ModelsEvaluator.class.getName())));

        RankProfilesConfig.Builder b = new RankProfilesConfig.Builder();
        cluster.getConfig(b);
        RankProfilesConfig config = new RankProfilesConfig(b);

        RankingConstantsConfig.Builder cb = new RankingConstantsConfig.Builder();
        cluster.getConfig(cb);
        RankingConstantsConfig constantsConfig = new RankingConstantsConfig(cb);

        assertEquals(4, config.rankprofile().size());
        Set<String> modelNames = config.rankprofile().stream().map(v -> v.name()).collect(Collectors.toSet());
        assertTrue(modelNames.contains("xgboost_2_2"));
        assertTrue(modelNames.contains("mnist_saved"));
        assertTrue(modelNames.contains("mnist_softmax"));
        assertTrue(modelNames.contains("mnist_softmax_saved"));

        ModelsEvaluator evaluator = new ModelsEvaluator(new ToleratingMissingConstantFilesRankProfilesConfigImporter(MockFileAcquirer.returnFile(null))
                                                                .importFrom(config, constantsConfig));

        assertEquals(4, evaluator.models().size());

        Model xgboost = evaluator.models().get("xgboost_2_2");
        assertNotNull(xgboost);
        assertNotNull(xgboost.evaluatorOf());
        assertNotNull(xgboost.evaluatorOf("xgboost_2_2"));

        Model tensorflow_mnist = evaluator.models().get("mnist_saved");
        assertNotNull(tensorflow_mnist);
        assertNotNull(tensorflow_mnist.evaluatorOf("serving_default"));
        assertNotNull(tensorflow_mnist.evaluatorOf("serving_default", "y"));
        assertNotNull(tensorflow_mnist.evaluatorOf("serving_default.y"));
        assertNotNull(evaluator.evaluatorOf("mnist_saved", "serving_default.y"));
        assertNotNull(evaluator.evaluatorOf("mnist_saved", "serving_default", "y"));

        Model onnx_mnist_softmax = evaluator.models().get("mnist_softmax");
        assertNotNull(onnx_mnist_softmax);
        assertNotNull(onnx_mnist_softmax.evaluatorOf());
        assertNotNull(onnx_mnist_softmax.evaluatorOf("default"));
        assertNotNull(onnx_mnist_softmax.evaluatorOf("default", "add"));
        assertNotNull(onnx_mnist_softmax.evaluatorOf("default.add"));
        assertNotNull(evaluator.evaluatorOf("mnist_softmax", "default.add"));
        assertNotNull(evaluator.evaluatorOf("mnist_softmax", "default", "add"));

        Model tensorflow_mnist_softmax = evaluator.models().get("mnist_softmax_saved");
        assertNotNull(tensorflow_mnist_softmax);
        assertNotNull(tensorflow_mnist_softmax.evaluatorOf());
        assertNotNull(tensorflow_mnist_softmax.evaluatorOf("serving_default"));
        assertNotNull(tensorflow_mnist_softmax.evaluatorOf("serving_default", "y"));
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
