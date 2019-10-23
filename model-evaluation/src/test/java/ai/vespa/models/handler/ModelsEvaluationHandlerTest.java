// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.handler;

import ai.vespa.models.evaluation.ModelTester;
import ai.vespa.models.evaluation.ModelsEvaluator;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.config.subscription.FileSource;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.filedistribution.fileacquirer.MockFileAcquirer;
import com.yahoo.path.Path;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;

public class ModelsEvaluationHandlerTest {

    private static ModelsEvaluationHandler handler;

    @BeforeClass
    static public void setUp() {
        Executor executor = Executors.newSingleThreadExecutor();
        ModelsEvaluator models = createModels("src/test/resources/config/models/");
        handler = new ModelsEvaluationHandler(models, executor);
    }

    @Test
    public void testUnknownAPI() {
        assertResponse("http://localhost/wrong-api-binding", 404);
    }

    @Test
    public void testUnknownVersion() {
        assertResponse("http://localhost/model-evaluation/v0", 404);
    }

    @Test
    public void testNonExistingModel() {
        assertResponse("http://localhost/model-evaluation/v1/non-existing-model", 404);
    }

    @Test
    public void testListModels() {
        String url = "http://localhost/model-evaluation/v1";
        String expected =
                "{\"mnist_softmax\":\"http://localhost/model-evaluation/v1/mnist_softmax\",\"mnist_saved\":\"http://localhost/model-evaluation/v1/mnist_saved\",\"mnist_softmax_saved\":\"http://localhost/model-evaluation/v1/mnist_softmax_saved\",\"xgboost_2_2\":\"http://localhost/model-evaluation/v1/xgboost_2_2\"}";
        assertResponse(url, 200, expected);
    }

    @Test
    public void testXgBoostEvaluationWithoutBindings() {
        String url = "http://localhost/model-evaluation/v1/xgboost_2_2/eval";  // only has a single function
        String expected = "{\"cells\":[{\"address\":{},\"value\":-4.376589999999999}]}";
        assertResponse(url, 200, expected);
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
        assertResponse(url, properties, 200, expected);
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
        assertResponse(url, properties, 200, expected);
    }

    @Test
    public void testMnistSoftmaxDetails() {
        String url = "http://localhost:8080/model-evaluation/v1/mnist_softmax";
        String expected = "{\"model\":\"mnist_softmax\",\"functions\":[{\"function\":\"default.add\",\"info\":\"http://localhost:8080/model-evaluation/v1/mnist_softmax/default.add\",\"eval\":\"http://localhost:8080/model-evaluation/v1/mnist_softmax/default.add/eval\",\"arguments\":[{\"name\":\"Placeholder\",\"type\":\"tensor(d0[],d1[784])\"}]}]}";
        assertResponse(url, 200, expected);
    }

    @Test
    public void testMnistSoftmaxTypeDetails() {
        String url = "http://localhost/model-evaluation/v1/mnist_softmax/default.add/";
        String expected = "{\"model\":\"mnist_softmax\",\"function\":\"default.add\",\"info\":\"http://localhost/model-evaluation/v1/mnist_softmax/default.add\",\"eval\":\"http://localhost/model-evaluation/v1/mnist_softmax/default.add/eval\",\"arguments\":[{\"name\":\"Placeholder\",\"type\":\"tensor(d0[],d1[784])\"}]}";
        assertResponse(url, 200, expected);
    }

    @Test
    public void testMnistSoftmaxEvaluateDefaultFunctionWithoutBindings() {
        String url = "http://localhost/model-evaluation/v1/mnist_softmax/eval";
        String expected = "{\"error\":\"Argument 'Placeholder' must be bound to a value of type tensor(d0[],d1[784])\"}";
        assertResponse(url, 400, expected);
    }

    @Test
    public void testMnistSoftmaxEvaluateSpecificFunctionWithoutBindings() {
        String url = "http://localhost/model-evaluation/v1/mnist_softmax/default.add/eval";
        String expected = "{\"error\":\"Argument 'Placeholder' must be bound to a value of type tensor(d0[],d1[784])\"}";
        assertResponse(url, 400, expected);
    }

