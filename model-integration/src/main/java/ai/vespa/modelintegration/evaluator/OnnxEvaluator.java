// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.evaluator;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Evaluator for ONNX models.
 *
 * @author bjorncs
 * @author glebashnik
 */
public interface OnnxEvaluator extends AutoCloseable {

    record IdAndType(String id, TensorType type) { }

    /**
     * Evaluates the given output without a deadline.
     * An evaluator can override this to warn about the missing deadline, or to add other behavior.
     */
    default Tensor evaluate(Map<String, Tensor> inputs, String output) {
        return evaluate(inputs, output, Optional.empty());
    }

    /**
     * Evaluates all outputs without a deadline.
     * An evaluator can override this to warn about the missing deadline, or to add other behavior.
     */
    default Map<String, Tensor> evaluate(Map<String, Tensor> inputs) {
        return evaluate(inputs, Optional.empty());
    }

    /**
     * Evaluates the given output with a deadline.
     * An empty deadline means the default timeout of the runtime, if the runtime has one.
     */
    Tensor evaluate(Map<String, Tensor> inputs, String output, Optional<Instant> deadline);

    /**
     * Evaluates all outputs with a deadline.
     * An empty deadline means the default timeout of the runtime, if the runtime has one.
     */
    Map<String, Tensor> evaluate(Map<String, Tensor> inputs, Optional<Instant> deadline);

    /** Evaluates the given output with the given timeout. */
    default Tensor evaluate(Map<String, Tensor> inputs, String output, Duration timeout) {
        return evaluate(inputs, output, deadlineAfter(timeout));
    }

    /** Evaluates all outputs with the given timeout. */
    default Map<String, Tensor> evaluate(Map<String, Tensor> inputs, Duration timeout) {
        return evaluate(inputs, deadlineAfter(timeout));
    }

    private static Optional<Instant> deadlineAfter(Duration timeout) {
        return Optional.of(Instant.now().plus(Objects.requireNonNull(timeout, "timeout")));
    }

    Map<String, OnnxEvaluator.IdAndType> getInputs();
    Map<String, OnnxEvaluator.IdAndType> getOutputs();
    Map<String, TensorType> getInputInfo();
    Map<String, TensorType> getOutputInfo();

    /**
     * Returns the length that an input sequence is padded to given its original length.
     * For an {@link OnnxPadder} this is a bucket length; evaluators which do not pad return the length unchanged.
     */
    default int paddedLength(int length) { return length; }

    /** Returns the pooling this evaluator's model is configured with, or empty if it returns the output as is. */
    default Optional<OnnxPoolingOptions> runtimePooling() { return Optional.empty(); }

    @Override void close();

}
