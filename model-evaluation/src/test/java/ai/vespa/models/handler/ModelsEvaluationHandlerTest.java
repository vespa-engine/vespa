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
        String expected = "{\"mnist_softmax\":\"http://localhost/model-evaluation/v1/mnist_softmax\",\"mnist_saved\":\"http://localhost/model-evaluation/v1/mnist_saved\",\"mnist_softmax_saved\":\"http://localhost/model-evaluation/v1/mnist_softmax_saved\",\"xgboost_2_2\":\"http://localhost/model-evaluation/v1/xgboost_2_2\"}";
        assertResponse(url, 200, expected);
    }

    @Test
    public void testXgBoostEvaluationWithoutBindings() {
        String url = "http://localhost/model-evaluation/v1/xgboost_2_2/eval";  // only has a single function
        String expected = "{\"cells\":[{\"address\":{},\"value\":-8.17695}]}";
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
    public void testMnistSoftmaxDetails() {
        String url = "http://localhost:8080/model-evaluation/v1/mnist_softmax";
        String expected = "{\"bindings\":[{\"name\":\"Placeholder\",\"type\":\"\"}]}";  // only has a single function
        assertResponse(url, 200, expected);
    }

    @Test
    public void testMnistSoftmaxTypeDetails() {
        String url = "http://localhost/model-evaluation/v1/mnist_softmax/default.add/";
        String expected = "{\"bindings\":[{\"name\":\"Placeholder\",\"type\":\"\"}]}";
        assertResponse(url, 200, expected);
    }

    @Test
    public void testMnistSoftmaxEvaluateDefaultFunctionWithoutBindings() {
        String url = "http://localhost/model-evaluation/v1/mnist_softmax/eval";
        String expected = "{\"cells\":[{\"address\":{\"d1\":\"0\"},\"value\":-0.3546536862850189},{\"address\":{\"d1\":\"1\"},\"value\":0.3759574592113495},{\"address\":{\"d1\":\"2\"},\"value\":0.06054411828517914},{\"address\":{\"d1\":\"3\"},\"value\":-0.251544713973999},{\"address\":{\"d1\":\"4\"},\"value\":0.017951013520359993},{\"address\":{\"d1\":\"5\"},\"value\":1.2899067401885986},{\"address\":{\"d1\":\"6\"},\"value\":-0.10389615595340729},{\"address\":{\"d1\":\"7\"},\"value\":0.6367976665496826},{\"address\":{\"d1\":\"8\"},\"value\":-1.4136744737625122},{\"address\":{\"d1\":\"9\"},\"value\":-0.2573896050453186}]}";
        assertResponse(url, 200, expected);
    }

    @Test
    public void testMnistSoftmaxEvaluateSpecificFunctionWithoutBindings() {
        String url = "http://localhost/model-evaluation/v1/mnist_softmax/default.add/eval";
        String expected = "{\"cells\":[{\"address\":{\"d1\":\"0\"},\"value\":-0.3546536862850189},{\"address\":{\"d1\":\"1\"},\"value\":0.3759574592113495},{\"address\":{\"d1\":\"2\"},\"value\":0.06054411828517914},{\"address\":{\"d1\":\"3\"},\"value\":-0.251544713973999},{\"address\":{\"d1\":\"4\"},\"value\":0.017951013520359993},{\"address\":{\"d1\":\"5\"},\"value\":1.2899067401885986},{\"address\":{\"d1\":\"6\"},\"value\":-0.10389615595340729},{\"address\":{\"d1\":\"7\"},\"value\":0.6367976665496826},{\"address\":{\"d1\":\"8\"},\"value\":-1.4136744737625122},{\"address\":{\"d1\":\"9\"},\"value\":-0.2573896050453186}]}";
        assertResponse(url, 200, expected);
    }

    @Test
    public void testMnistSoftmaxEvaluateDefaultFunctionWithBindings() {
        Map<String, String> properties = new HashMap<>();
        properties.put("Placeholder", "{1.0}");
        String url = "http://localhost/model-evaluation/v1/mnist_softmax/eval";
        String expected = "{\"cells\":[{\"address\":{\"d1\":\"0\"},\"value\":2.7147769462592217},{\"address\":{\"d1\":\"1\"},\"value\":-19.710327346521872},{\"address\":{\"d1\":\"2\"},\"value\":9.496512226053643},{\"address\":{\"d1\":\"3\"},\"value\":13.11241075176957},{\"address\":{\"d1\":\"4\"},\"value\":-12.355567088005559},{\"address\":{\"d1\":\"5\"},\"value\":10.39812446509341},{\"address\":{\"d1\":\"6\"},\"value\":-1.3739236534397499},{\"address\":{\"d1\":\"7\"},\"value\":-3.4260787871386995},{\"address\":{\"d1\":\"8\"},\"value\":6.471120687192041},{\"address\":{\"d1\":\"9\"},\"value\":-5.327024804970982}]}";
        assertResponse(url, properties, 200, expected);
    }

    @Test
    public void testMnistSoftmaxEvaluateSpecificFunctionWithBindings() {
        Map<String, String> properties = new HashMap<>();
        properties.put("Placeholder", "{1.0}");
        String url = "http://localhost/model-evaluation/v1/mnist_softmax/default.add/eval";
        String expected = "{\"cells\":[{\"address\":{\"d1\":\"0\"},\"value\":2.7147769462592217},{\"address\":{\"d1\":\"1\"},\"value\":-19.710327346521872},{\"address\":{\"d1\":\"2\"},\"value\":9.496512226053643},{\"address\":{\"d1\":\"3\"},\"value\":13.11241075176957},{\"address\":{\"d1\":\"4\"},\"value\":-12.355567088005559},{\"address\":{\"d1\":\"5\"},\"value\":10.39812446509341},{\"address\":{\"d1\":\"6\"},\"value\":-1.3739236534397499},{\"address\":{\"d1\":\"7\"},\"value\":-3.4260787871386995},{\"address\":{\"d1\":\"8\"},\"value\":6.471120687192041},{\"address\":{\"d1\":\"9\"},\"value\":-5.327024804970982}]}";
        assertResponse(url, properties, 200, expected);
    }

    @Test
    public void testMnistSavedDetails() {
        String url = "http://localhost:8080/model-evaluation/v1/mnist_saved";
        String expected = "{\"imported_ml_macro_mnist_saved_dnn_hidden1_add\":\"http://localhost:8080/model-evaluation/v1/mnist_saved/imported_ml_macro_mnist_saved_dnn_hidden1_add\",\"serving_default.y\":\"http://localhost:8080/model-evaluation/v1/mnist_saved/serving_default.y\"}";
        assertResponse(url, 200, expected);
    }

    @Test
    public void testMnistSavedTypeDetails() {
        String url = "http://localhost/model-evaluation/v1/mnist_saved/serving_default.y/";
        String expected = "{\"bindings\":[{\"name\":\"input\",\"type\":\"\"}]}";
        assertResponse(url, 200, expected);
    }

    @Test
    public void testMnistSavedEvaluateDefaultFunctionShouldFail() {
        String url = "http://localhost/model-evaluation/v1/mnist_saved/eval";
        String expected = "{\"error\":\"attempt to evaluate model without specifying function\"}";
        assertResponse(url, 404, expected);
    }

    @Test
    public void testMnistSavedEvaluateSpecificFunction() {
        Map<String, String> properties = new HashMap<>();
        properties.put("input", "-1.0");
        String url = "http://localhost/model-evaluation/v1/mnist_saved/serving_default.y/eval";
        String expected = "{\"cells\":[{\"address\":{\"d1\":\"0\"},\"value\":-2.72208123403445},{\"address\":{\"d1\":\"1\"},\"value\":6.465137496457595},{\"address\":{\"d1\":\"2\"},\"value\":-7.078050386283122},{\"address\":{\"d1\":\"3\"},\"value\":-10.485296462655546},{\"address\":{\"d1\":\"4\"},\"value\":0.19508378636937004},{\"address\":{\"d1\":\"5\"},\"value\":6.348870746681019},{\"address\":{\"d1\":\"6\"},\"value\":10.756191852397258},{\"address\":{\"d1\":\"7\"},\"value\":1.476101533270058},{\"address\":{\"d1\":\"8\"},\"value\":-17.778398655804875},{\"address\":{\"d1\":\"9\"},\"value\":-2.0597690508530295}]}";
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
        assertEquals(expectedCode, response.getStatus());
        if (expectedResult != null) {
            assertEquals(expectedResult, getContents(response));
        }
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

}