    @Test
    public void testMnistSoftmaxEvaluateDefaultFunctionWithBindings() {
        Map<String, String> properties = new HashMap<>();
        properties.put("Placeholder", inputTensor());
        String url = "http://localhost/model-evaluation/v1/mnist_softmax/eval";
        String expected = "{\"cells\":[{\"address\":{\"d0\":\"0\",\"d1\":\"0\"},\"value\":-0.3546536862850189},{\"address\":{\"d0\":\"0\",\"d1\":\"1\"},\"value\":0.3759574592113495},{\"address\":{\"d0\":\"0\",\"d1\":\"2\"},\"value\":0.06054411828517914},{\"address\":{\"d0\":\"0\",\"d1\":\"3\"},\"value\":-0.251544713973999},{\"address\":{\"d0\":\"0\",\"d1\":\"4\"},\"value\":0.017951013520359993},{\"address\":{\"d0\":\"0\",\"d1\":\"5\"},\"value\":1.2899067401885986},{\"address\":{\"d0\":\"0\",\"d1\":\"6\"},\"value\":-0.10389615595340729},{\"address\":{\"d0\":\"0\",\"d1\":\"7\"},\"value\":0.6367976665496826},{\"address\":{\"d0\":\"0\",\"d1\":\"8\"},\"value\":-1.4136744737625122},{\"address\":{\"d0\":\"0\",\"d1\":\"9\"},\"value\":-0.2573896050453186}]}";
        assertResponse(url, properties, 200, expected);
    }

    @Test
    public void testMnistSoftmaxEvaluateSpecificFunctionWithBindings() {
        Map<String, String> properties = new HashMap<>();
        properties.put("Placeholder", inputTensor());
        String url = "http://localhost/model-evaluation/v1/mnist_softmax/default.add/eval";
        String expected = "{\"cells\":[{\"address\":{\"d0\":\"0\",\"d1\":\"0\"},\"value\":-0.3546536862850189},{\"address\":{\"d0\":\"0\",\"d1\":\"1\"},\"value\":0.3759574592113495},{\"address\":{\"d0\":\"0\",\"d1\":\"2\"},\"value\":0.06054411828517914},{\"address\":{\"d0\":\"0\",\"d1\":\"3\"},\"value\":-0.251544713973999},{\"address\":{\"d0\":\"0\",\"d1\":\"4\"},\"value\":0.017951013520359993},{\"address\":{\"d0\":\"0\",\"d1\":\"5\"},\"value\":1.2899067401885986},{\"address\":{\"d0\":\"0\",\"d1\":\"6\"},\"value\":-0.10389615595340729},{\"address\":{\"d0\":\"0\",\"d1\":\"7\"},\"value\":0.6367976665496826},{\"address\":{\"d0\":\"0\",\"d1\":\"8\"},\"value\":-1.4136744737625122},{\"address\":{\"d0\":\"0\",\"d1\":\"9\"},\"value\":-0.2573896050453186}]}";
        assertResponse(url, properties, 200, expected);
    }

    @Test
    public void testMnistSavedDetails() {
        String url = "http://localhost:8080/model-evaluation/v1/mnist_saved";
        String expected = "{\"model\":\"mnist_saved\",\"functions\":[{\"function\":\"serving_default.y\",\"info\":\"http://localhost:8080/model-evaluation/v1/mnist_saved/serving_default.y\",\"eval\":\"http://localhost:8080/model-evaluation/v1/mnist_saved/serving_default.y/eval\",\"arguments\":[{\"name\":\"input\",\"type\":\"tensor(d0[],d1[784])\"}]}]}";
        assertResponse(url, 200, expected);
    }

    @Test
    public void testMnistSavedTypeDetails() {
        String url = "http://localhost/model-evaluation/v1/mnist_saved/serving_default.y/";
        String expected = "{\"model\":\"mnist_saved\",\"function\":\"serving_default.y\",\"info\":\"http://localhost/model-evaluation/v1/mnist_saved/serving_default.y\",\"eval\":\"http://localhost/model-evaluation/v1/mnist_saved/serving_default.y/eval\",\"arguments\":[{\"name\":\"input\",\"type\":\"tensor(d0[],d1[784])\"}]}";
        assertResponse(url, 200, expected);
    }

