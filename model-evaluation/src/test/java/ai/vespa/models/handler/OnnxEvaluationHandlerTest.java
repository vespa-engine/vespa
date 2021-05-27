// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.handler;

import ai.vespa.models.evaluation.ModelsEvaluator;
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
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class OnnxEvaluationHandlerTest {

    private static HandlerTester handler;

    @BeforeClass
    static public void setUp() {
        handler = new HandlerTester(createModels("src/test/resources/config/onnx/"));
    }

    @Test
    public void testListModels() {
        String url = "http://localhost/model-evaluation/v1";
        String expected = "{\"one_layer\":\"http://localhost/model-evaluation/v1/one_layer\"," +
                           "\"add_mul\":\"http://localhost/model-evaluation/v1/add_mul\"," +
                           "\"no_model\":\"http://localhost/model-evaluation/v1/no_model\"}";
        handler.assertResponse(url, 200, expected);
    }

    @Test
    public void testModelInfo() {
        String url = "http://localhost/model-evaluation/v1/add_mul";
        String expected = "{\"model\":\"add_mul\",\"functions\":[" +
                "{\"function\":\"output1\"," +
                    "\"info\":\"http://localhost/model-evaluation/v1/add_mul/output1\"," +
                    "\"eval\":\"http://localhost/model-evaluation/v1/add_mul/output1/eval\"," +
                    "\"arguments\":[" +
                        "{\"name\":\"input1\",\"type\":\"tensor<float>(d0[1])\"}," +
                        "{\"name\":\"onnxModel(add_mul).output1\",\"type\":\"tensor<float>(d0[1])\"}," +
                        "{\"name\":\"input2\",\"type\":\"tensor<float>(d0[1])\"}" +
                    "]}," +
                "{\"function\":\"output2\"," +
                    "\"info\":\"http://localhost/model-evaluation/v1/add_mul/output2\"," +
                    "\"eval\":\"http://localhost/model-evaluation/v1/add_mul/output2/eval\"," +
                    "\"arguments\":[" +
                        "{\"name\":\"input1\",\"type\":\"tensor<float>(d0[1])\"}," +
                        "{\"name\":\"onnxModel(add_mul).output2\",\"type\":\"tensor<float>(d0[1])\"}," +
                        "{\"name\":\"input2\",\"type\":\"tensor<float>(d0[1])\"}" +
                "]}]}";
        handler.assertResponse(url, 200, expected);
    }

    @Test
    public void testEvaluationWithoutSpecifyingOutput() {
        String url = "http://localhost/model-evaluation/v1/add_mul/eval";
        String expected = "{\"error\":\"More than one function is available in model 'add_mul', but no name is given. Available functions: output1, output2\"}";
        handler.assertResponse(url, 404, expected);
    }

    @Test
    public void testEvaluationWithoutBindings() {
        String url = "http://localhost/model-evaluation/v1/add_mul/output1/eval";
        String expected = "{\"error\":\"Argument 'input2' must be bound to a value of type tensor<float>(d0[1])\"}";
        handler.assertResponse(url, 400, expected);
    }

    @Test
    public void testEvaluationOutput1() {
        Map<String, String> properties = new HashMap<>();
        properties.put("input1", "tensor<float>(d0[1]):[2]");
        properties.put("input2", "tensor<float>(d0[1]):[3]");
        String url = "http://localhost/model-evaluation/v1/add_mul/output1/eval";
        String expected = "{\"cells\":[{\"address\":{\"d0\":\"0\"},\"value\":6.0}]}";  // output1 is a mul
        handler.assertResponse(url, properties, 200, expected);
    }

    @Test
    public void testEvaluationOutput2() {
        Map<String, String> properties = new HashMap<>();
        properties.put("input1", "tensor<float>(d0[1]):[2]");
        properties.put("input2", "tensor<float>(d0[1]):[3]");
        String url = "http://localhost/model-evaluation/v1/add_mul/output2/eval";
        String expected = "{\"cells\":[{\"address\":{\"d0\":\"0\"},\"value\":5.0}]}";  // output2 is an add
        handler.assertResponse(url, properties, 200, expected);
    }

    @Test
    public void testBatchDimensionModelInfo() {
        String url = "http://localhost/model-evaluation/v1/one_layer";
        String expected = "{\"model\":\"one_layer\",\"functions\":[" +
                "{\"function\":\"output\"," +
                "\"info\":\"http://localhost/model-evaluation/v1/one_layer/output\"," +
                "\"eval\":\"http://localhost/model-evaluation/v1/one_layer/output/eval\"," +
                "\"arguments\":[" +
                "{\"name\":\"input\",\"type\":\"tensor<float>(d0[],d1[3])\"}," +
                "{\"name\":\"onnxModel(one_layer)\",\"type\":\"tensor<float>(d0[],d1[1])\"}" +
                "]}]}";
        handler.assertResponse(url, 200, expected);
    }

    @Test
    public void testBatchDimensionEvaluation() {
        Map<String, String> properties = new HashMap<>();
        properties.put("input", "tensor<float>(d0[],d1[3]):{{d0:0,d1:0}:0.1,{d0:0,d1:1}:0.2,{d0:0,d1:2}:0.3,{d0:1,d1:0}:0.4,{d0:1,d1:1}:0.5,{d0:1,d1:2}:0.6}");
        String url = "http://localhost/model-evaluation/v1/one_layer/eval";  // output not specified
        Tensor expected = Tensor.from("tensor<float>(d0[2],d1[1]):[0.6393113,0.67574286]");
        handler.assertResponse(url, properties, 200, expected);
    }

    static private ModelsEvaluator createModels(String path) {
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
