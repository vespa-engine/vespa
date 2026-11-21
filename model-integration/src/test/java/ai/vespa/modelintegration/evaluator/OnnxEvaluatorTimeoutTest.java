// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the overloads of {@link OnnxEvaluator#evaluate} that take a timeout.
 *
 * @author glebashnik
 */
class OnnxEvaluatorTimeoutTest {

    @Test
    void aTimeoutIsPassedAsADeadline() {
        var evaluator = new DeadlineRecordingEvaluator();
        var before = Instant.now();
        evaluator.evaluate(Map.of(), "output", Duration.ofSeconds(10));
        evaluator.evaluate(Map.of(), Duration.ofSeconds(20));
        var after = Instant.now();
        assertDeadlineBetween(before.plusSeconds(10), after.plusSeconds(10), evaluator.deadlines.get(0));
        assertDeadlineBetween(before.plusSeconds(20), after.plusSeconds(20), evaluator.deadlines.get(1));
    }

    @Test
    void aNullTimeoutFails() {
        var evaluator = new DeadlineRecordingEvaluator();
        assertThrows(NullPointerException.class, () -> evaluator.evaluate(Map.of(), "output", (Duration) null));
        assertThrows(NullPointerException.class, () -> evaluator.evaluate(Map.of(), (Duration) null));
        assertEquals(List.of(), evaluator.deadlines);
    }

    private static void assertDeadlineBetween(Instant earliest, Instant latest, Optional<Instant> deadline) {
        var instant = deadline.orElseThrow();
        assertFalse(instant.isBefore(earliest) || instant.isAfter(latest),
                    "Deadline " + instant + " is not between " + earliest + " and " + latest);
    }

    /** Records the deadline of each evaluation */
    private static class DeadlineRecordingEvaluator implements OnnxEvaluator {

        final List<Optional<Instant>> deadlines = new ArrayList<>();

        @Override
        public Tensor evaluate(Map<String, Tensor> inputs, String output, Optional<Instant> deadline) {
            deadlines.add(deadline);
            return Tensor.from("tensor():{1.0}");
        }

        @Override
        public Map<String, Tensor> evaluate(Map<String, Tensor> inputs, Optional<Instant> deadline) {
            deadlines.add(deadline);
            return Map.of();
        }

        @Override public Map<String, IdAndType> getInputs() { return Map.of(); }
        @Override public Map<String, IdAndType> getOutputs() { return Map.of(); }
        @Override public Map<String, TensorType> getInputInfo() { return Map.of(); }
        @Override public Map<String, TensorType> getOutputInfo() { return Map.of(); }
        @Override public void close() { }

    }

}