    @Test
    public void testMnistSavedEvaluateDefaultFunctionShouldFail() {
        String url = "http://localhost/model-evaluation/v1/mnist_saved/eval";
        String expected = "{\"error\":\"More than one function is available in model 'mnist_saved', but no name is given. Available functions: imported_ml_function_mnist_saved_dnn_hidden1_add, serving_default.y\"}";
        assertResponse(url, 404, expected);
    }

    @Test
    public void testMnistSavedEvaluateSpecificFunction() {
        Map<String, String> properties = new HashMap<>();
        properties.put("input", inputTensor());
        String url = "http://localhost/model-evaluation/v1/mnist_saved/serving_default.y/eval";
        String expected = "{\"cells\":[{\"address\":{\"d0\":\"0\",\"d1\":\"0\"},\"value\":-0.6319251673007533},{\"address\":{\"d0\":\"0\",\"d1\":\"1\"},\"value\":-7.577770600619843E-4},{\"address\":{\"d0\":\"0\",\"d1\":\"2\"},\"value\":-0.010707969042025622},{\"address\":{\"d0\":\"0\",\"d1\":\"3\"},\"value\":-0.6344759233540788},{\"address\":{\"d0\":\"0\",\"d1\":\"4\"},\"value\":-0.17529455385847528},{\"address\":{\"d0\":\"0\",\"d1\":\"5\"},\"value\":0.7490809723192187},{\"address\":{\"d0\":\"0\",\"d1\":\"6\"},\"value\":-0.022790284182901716},{\"address\":{\"d0\":\"0\",\"d1\":\"7\"},\"value\":0.26799240657608936},{\"address\":{\"d0\":\"0\",\"d1\":\"8\"},\"value\":-0.3152438845465862},{\"address\":{\"d0\":\"0\",\"d1\":\"9\"},\"value\":0.05949304847735276}]}";
        assertResponse(url, properties, 200, expected);
    }

    static private void assertResponse(String url, int expectedCode) {
        assertResponse(url, Collections.emptyMap(), expectedCode, null);
    }

    static private void assertResponse(String url, int expectedCode, String expectedResult) {
        assertResponse(url, Collections.emptyMap(), expectedCode, expectedResult);
    }

    static private void assertResponse(String url, Map<String, String> properties, int expectedCode, String expectedResult) {
        HttpRequest getRequest = HttpRequest.createTestRequest(url, com.yahoo.jdisc.http.HttpRequest.Method.GET, null, properties);
        HttpRequest postRequest = HttpRequest.createTestRequest(url, com.yahoo.jdisc.http.HttpRequest.Method.POST, null, properties);
        assertResponse(getRequest, expectedCode, expectedResult);
        assertResponse(postRequest, expectedCode, expectedResult);
    }

    static private void assertResponse(HttpRequest request, int expectedCode, String expectedResult) {
        HttpResponse response = handler.handle(request);
        assertEquals("application/json", response.getContentType());
        if (expectedResult != null) {
            assertEquals(expectedResult, getContents(response));
        }
        assertEquals(expectedCode, response.getStatus());
    }

    static private String getContents(HttpResponse response) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            response.render(stream);
            return stream.toString();
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    static private ModelsEvaluator createModels(String path) {
        Path configDir = Path.fromString(path);
        RankProfilesConfig config = new ConfigGetter<>(new FileSource(configDir.append("rank-profiles.cfg").toFile()),
                RankProfilesConfig.class).getConfig("");
        RankingConstantsConfig constantsConfig = new ConfigGetter<>(new FileSource(configDir.append("ranking-constants.cfg").toFile()),
                RankingConstantsConfig.class).getConfig("");
        ModelTester.RankProfilesConfigImporterWithMockedConstants importer =
                new ModelTester.RankProfilesConfigImporterWithMockedConstants(Path.fromString(path).append("constants"),
                                                                              MockFileAcquirer.returnFile(null));
        return new ModelsEvaluator(importer.importFrom(config, constantsConfig));
    }

    private String inputTensor() {
        Tensor.Builder b = Tensor.Builder.of(TensorType.fromSpec("tensor(d0[],d1[784])"));
        for (int i = 0; i < 784; i++)
            b.cell(0.0, 0, i);
        return b.build().toString();
    }

}
