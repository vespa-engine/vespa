package com.yahoo.config.model;

import ai.vespa.models.evaluation.Model;
import ai.vespa.models.evaluation.ModelsEvaluator;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import org.junit.After;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class ModelEvaluationTest {

    private static final Path appDir = Path.fromString("src/test/cfg/application/ml_serving");

    @After
    public void removeGeneratedModelFiles() {
        IOUtils.recursiveDeleteDir(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
    }

    @Test
    public void testMl_ServingApplication() throws SAXException, IOException {
        ApplicationPackageTester tester = ApplicationPackageTester.create(appDir.toString());
        VespaModel model = new VespaModel(tester.app());
        assertHasMlModels(model);

        // At this point the expression is stored - copy application to another location which do not have a models dir
        Path storedAppDir = appDir.append("copy");
        try {
            storedAppDir.toFile().mkdirs();
            IOUtils.copy(appDir.append("services.xml").toString(), storedAppDir.append("services.xml").toString());
            IOUtils.copyDirectory(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                                  storedAppDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            ApplicationPackageTester storedTester = ApplicationPackageTester.create(storedAppDir.toString());
            VespaModel storedModel = new VespaModel(storedTester.app());
            assertHasMlModels(storedModel);
        }
        finally {
            IOUtils.recursiveDeleteDir(storedAppDir.toFile());
        }
    }

    private void assertHasMlModels(VespaModel model) {
        ContainerCluster cluster = model.getContainerClusters().get("container");

        RankProfilesConfig.Builder b = new RankProfilesConfig.Builder();
        cluster.getConfig(b);
        RankProfilesConfig config = new RankProfilesConfig(b);

        RankingConstantsConfig.Builder cb = new RankingConstantsConfig.Builder();
        cluster.getConfig(cb);
        RankingConstantsConfig constantsConfig = new RankingConstantsConfig(cb);

        assertEquals(4, config.rankprofile().size());
        Set<String> modelNames = config.rankprofile().stream().map(v -> v.name()).collect(Collectors.toSet());
        assertTrue(modelNames.contains("xgboost_2_2"));
        assertTrue(modelNames.contains("mnist_softmax"));
        assertTrue(modelNames.contains("mnist_softmax_saved"));

        ModelsEvaluator evaluator = new ModelsEvaluator(config, constantsConfig);

        assertEquals(4, evaluator.models().size());
        Model xgboost = evaluator.models().get("xgboost_2_2");
        assertNotNull(xgboost);
        assertNotNull(xgboost.evaluatorOf());
        assertNotNull(xgboost.evaluatorOf("xgboost_2_2"));

        Model onnx = evaluator.models().get("mnist_softmax");
        assertNotNull(onnx);
        assertNotNull(onnx.evaluatorOf());
        assertNotNull(onnx.evaluatorOf("default"));
        assertNotNull(onnx.evaluatorOf("default", "add"));
        assertNotNull(onnx.evaluatorOf("default.add"));
        assertNotNull(evaluator.evaluatorOf("mnist_softmax", "default.add"));
        assertNotNull(evaluator.evaluatorOf("mnist_softmax", "default", "add"));

        Model tensorflow = evaluator.models().get("mnist_softmax_saved");
        assertNotNull(tensorflow);
        assertNotNull(tensorflow.evaluatorOf());
        assertNotNull(tensorflow.evaluatorOf("serving_default"));
        assertNotNull(tensorflow.evaluatorOf("serving_default", "y"));
    }

}
