// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import com.yahoo.application.Application;
import com.yahoo.application.Networking;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.test.json.JsonTestHelper;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

/**
 * Verify that we can create a JDisc (and hence Application) instance capable of doing model evaluation
 *
 * @author bratseth
 */
public class ContainerModelEvaluationTest {

    @Test
    @Ignore // This should ideally work but may not be worth the effort
    public void testCreateJDiscInstanceWithModelEvaluation() {
        try (JDisc jdisc =
                     JDisc.fromPath(new File("src/test/app-packages/model-evaluation").toPath(),
                                    Networking.disable)) {
            assertLoadedModels(jdisc);
        }
    }

    @Test
    public void testCreateApplicationInstanceWithModelEvaluation() {
        try (Application application =
                     Application.fromApplicationPackage(new File("src/test/app-packages/model-evaluation"),
                                                        Networking.disable)) {
            assertLoadedModels(application.getJDisc("default"));
        }
    }
    private void assertLoadedModels(JDisc jdisc) {
        {
            String expected = "{\"xgboost_xgboost_2_2\":\"http://localhost/model-evaluation/v1/xgboost_xgboost_2_2\",\"onnx_mnist_softmax\":\"http://localhost/model-evaluation/v1/onnx_mnist_softmax\",\"tensorflow_mnist_softmax_saved\":\"http://localhost/model-evaluation/v1/tensorflow_mnist_softmax_saved\",\"tensorflow_mnist_saved\":\"http://localhost/model-evaluation/v1/tensorflow_mnist_saved\",\"vespa_example\":\"http://localhost/model-evaluation/v1/vespa_example\"}";
            assertResponse("http://localhost/model-evaluation/v1", expected, jdisc);
        }

        {
            String expected = "{\"cells\":[{\"address\":{},\"value\":2.496898}]}";
            assertResponse("http://localhost/model-evaluation/v1/xgboost_xgboost_2_2/eval", expected, jdisc);
        }

        {
            // Note: The specific response value here has not been verified
            String expected = "{\"cells\":[{\"address\":{\"d0\":\"0\",\"d1\":\"0\"},\"value\":-0.5066885003407351},{\"address\":{\"d0\":\"0\",\"d1\":\"1\"},\"value\":0.3912837743150205},{\"address\":{\"d0\":\"0\",\"d1\":\"2\"},\"value\":-0.12401806321703948},{\"address\":{\"d0\":\"0\",\"d1\":\"3\"},\"value\":-0.7019029168606575},{\"address\":{\"d0\":\"0\",\"d1\":\"4\"},\"value\":0.13120114146441697},{\"address\":{\"d0\":\"0\",\"d1\":\"5\"},\"value\":0.6611923203384626},{\"address\":{\"d0\":\"0\",\"d1\":\"6\"},\"value\":-0.22365810810026446},{\"address\":{\"d0\":\"0\",\"d1\":\"7\"},\"value\":-0.0740018307465809},{\"address\":{\"d0\":\"0\",\"d1\":\"8\"},\"value\":0.056492490256153896},{\"address\":{\"d0\":\"0\",\"d1\":\"9\"},\"value\":-0.18422015072393733}]}";
            assertResponse("http://localhost/model-evaluation/v1/tensorflow_mnist_saved/serving_default.y/eval?input=" + inputTensor(), expected, jdisc);
        }
    }


    private void assertResponse(String url, String expectedResponse, JDisc jdisc) {
        try {
            Response response = jdisc.handleRequest(new Request(url));
            JsonTestHelper.assertJsonEquals(expectedResponse, response.getBodyAsString());
            assertEquals(200, response.getStatus());
        }
        catch (CharacterCodingException e) {
            throw new RuntimeException(e);
        }
    }

    private String inputTensor() {
        Tensor.Builder b = Tensor.Builder.of(TensorType.fromSpec("tensor(d0[],d1[784])"));
        for (int i = 0; i < 784; i++)
            b.cell(0.0, 0, i);
        return URLEncoder.encode(b.build().toString(), StandardCharsets.UTF_8);
    }

}
