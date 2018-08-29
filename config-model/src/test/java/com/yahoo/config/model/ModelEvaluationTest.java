package com.yahoo.config.model;

import ai.vespa.models.evaluation.Model;
import ai.vespa.models.evaluation.ModelsEvaluator;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.search.RankProfilesConfig;
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

    private static final String appDir = "src/test/cfg/application/ml_serving";

    @After
    public void removeGeneratedModelFiles() {
        IOUtils.recursiveDeleteDir(Path.fromString(appDir).append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
    }

    @Test
    public void testMl_ServingApplication() throws SAXException, IOException {
        ApplicationPackageTester tester = ApplicationPackageTester.create(appDir);
        VespaModel model = new VespaModel(tester.app());
        ContainerCluster cluster = model.getContainerClusters().get("container");
        RankProfilesConfig.Builder b = new RankProfilesConfig.Builder();
        cluster.getConfig(b);
        RankProfilesConfig config = new RankProfilesConfig(b);
        assertEquals(3, config.rankprofile().size());
        Set<String> modelNames = config.rankprofile().stream().map(v -> v.name()).collect(Collectors.toSet());
        assertTrue(modelNames.contains("xgboost_2_2"));
        assertTrue(modelNames.contains("mnist_softmax"));
        assertTrue(modelNames.contains("mnist_softmax_saved"));

        ModelsEvaluator evaluator = new ModelsEvaluator(config);

        assertEquals(3, evaluator.models().size());
        Model xgboost = evaluator.models().get("xgboost_2_2");
        assertNotNull(xgboost);
        assertNotNull(xgboost.evaluatorOf());
        assertNotNull(xgboost.evaluatorOf("xgboost_2_2"));

        Model onnx = evaluator.models().get("mnist_softmax");
        assertNotNull(onnx);
        assertNotNull(onnx.evaluatorOf());
        assertNotNull(onnx.evaluatorOf("default"));
        assertNotNull(onnx.evaluatorOf("default", "add"));

        Model tensorflow = evaluator.models().get("mnist_softmax_saved");
        assertNotNull(tensorflow);
        assertNotNull(tensorflow.evaluatorOf());
        assertNotNull(tensorflow.evaluatorOf("serving_default"));
        assertNotNull(tensorflow.evaluatorOf("serving_default", "y"));
    }

}
