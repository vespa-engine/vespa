// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.config.subscription.FileSource;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.filedistribution.fileacquirer.MockFileAcquirer;
import com.yahoo.path.Path;
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

/**
 * @author lesters
 */
public class OnnxEvaluatorTest {

    private static final double delta = 0.00000000001;

    @Test
    public void testOnnxEvaluation() {
        ModelsEvaluator models = createModels("src/test/resources/config/onnx/");

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

    private ModelsEvaluator createModels(String path) {
        Path configDir = Path.fromString(path);
        RankProfilesConfig config = new ConfigGetter<>(new FileSource(configDir.append("rank-profiles.cfg").toFile()),
                                                       RankProfilesConfig.class).getConfig("");
        RankingConstantsConfig constantsConfig = new ConfigGetter<>(new FileSource(configDir.append("ranking-constants.cfg").toFile()),
                                                                    RankingConstantsConfig.class).getConfig("");
        RankingExpressionsConfig expressionsConfig = new ConfigGetter<>(new FileSource(configDir.append("ranking-expressions.cfg").toFile()),
                                                                        RankingExpressionsConfig.class).getConfig("");
        OnnxModelsConfig onnxModelsConfig = new ConfigGetter<>(new FileSource(configDir.append("onnx-models.cfg").toFile()),
                                                               OnnxModelsConfig.class).getConfig("");

        Map<String, File> fileMap = new HashMap<>();
        for (OnnxModelsConfig.Model onnxModel : onnxModelsConfig.model()) {
            fileMap.put(onnxModel.fileref().value(), new File(path + onnxModel.fileref().value()));
        }
        FileAcquirer fileAcquirer = MockFileAcquirer.returnFiles(fileMap);

        return new ModelsEvaluator(config, constantsConfig, expressionsConfig, onnxModelsConfig, fileAcquirer);
    }

}
