// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.handler;

import ai.vespa.models.evaluation.ModelTester;
import ai.vespa.models.evaluation.ModelsEvaluator;
import ai.vespa.models.evaluation.RankProfilesConfigImporterWithMockedConstants;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.config.subscription.FileSource;
import com.yahoo.filedistribution.fileacquirer.MockFileAcquirer;
import com.yahoo.path.Path;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class ModelsEvaluationHandlerTest {

    private static HandlerTester handler;

    @BeforeClass
    static public void setUp() {
        handler = new HandlerTester(createModels("src/test/resources/config/models/"));
    }

    @Test
    public void testUnknownAPI() {
        handler.assertResponse("http://localhost/wrong-api-binding", 404);
    }

    @Test
    public void testUnknownVersion() {
        handler.assertResponse("http://localhost/model-evaluation/v0", 404);
    }

    @Test
    public void testNonExistingModel() {
        handler.assertResponse("http://localhost/model-evaluation/v1/non-existing-model", 404);
    }

    @Test
    public void testListModels() {
        String url = "http://localhost/model-evaluation/v1";
        String expected =
                "{\"mnist_softmax\":\"http://localhost/model-evaluation/v1/mnist_softmax\",\"mnist_saved\":\"http://localhost/model-evaluation/v1/mnist_saved\",\"mnist_softmax_saved\":\"http://localhost/model-evaluation/v1/mnist_softmax_saved\",\"xgboost_2_2\":\"http://localhost/model-evaluation/v1/xgboost_2_2\",\"lightgbm_regression\":\"http://localhost/model-evaluation/v1/lightgbm_regression\"}";
        handler.assertResponse(url, 200, expected);
    }

    @Test
    public void testXgBoostEvaluationWithoutBindings() {
        String url = "http://localhost/model-evaluation/v1/xgboost_2_2/eval";  // only has a single function
        String expected = "{\"cells\":[{\"address\":{},\"value\":-4.376589999999999}]}";
        handler.assertResponse(url, 200, expected);
    }

    @Test
    public void testXgBoostEvaluationWithBindings() {
        Map<String, String> properties = new HashMap<>();
        properties.put("f29", "-1.0");
        properties.put("f56", "0.2");
        properties.put("f60", "0.3");
        properties.put("f109", "0.4");
        properties.put("non-existing-binding", "-1");
        String url = "http://localhost/model-evaluation/v1/xgboost_2_2/eval";
        String expected = "{\"cells\":[{\"address\":{},\"value\":-7.936679999999999}]}";
        handler.assertResponse(url, properties, 200, expected);
    }

    @Test
    public void testXgBoostEvaluationWithMissingValue() {
        Map<String, String> properties = new HashMap<>();
        properties.put("missing-value", "-1.0");
        properties.put("f56", "0.2");
        properties.put("f60", "0.3");
        properties.put("f109", "0.4");
        properties.put("non-existing-binding", "-1");
        String url = "http://localhost/model-evaluation/v1/xgboost_2_2/eval";
        String expected = "{\"cells\":[{\"address\":{},\"value\":-7.936679999999999}]}";
        handler.assertResponse(url, properties, 200, expected);
    }

    @Test
    public void testLightGBMEvaluationWithoutBindings() {
        String url = "http://localhost/model-evaluation/v1/lightgbm_regression/eval";
        String expected = "{\"cells\":[{\"address\":{},\"value\":1.9130086820218188}]}";
        handler.assertResponse(url, 200, expected);
    }

    @Test
    public void testLightGBMEvaluationWithBindings() {
        Map<String, String> properties = new HashMap<>();
        properties.put("numerical_1", "0.1");
        properties.put("numerical_2", "0.2");
        properties.put("categorical_1", "a");
        properties.put("categorical_2", "i");
        properties.put("non-existing-binding", "-1");
        String url = "http://localhost/model-evaluation/v1/lightgbm_regression/eval";
        String expected = "{\"cells\":[{\"address\":{},\"value\":2.054697758469921}]}";
        handler.assertResponse(url, properties, 200, expected);
    }

    @Test
    public void testLightGBMEvaluationWithMissingValue() {
        Map<String, String> properties = new HashMap<>();
        properties.put("missing-value", "-1.0");
        properties.put("numerical_2", "0.5");
        properties.put("categorical_1", "b");
        properties.put("categorical_2", "j");
        properties.put("non-existing-binding", "-1");
        String url = "http://localhost/model-evaluation/v1/lightgbm_regression/eval";
        String expected = "{\"cells\":[{\"address\":{},\"value\":2.0745534018208094}]}";
        handler.assertResponse(url, properties, 200, expected);
    }

    @Test
    public void testMnistSoftmaxDetails() {
        String url = "http://localhost:8080/model-evaluation/v1/mnist_softmax";
        String expected = "{\"model\":\"mnist_softmax\",\"functions\":[{\"function\":\"default.add\",\"info\":\"http://localhost:8080/model-evaluation/v1/mnist_softmax/default.add\",\"eval\":\"http://localhost:8080/model-evaluation/v1/mnist_softmax/default.add/eval\",\"arguments\":[{\"name\":\"Placeholder\",\"type\":\"tensor(d0[],d1[784])\"}]}]}";
        handler.assertResponse(url, 200, expected);
    }

    @Test
    public void testMnistSoftmaxTypeDetails() {
        String url = "http://localhost/model-evaluation/v1/mnist_softmax/default.add/";
        String expected = "{\"model\":\"mnist_softmax\",\"function\":\"default.add\",\"info\":\"http://localhost/model-evaluation/v1/mnist_softmax/default.add\",\"eval\":\"http://localhost/model-evaluation/v1/mnist_softmax/default.add/eval\",\"arguments\":[{\"name\":\"Placeholder\",\"type\":\"tensor(d0[],d1[784])\"}]}";
        handler.assertResponse(url, 200, expected);
    }

    @Test
    public void testMnistSoftmaxEvaluateDefaultFunctionWithoutBindings() {
        String url = "http://localhost/model-evaluation/v1/mnist_softmax/eval";
        String expected = "{\"error\":\"Argument 'Placeholder' must be bound to a value of type tensor(d0[],d1[784])\"}";
        handler.assertResponse(url, 400, expected);
    }

    @Test
    public void testMnistSoftmaxEvaluateSpecificFunctionWithoutBindings() {
        String url = "http://localhost/model-evaluation/v1/mnist_softmax/default.add/eval";
        String expected = "{\"error\":\"Argument 'Placeholder' must be bound to a value of type tensor(d0[],d1[784])\"}";
        handler.assertResponse(url, 400, expected);
    }

    @Test
    public void testMnistSoftmaxEvaluateDefaultFunctionWithBindings() {
        Map<String, String> properties = new HashMap<>();
        properties.put("Placeholder", inputTensor());
        String url = "http://localhost/model-evaluation/v1/mnist_softmax/eval";
        String expected = "{\"cells\":[{\"address\":{\"d0\":\"0\",\"d1\":\"0\"},\"value\":-0.3546536862850189},{\"address\":{\"d0\":\"0\",\"d1\":\"1\"},\"value\":0.3759574592113495},{\"address\":{\"d0\":\"0\",\"d1\":\"2\"},\"value\":0.06054411828517914},{\"address\":{\"d0\":\"0\",\"d1\":\"3\"},\"value\":-0.251544713973999},{\"address\":{\"d0\":\"0\",\"d1\":\"4\"},\"value\":0.017951013520359993},{\"address\":{\"d0\":\"0\",\"d1\":\"5\"},\"value\":1.2899067401885986},{\"address\":{\"d0\":\"0\",\"d1\":\"6\"},\"value\":-0.10389615595340729},{\"address\":{\"d0\":\"0\",\"d1\":\"7\"},\"value\":0.6367976665496826},{\"address\":{\"d0\":\"0\",\"d1\":\"8\"},\"value\":-1.4136744737625122},{\"address\":{\"d0\":\"0\",\"d1\":\"9\"},\"value\":-0.2573896050453186}]}";
        handler.assertResponse(url, properties, 200, expected);
    }

    @Test
    public void testMnistSoftmaxEvaluateSpecificFunctionWithBindings() {
        Map<String, String> properties = new HashMap<>();
        properties.put("Placeholder", inputTensor());
        String url = "http://localhost/model-evaluation/v1/mnist_softmax/default.add/eval";
        String expected = "{\"cells\":[{\"address\":{\"d0\":\"0\",\"d1\":\"0\"},\"value\":-0.3546536862850189},{\"address\":{\"d0\":\"0\",\"d1\":\"1\"},\"value\":0.3759574592113495},{\"address\":{\"d0\":\"0\",\"d1\":\"2\"},\"value\":0.06054411828517914},{\"address\":{\"d0\":\"0\",\"d1\":\"3\"},\"value\":-0.251544713973999},{\"address\":{\"d0\":\"0\",\"d1\":\"4\"},\"value\":0.017951013520359993},{\"address\":{\"d0\":\"0\",\"d1\":\"5\"},\"value\":1.2899067401885986},{\"address\":{\"d0\":\"0\",\"d1\":\"6\"},\"value\":-0.10389615595340729},{\"address\":{\"d0\":\"0\",\"d1\":\"7\"},\"value\":0.6367976665496826},{\"address\":{\"d0\":\"0\",\"d1\":\"8\"},\"value\":-1.4136744737625122},{\"address\":{\"d0\":\"0\",\"d1\":\"9\"},\"value\":-0.2573896050453186}]}";
        handler.assertResponse(url, properties, 200, expected);
    }

    @Test
    public void testMnistSavedDetails() {
        String url = "http://localhost:8080/model-evaluation/v1/mnist_saved";
        String expected = "{\"model\":\"mnist_saved\",\"functions\":[{\"function\":\"serving_default.y\",\"info\":\"http://localhost:8080/model-evaluation/v1/mnist_saved/serving_default.y\",\"eval\":\"http://localhost:8080/model-evaluation/v1/mnist_saved/serving_default.y/eval\",\"arguments\":[{\"name\":\"input\",\"type\":\"tensor(d0[],d1[784])\"}]}]}";
        handler.assertResponse(url, 200, expected);
    }

    @Test
    public void testMnistSavedTypeDetails() {
        String url = "http://localhost/model-evaluation/v1/mnist_saved/serving_default.y/";
        String expected = "{\"model\":\"mnist_saved\",\"function\":\"serving_default.y\",\"info\":\"http://localhost/model-evaluation/v1/mnist_saved/serving_default.y\",\"eval\":\"http://localhost/model-evaluation/v1/mnist_saved/serving_default.y/eval\",\"arguments\":[{\"name\":\"input\",\"type\":\"tensor(d0[],d1[784])\"}]}";
        handler.assertResponse(url, 200, expected);
    }

    @Test
    public void testMnistSavedEvaluateDefaultFunctionShouldFail() {
        String url = "http://localhost/model-evaluation/v1/mnist_saved/eval";
        String expected = "{\"error\":\"More than one function is available in model 'mnist_saved', but no name is given. Available functions: imported_ml_function_mnist_saved_dnn_hidden1_add, serving_default.y\"}";
        handler.assertResponse(url, 404, expected);
    }

    @Test
    public void testMnistSavedEvaluateSpecificFunction() {
        Map<String, String> properties = new HashMap<>();
        properties.put("input", inputTensor());
        String url = "http://localhost/model-evaluation/v1/mnist_saved/serving_default.y/eval";
        String expected = "{\"cells\":[{\"address\":{\"d0\":\"0\",\"d1\":\"0\"},\"value\":-0.6319251673007533},{\"address\":{\"d0\":\"0\",\"d1\":\"1\"},\"value\":-7.577770600619843E-4},{\"address\":{\"d0\":\"0\",\"d1\":\"2\"},\"value\":-0.010707969042025622},{\"address\":{\"d0\":\"0\",\"d1\":\"3\"},\"value\":-0.6344759233540788},{\"address\":{\"d0\":\"0\",\"d1\":\"4\"},\"value\":-0.17529455385847528},{\"address\":{\"d0\":\"0\",\"d1\":\"5\"},\"value\":0.7490809723192187},{\"address\":{\"d0\":\"0\",\"d1\":\"6\"},\"value\":-0.022790284182901716},{\"address\":{\"d0\":\"0\",\"d1\":\"7\"},\"value\":0.26799240657608936},{\"address\":{\"d0\":\"0\",\"d1\":\"8\"},\"value\":-0.3152438845465862},{\"address\":{\"d0\":\"0\",\"d1\":\"9\"},\"value\":0.05949304847735276}]}";
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
        return new ModelsEvaluator(new RankProfilesConfigImporterWithMockedConstants(Path.fromString(path).append("constants"), MockFileAcquirer.returnFile(null)),
                config, constantsConfig, expressionsConfig, onnxModelsConfig);
    }

    private String inputTensor() {
        Tensor.Builder b = Tensor.Builder.of(TensorType.fromSpec("tensor(d0[],d1[784])"));
        for (int i = 0; i < 784; i++)
            b.cell(0.0, 0, i);
        return b.build().toString();
    }

}
