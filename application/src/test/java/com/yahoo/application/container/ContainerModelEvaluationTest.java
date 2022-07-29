// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import com.yahoo.application.Application;
import com.yahoo.application.Networking;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.test.json.JsonTestHelper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verify that we can create a JDisc (and hence Application) instance capable of doing model evaluation
 *
 * @author bratseth
 */
public class ContainerModelEvaluationTest {

    // This should ideally work but may not be worth the effort
    @Test
    @Disabled
    void testCreateJDiscInstanceWithModelEvaluation() {
        try (JDisc jdisc =
                JDisc.fromPath(new File("src/test/app-packages/model-evaluation").toPath(),
                        Networking.disable)) {
            assertLoadedModels(jdisc);
        }
    }

    @Test
    void testCreateApplicationInstanceWithModelEvaluation() {
        assumeTrue(OnnxEvaluator.isRuntimeAvailable());
        try (Application application =
                Application.fromApplicationPackage(new File("src/test/app-packages/model-evaluation"),
                        Networking.disable)) {
            assertLoadedModels(application.getJDisc("default"));
        }
    }
    private void assertLoadedModels(JDisc jdisc) {
        {
            String expected = "{\"xgboost_xgboost_2_2\":\"http://localhost/model-evaluation/v1/xgboost_xgboost_2_2\",\"onnx_mnist_softmax\":\"http://localhost/model-evaluation/v1/onnx_mnist_softmax\",\"vespa_example\":\"http://localhost/model-evaluation/v1/vespa_example\",\"onnx_softmax_func\":\"http://localhost/model-evaluation/v1/onnx_softmax_func\",\"lightgbm_regression\":\"http://localhost/model-evaluation/v1/lightgbm_regression\"}";
            assertResponse("http://localhost/model-evaluation/v1", expected, jdisc);
        }

        {
            String expected = "{\"cells\":[{\"address\":{},\"value\":2.496898}]}";
            assertResponse("http://localhost/model-evaluation/v1/xgboost_xgboost_2_2/eval", expected, jdisc);
        }

        {
            String expected = "{\"cells\":[{\"address\":{},\"value\":1.9130086820218188}]}";
            assertResponse("http://localhost/model-evaluation/v1/lightgbm_regression/eval", expected, jdisc);
        }

        {
            String expected = "{\"cells\":[{\"address\":{\"d0\":\"0\"},\"value\":0.3006095290184021},{\"address\":{\"d0\":\"1\"},\"value\":0.33222490549087524},{\"address\":{\"d0\":\"2\"},\"value\":0.3671652674674988}]}";
            assertResponse("http://localhost/model-evaluation/v1/onnx_softmax_func/output/eval?input=" + inputTensor(), expected, jdisc);
            assertResponse("http://localhost/model-evaluation/v1/onnx_softmax_func/default.output/eval?input=" + inputTensor(), expected, jdisc);
            assertResponse("http://localhost/model-evaluation/v1/onnx_softmax_func/default/output/eval?input=" + inputTensor(), expected, jdisc);
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
        Tensor.Builder b = Tensor.Builder.of(TensorType.fromSpec("tensor<float>(d0[3])"));
        b.cell(0.1, 0);
        b.cell(0.2, 1);
        b.cell(0.3, 2);
        return URLEncoder.encode(b.build().toString(), StandardCharsets.UTF_8);
    }

}
