// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.filedistribution.fileacquirer.MockFileAcquirer;
import com.yahoo.tensor.Tensor;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * @author lesters
 */
public class OnnxEvaluatorTest {

    private static final double delta = 0.00000000001;
    private static final String CONFIG_DIR = "src/test/resources/config/onnx/";

    @Test
    public void testOnnxEvaluation() {
        assumeTrue(OnnxEvaluator.isRuntimeAvailable());
        ModelsEvaluator models = createModels();

        assertTrue(models.models().containsKey("add_mul"));
        assertTrue(models.models().containsKey("one_layer"));

        FunctionEvaluator function = models.evaluatorOf("add_mul", "output1");
        function.bind("input1", Tensor.from("tensor<float>(d0[1]):[2]"));
        function.bind("input2", Tensor.from("tensor<float>(d0[1]):[3]"));
        assertEquals(6.0, function.evaluate().sum().asDouble(), delta);

        function = models.evaluatorOf("add_mul", "output2");
        function.bind("input1", Tensor.from("tensor<float>(d0[1]):[2]"));
        function.bind("input2", Tensor.from("tensor<float>(d0[1]):[3]"));
        assertEquals(5.0, function.evaluate().sum().asDouble(), delta);

        function = models.evaluatorOf("one_layer");
        function.bind("input", Tensor.from("tensor<float>(d0[2],d1[3]):[[0.1, 0.2, 0.3],[0.4,0.5,0.6]]"));
        assertEquals(function.evaluate(), Tensor.from("tensor<float>(d0[2],d1[1]):[0.63931,0.67574]"));
    }

    @SuppressWarnings("deprecation")
    private ModelsEvaluator createModels() {
        RankProfilesConfig config = ConfigGetter.getConfig(RankProfilesConfig.class, fileConfigId("rank-profiles.cfg"));
        RankingConstantsConfig constantsConfig = ConfigGetter.getConfig(RankingConstantsConfig.class, fileConfigId("ranking-constants.cfg"));
        RankingExpressionsConfig expressionsConfig = ConfigGetter.getConfig(RankingExpressionsConfig.class, fileConfigId("ranking-expressions.cfg"));
        OnnxModelsConfig onnxModelsConfig = ConfigGetter.getConfig(OnnxModelsConfig.class, fileConfigId("onnx-models.cfg"));

        Map<String, File> fileMap = new HashMap<>();
        for (OnnxModelsConfig.Model onnxModel : onnxModelsConfig.model()) {
            fileMap.put(onnxModel.fileref().value(), new File(CONFIG_DIR + onnxModel.fileref().value()));
        }
        FileAcquirer fileAcquirer = MockFileAcquirer.returnFiles(fileMap);

        return new ModelsEvaluator(config, constantsConfig, expressionsConfig, onnxModelsConfig, fileAcquirer);
    }

    private static String fileConfigId(String filename) {
        return "file:" + CONFIG_DIR + filename;
    }

}
