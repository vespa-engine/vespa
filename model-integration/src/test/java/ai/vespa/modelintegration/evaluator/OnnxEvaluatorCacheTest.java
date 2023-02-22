// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * @author bjorncs
 */
class OnnxEvaluatorCacheTest {

    @Test
    void reuses_instance_while_in_use() {
        var cache = new OnnxEvaluatorCache((__, ___) -> mock(OnnxEvaluator.class));
        var referencedEvaluator1 = cache.evaluatorOf("model1", new OnnxEvaluatorOptions());
        var referencedEvaluator2 = cache.evaluatorOf("model1", new OnnxEvaluatorOptions());
        var referencedEvaluator3 = cache.evaluatorOf("model2", new OnnxEvaluatorOptions());
        assertSame(referencedEvaluator1.evaluator(), referencedEvaluator2.evaluator());
        assertNotSame(referencedEvaluator1.evaluator(), referencedEvaluator3.evaluator());
        assertEquals(2, cache.size());
        referencedEvaluator1.close();
        referencedEvaluator2.close();
        assertEquals(1, cache.size());
        referencedEvaluator3.close();
        assertEquals(0, cache.size());
        var referencedEvaluator4 = cache.evaluatorOf("model1", new OnnxEvaluatorOptions());
        assertNotSame(referencedEvaluator1.evaluator(), referencedEvaluator4.evaluator());
        assertEquals(1, cache.size());
        referencedEvaluator4.close();
        assertEquals(0, cache.size());
    }

}