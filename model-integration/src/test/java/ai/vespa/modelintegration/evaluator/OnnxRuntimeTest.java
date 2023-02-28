// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * @author bjorncs
 */
class OnnxRuntimeTest {

    @Test
    void reuses_sessions_while_active() throws OrtException {
        var runtime = new OnnxRuntime((__, ___) -> mock(OrtSession.class));
        var session1 = runtime.acquireSession("model1", new OnnxEvaluatorOptions(), false);
        var session2 = runtime.acquireSession("model1", new OnnxEvaluatorOptions(), false);
        var session3 = runtime.acquireSession("model2", new OnnxEvaluatorOptions(), false);
        assertSame(session1.instance(), session2.instance());
        assertNotSame(session1.instance(), session3.instance());
        assertEquals(2, runtime.sessionsCached());

        session1.close();
        session2.close();
        assertEquals(1, runtime.sessionsCached());
        verify(session1.instance()).close();
        verify(session3.instance(), never()).close();

        session3.close();
        assertEquals(0, runtime.sessionsCached());
        verify(session3.instance()).close();

        var session4 = runtime.acquireSession("model1", new OnnxEvaluatorOptions(), false);
        assertNotSame(session1.instance(), session4.instance());
        assertEquals(1, runtime.sessionsCached());
        session4.close();
        assertEquals(0, runtime.sessionsCached());
        verify(session4.instance()).close();
    }
}