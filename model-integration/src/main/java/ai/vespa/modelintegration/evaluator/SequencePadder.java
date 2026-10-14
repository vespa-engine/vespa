// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import com.yahoo.tensor.DimensionSizes;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Pads the sequence inputs of a model to a few fixed lengths (buckets), so that requests of different lengths
 * have the same input shapes and a batching runtime can combine them into one inference call.
 * Padding is zeros after the real tokens, so padded tokens have a zero attention mask and a model that respects
 * the mask gives the same output for the real tokens. Outputs indexed by position are unpadded to the real
 * length while decoded, so callers never see the padding.
 * Sequence inputs are the inputs of shape [batch, sequence], and sequence outputs the outputs with an unbound
 * second dimension, unless the caller names them, see {@link SequencePadderOptions}.
 * That the model respects its mask is verified by evaluating a sample padded to the largest bucket and unpadded,
 * at construction when a sample can be made from the model's types, else on the first padded request.
 * If the outputs differ, padding is disabled with a warning.
 * An evaluator with padding buckets configured holds one instance and calls {@link #evaluate} around its own
 * evaluation; an evaluator without runs as if this class did not exist.
 *
 * @author ingridhabrekke
 * @author glebashnik
 */
public final class SequencePadder {

    private static final Logger log = Logger.getLogger(SequencePadder.class.getName());

    /**
     * The largest difference between padded and unpadded output, relative to the mean absolute value, attributed
     * to float arithmetic: that gives at most 0.2%, while attending to padding or dynamic quantization gives several %.
     */
    static final double VERIFICATION_TOLERANCE = 0.01;

    public enum State {
        /** Configured, but not yet verified not to change the output */
        UNVERIFIED,
        /** Verified and in use */
        ENABLED,
        /** Not configured, not applicable to the model, or found to change its output */
        DISABLED
    }

    /** The padding of one request: the length of its sequence inputs, and the length they are padded to. */
    public record Request(int length, int paddedLength) {

        public static Request unpadded(int length) { return new Request(length, length); }

        public boolean isPadded() { return paddedLength > length; }

    }

    /**
     * The slice of a padded output of shape [rows, paddedLength, rest...] that unpads it to [rows, length, rest...]:
     * each row keeps its first keptPerRow cells of the sourceStride it has in the padded output.
     */
    public record Slice(int rows, int keptPerRow, int sourceStride) {}

    /** Evaluates a model with the inputs padded as the request says, and the outputs cut back accordingly. */
    @FunctionalInterface
    public interface Evaluation {
        Map<String, Tensor> evaluate(Map<String, Tensor> inputs, Request request);
    }

    /** How many times verification may fail to evaluate before padding is disabled */
    private static final int MAX_VERIFICATION_FAILURES = 3;
    /** The length of the sample padding is verified with: short, so that it is shorter than even small buckets */
    private static final int SAMPLE_LENGTH = 4;

    private final int[] buckets; // Sorted and distinct
    private final Set<String> sequenceInputs;
    private final String maskName;
    private final boolean allInputsAreSequences;
    private final String modelName;
    /** Refined at verification to the outputs whose second dimension is the sequence length */
    private volatile Set<String> sequenceOutputs;
    private volatile State state;
    private int verificationFailures = 0;
    private boolean warnedAboutLengths = false;

    private SequencePadder(int[] buckets, Set<String> sequenceInputs, String maskName, boolean allInputsAreSequences,
                            Set<String> sequenceOutputs, String modelName, State state) {
        this.buckets = buckets;
        this.sequenceInputs = sequenceInputs;
        this.maskName = maskName;
        this.allInputsAreSequences = allInputsAreSequences;
        this.sequenceOutputs = sequenceOutputs;
        this.modelName = modelName;
        this.state = state;
    }

    /**
     * Returns the padding the options ask for, of a model with the given input and output types.
     * Disabled if the model has no attention mask of shape [batch, sequence].
     */
    public static SequencePadder of(SequencePadderOptions options, Map<String, TensorType> inputTypes,
                                     Map<String, TensorType> outputTypes, String modelName) {
        var sequenceInputs = inputTypes.entrySet().stream()
                                       .filter(input -> isSequenceType(input.getValue(), 2))
                                       .map(Map.Entry::getKey)
                                       .collect(Collectors.toUnmodifiableSet());
        var mask = options.attentionMaskName();
        if ( ! sequenceInputs.contains(mask)) {
            log.warning("Not padding inputs of " + modelName + ": it has no attention mask input '" + mask +
                        "' of shape [batch, sequence], so it would attend to the padding. Inputs: " + inputTypes);
            return disabled(modelName);
        }

        var sequenceOutputs = outputTypes.entrySet().stream()
                                         .filter(output -> isSequenceType(output.getValue(), Integer.MAX_VALUE))
                                         .map(Map.Entry::getKey)
                                         .collect(Collectors.toUnmodifiableSet());

        var buckets = options.buckets().stream().mapToInt(Integer::intValue).sorted().distinct().toArray();
        if (options.maxLength().isPresent() && buckets[buckets.length - 1] > options.maxLength().getAsInt()) {
            int maxLength = options.maxLength().getAsInt();
            var capped = Arrays.stream(buckets).map(bucket -> Math.min(bucket, maxLength)).distinct().toArray();
            log.warning("Padding inputs of " + modelName + " to " + Arrays.toString(capped) + " instead of " +
                        Arrays.toString(buckets) + ", since it accepts at most " + maxLength + " tokens");
            buckets = capped;
        }

        return new SequencePadder(buckets, sequenceInputs, mask, sequenceInputs.size() == inputTypes.size(),
                                   sequenceOutputs, modelName, State.UNVERIFIED);
    }

    private static SequencePadder disabled(String modelName) {
        return new SequencePadder(new int[0], Set.of(), "", false, Set.of(), modelName, State.DISABLED);
    }

    /**
     * Returns whether the options ask for the batching that makes padding worthwhile: a max size above 1
     * on a GPU, where a batch is cheaper per request. A CPU does the same work either way.
     */
    public static boolean isBatchingOnGpu(OnnxEvaluatorOptions options) {
        return options.batchingMaxSize() > 1 && options.requestingGpu();
    }

    /**
     * Returns whether the model at the path quantizes dynamically, so that padding changes its results:
     * the quantization scale is computed from the whole input. False if the path is not a readable local file.
     */
    public static boolean isDynamicallyQuantized(String modelPath) {
        var path = Path.of(modelPath);
        if ( ! Files.isRegularFile(path)) return false;
        try {
            if ( ! OnnxStreamParser.usesDynamicQuantization(path)) return false;
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not read the operators of model " + modelPath +
                                   ", assuming it is not dynamically quantized", e);
            return false;
        }
        log.info("Not padding inputs of " + modelPath + ": the model is dynamically quantized, " +
                 "so padding would change its results");
        return true;
    }

    public State state() { return state; }

    /** Returns the smallest bucket that fits the length, or the length itself if none does */
    int paddedLength(int length) {
        for (int bucket : buckets) {
            if (bucket >= length) return bucket;
        }
        return length;
    }

    /**
     * Returns the padding of a request with the given inputs: unpadded if padding is disabled, or the request
     * has no sequence inputs (length 0), or they have different lengths.
     */
    Request requestFor(Map<String, Tensor> inputs) {
        int length = -1;
        for (var input : inputs.entrySet()) {
            if ( ! sequenceInputs.contains(input.getKey())) continue;
            if ( ! (input.getValue() instanceof IndexedTensor tensor)) return Request.unpadded(0); // Rejected by the runtime
            int inputLength = (int) tensor.shape()[1];
            if (length >= 0 && inputLength != length) {
                warnAboutLengths(inputs);
                return Request.unpadded(0);
            }
            length = inputLength;
        }
        if (length < 0) return Request.unpadded(0);
        return state == State.DISABLED ? Request.unpadded(length) : new Request(length, paddedLength(length));
    }

    private synchronized void warnAboutLengths(Map<String, Tensor> inputs) {
        if (warnedAboutLengths) return;
        warnedAboutLengths = true;
        log.warning("Not padding inputs of " + modelName + " with different lengths: " +
                    inputs.entrySet().stream()
                          .filter(i -> sequenceInputs.contains(i.getKey()))
                          .map(i -> i.getKey() + " " + i.getValue().type())
                          .collect(Collectors.joining(", ")));
    }

    /** Evaluates the inputs, padded if verified, and verifies on the first padded request if not yet done */
    public Map<String, Tensor> evaluate(Map<String, Tensor> inputs, Evaluation evaluation) {
        var request = requestFor(inputs);
        if ( ! request.isPadded()) return evaluation.evaluate(inputs, request);
        if (state == State.UNVERIFIED) {
            var verified = verifyWith(inputs, request, evaluation);
            if (verified != null) return verified;
        }
        // Decided, here or by a concurrent request: evaluate outside the lock
        return evaluation.evaluate(inputs, state == State.ENABLED ? request : Request.unpadded(request.length()));
    }

    /**
     * Verifies padding with a sample made from the model's types, padded to the largest bucket, if all the model's
     * inputs are sequences. Otherwise, or if the sample cannot be evaluated, verification waits for the first
     * padded request. Called once at construction.
     */
    public void verify(Evaluation evaluation) {
        if (state != State.UNVERIFIED || ! allInputsAreSequences) return;
        int length = Math.min(SAMPLE_LENGTH, largestBucket() - 1);
        if (length < 1) {
            log.warning("Not padding inputs of " + modelName + " to " + this + ": no bucket is longer than a sample");
            state = State.DISABLED;
            return;
        }
        try {
            verifyWith(sampleInputs(length), new Request(length, largestBucket()), evaluation);
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Could not verify padding inputs of " + modelName + " to " + this +
                                   " with a sample, will verify with the first padded request", e);
        }
    }

    /** The sample padding is verified with: ones for the mask, and zeros, a token of every vocabulary, for the rest */
    private Map<String, Tensor> sampleInputs(int length) {
        var type = new TensorType.Builder(TensorType.Value.FLOAT).indexed("d0", 1).indexed("d1", length).build();
        var inputs = new HashMap<String, Tensor>();
        for (var name : sequenceInputs) {
            var builder = (IndexedTensor.BoundBuilder) Tensor.Builder.of(type, DimensionSizes.of(type));
            if (name.equals(maskName))
                for (int i = 0; i < length; i++) builder.cellByDirectIndex(i, 1);
            inputs.put(name, builder.build());
        }
        return inputs;
    }

    /**
     * Verifies padding with the given inputs and returns their unpadded output, or null if padding was decided
     * meanwhile by a concurrent request, which then evaluates on its own. A failure to evaluate padded leaves
     * padding unverified, to be retried, until it has failed a few times.
     */
    private synchronized Map<String, Tensor> verifyWith(Map<String, Tensor> inputs, Request request, Evaluation evaluation) {
        if (state != State.UNVERIFIED) return null;

        var unpadded = evaluation.evaluate(inputs, Request.unpadded(request.length()));
        // The sequence outputs are those with the sequence length as their second dimension
        sequenceOutputs = unpadded.entrySet().stream()
                                  .filter(output -> sequenceOutputs.contains(output.getKey()))
                                  .filter(output -> ((IndexedTensor) output.getValue()).shape()[1] == request.length())
                                  .map(Map.Entry::getKey)
                                  .collect(Collectors.toUnmodifiableSet());
        Map<String, Tensor> padded;
        try {
            padded = evaluation.evaluate(inputs, request);
        } catch (RuntimeException e) {
            if (++verificationFailures < MAX_VERIFICATION_FAILURES) {
                log.log(Level.WARNING, "Could not verify that padding inputs of " + modelName + " to " + this +
                                       " does not change its output, will retry", e);
            } else {
                log.log(Level.WARNING, "Not padding inputs of " + modelName + " to " + this + ": could not verify " +
                                       "that padding does not change its output in " + verificationFailures + " attempts", e);
                state = State.DISABLED;
            }
            return unpadded;
        }
        var change = changeFromPadding(unpadded, padded);
        if (change != null) {
            log.warning("Not padding inputs of " + modelName + " to " + this + ": padding changes its output " +
                        change + ". To be padded, a model must ignore tokens with a zero attention mask, " +
                        "and must not be dynamically quantized.");
            state = State.DISABLED;
        } else {
            log.info("Padding inputs of " + modelName + " to " + this + ", cutting outputs " + sequenceOutputs +
                     " back, verified not to change its output");
            state = State.ENABLED;
        }
        return unpadded;
    }

    private int largestBucket() { return buckets[buckets.length - 1]; }

    /** Returns how the padded outputs differ from the unpadded, or null if within the tolerance */
    private static String changeFromPadding(Map<String, Tensor> unpadded, Map<String, Tensor> padded) {
        for (var output : unpadded.entrySet()) {
            var expected = (IndexedTensor) output.getValue();
            var actual = (IndexedTensor) padded.get(output.getKey());
            if (actual == null || ! Arrays.equals(expected.shape(), actual.shape()))
                return "'" + output.getKey() + "' from " + expected.type() + " to " + (actual == null ? "nothing" : actual.type());
            double difference = largestDifference(expected, actual);
            if (Double.isNaN(difference)) return "'" + output.getKey() + "' to NaN";
            if (difference > VERIFICATION_TOLERANCE)
                return "'" + output.getKey() + "' by up to " + Math.round(difference * 100) + "%";
        }
        return null;
    }

    /**
     * Returns the largest cell difference relative to the mean absolute value of the first tensor, so that a few
     * outliers, common in transformer outputs, do not hide changes to typical values. NaN if any cell is NaN.
     */
    private static double largestDifference(IndexedTensor expected, IndexedTensor actual) {
        double absoluteSum = 0;
        double difference = 0;
        long size = expected.size();
        for (long i = 0; i < size; i++) {
            double value = expected.get(i);
            absoluteSum += Math.abs(value);
            double cellDifference = Math.abs(value - actual.get(i));
            if (Double.isNaN(cellDifference)) return Double.NaN;
            difference = Math.max(difference, cellDifference);
        }
        double scale = size == 0 ? 0 : absoluteSum / size;
        return scale == 0 ? difference : difference / scale;
    }

    /** Returns the inputs with the sequence inputs padded as the request says */
    public Map<String, Tensor> pad(Map<String, Tensor> inputs, Request request) {
        if ( ! request.isPadded()) return inputs;
        var padded = new HashMap<String, Tensor>(inputs);
        for (var input : inputs.entrySet()) {
            if (sequenceInputs.contains(input.getKey()))
                padded.put(input.getKey(), pad((IndexedTensor) input.getValue(), request));
        }
        return padded;
    }

    /** Returns the [rows, length] tensor with each row zero-padded to the padded length */
    private IndexedTensor pad(IndexedTensor input, Request request) {
        long rows = input.shape()[0];
        var type = withSequenceLength(input.type(), request.paddedLength());
        var builder = (IndexedTensor.BoundBuilder) Tensor.Builder.of(type, DimensionSizes.of(type));
        for (long row = 0; row < rows; row++) {
            long source = row * request.length();
            long destination = row * request.paddedLength();
            for (int i = 0; i < request.length(); i++)
                builder.cellByDirectIndex(destination + i, input.get(source + i));
            // The rest of the row is already zero
        }
        return builder.build();
    }

    /**
     * Returns the slice that unpads an output of the given shape: null unless it is a sequence output with the
     * padded length as its second dimension.
     */
    public Slice sliceFor(String name, long[] shape, Request request) {
        if ( ! request.isPadded() || shape.length < 2 || shape[1] != request.paddedLength() || ! sequenceOutputs.contains(name))
            return null;
        int rest = 1;
        for (int i = 2; i < shape.length; i++) rest = Math.toIntExact(rest * shape[i]);
        return new Slice(Math.toIntExact(shape[0]), request.length() * rest, request.paddedLength() * rest);
    }

    /** Returns the type of a sliced output: the given type with the real length as its second dimension */
    public TensorType slicedType(TensorType type, Request request) {
        return withSequenceLength(type, request.length());
    }

    private static TensorType withSequenceLength(TensorType type, long length) {
        var builder = new TensorType.Builder(type.valueType());
        var dimensions = type.dimensions();
        for (int i = 0; i < dimensions.size(); i++) {
            var dimension = dimensions.get(i);
            builder.indexed(dimension.name(), i == 1 ? length : dimension.size().orElseThrow());
        }
        return builder.build();
    }

    /** Whether a type is indexed, of rank 2 to maxRank, with an unbound second dimension */
    private static boolean isSequenceType(TensorType type, int maxRank) {
        var dimensions = type.dimensions();
        if (dimensions.size() < 2 || dimensions.size() > maxRank) return false;
        return dimensions.stream().allMatch(TensorType.Dimension::isIndexed) && dimensions.get(1).size().isEmpty();
    }

    // For testing
    Set<String> sequenceInputs() { return sequenceInputs; }
    Set<String> sequenceOutputs() { return sequenceOutputs; }
    List<Integer> buckets() { return Arrays.stream(buckets).boxed().toList(); }

    @Override
    public String toString() {
        return buckets.length == 0 ? "no padding-buckets" : "padding-buckets " + Arrays.toString(buckets);
    }

}
