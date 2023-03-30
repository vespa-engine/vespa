// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * @author bjorncs
 */
class OnnxRuntimeTest {

    @Test
    void reuses_sessions_while_active() {
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        OnnxRuntime runtime = new OnnxRuntime();
        String model1 = "src/test/models/onnx/simple/simple.onnx";
        var evaluator1 = runtime.evaluatorOf(model1);
        var evaluator2 = runtime.evaluatorOf(model1);
        String model2 = "src/test/models/onnx/simple/matmul.onnx";
        var evaluator3 = runtime.evaluatorOf(model2);
        assertSameSession(evaluator1, evaluator2);
        assertNotSameSession(evaluator1, evaluator3);
        assertEquals(2, runtime.sessionsCached());

        evaluator1.close();
        evaluator2.close();
        assertEquals(1, runtime.sessionsCached());
        assertClosed(evaluator1);
        assertNotClosed(evaluator3);

        evaluator3.close();
        assertEquals(0, runtime.sessionsCached());
        assertClosed(evaluator3);

        var session4 = runtime.evaluatorOf(model1);
        assertNotSameSession(evaluator1, session4);
        assertEquals(1, runtime.sessionsCached());
        session4.close();
        assertEquals(0, runtime.sessionsCached());
        assertClosed(session4);
    }

    @Test
    void loads_model_from_byte_array() throws IOException {
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        var runtime = new OnnxRuntime();
        byte[] bytes = Files.readAllBytes(Paths.get("src/test/models/onnx/simple/simple.onnx"));
        var evaluator1 = runtime.evaluatorOf(bytes);
        var evaluator2 = runtime.evaluatorOf(bytes);
        assertEquals(3, evaluator1.getInputs().size());
        assertEquals(1, runtime.sessionsCached());
        assertSameSession(evaluator1, evaluator2);
        evaluator2.close();
        evaluator1.close();
        assertEquals(0, runtime.sessionsCached());
        assertClosed(evaluator1);
    }

    @Test
    void loading_same_model_from_bytes_and_file_resolve_to_same_instance() throws IOException {
        assumeTrue(OnnxRuntime.isRuntimeAvailable());
        var runtime = new OnnxRuntime();
        String modelPath = "src/test/models/onnx/simple/simple.onnx";
        byte[] bytes = Files.readAllBytes(Paths.get(modelPath));
        try (var evaluator1 = runtime.evaluatorOf(bytes);
             var evaluator2 = runtime.evaluatorOf(modelPath)) {
            assertSameSession(evaluator1, evaluator2);
            assertEquals(1, runtime.sessionsCached());
        }
    }

    private static void assertClosed(OnnxEvaluator evaluator) { assertTrue(isClosed(evaluator), "Session is not closed"); }
    private static void assertNotClosed(OnnxEvaluator evaluator) { assertFalse(isClosed(evaluator), "Session is closed"); }
    private static void assertSameSession(OnnxEvaluator evaluator1, OnnxEvaluator evaluator2) {
        assertSame(evaluator1.ortSession(), evaluator2.ortSession());
    }
    private static void assertNotSameSession(OnnxEvaluator evaluator1, OnnxEvaluator evaluator2) {
        assertNotSame(evaluator1.ortSession(), evaluator2.ortSession());
    }

    private static boolean isClosed(OnnxEvaluator evaluator) {
        try {
            evaluator.getInputs();
            return false;
        } catch (IllegalStateException e) {
            assertEquals("Asking for inputs from a closed OrtSession.", e.getMessage());
            return true;
        }
    }
}