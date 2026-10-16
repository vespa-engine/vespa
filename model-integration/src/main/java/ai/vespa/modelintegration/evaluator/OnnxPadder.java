// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import com.yahoo.tensor.DimensionSizes;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * An evaluator which pads the sequence inputs of the evaluator it wraps to a few fixed lengths (buckets), so that
 * requests of different lengths have the same input shapes and a batching runtime can combine them into one
 * inference call.
 * Padding is zeros after the real tokens, so padded tokens have a zero attention mask and a model that respects
 * the mask gives the same output for the real tokens. Outputs indexed by position are cut back to the real length,
 * so callers never see the padding.
 * Sequence inputs are the inputs of shape [batch, sequence], among them the attention mask named in the
 * {@link OnnxPadderOptions}, and sequence outputs the outputs with an unbound second dimension. An input longer
 * than the largest bucket is not padded.
 * {@link #of} verifies that the model respects its mask, by evaluating a sample padded to the largest bucket and
 * unpadded, and returns the wrapped evaluator itself, with a warning, if the outputs differ, or if the model
 * cannot be padded: it has no attention mask, or inputs that are not sequences.
 *
 * @author ingridhabrekke
 * @author glebashnik
 */
public final class OnnxPadder implements OnnxEvaluator {

    private static final Logger log = Logger.getLogger(OnnxPadder.class.getName());

    /**
     * The largest relative difference between padded and unpadded output attributed to arithmetic: the root mean
     * square of the cell differences over that of the unpadded output. Measured on a GPU, padding changes an
     * fp32 model's output by under 0.01%, and an fp16 model's by up to 0.7%, in single cells rounded differently
     * at another batch shape, while attending to the padding changes every token's output by tens of percent.
     */
    static final double VERIFICATION_TOLERANCE = 0.02;

    /** The length of the sample padding is verified with: short, so that it is shorter than even small buckets */
    private static final int SAMPLE_LENGTH = 4;

    /** The padding of one request: the length of its sequence inputs, and the length they are padded to. */
    private record PaddingInfo(int length, int paddedLength) {

        static PaddingInfo none(int length) { return new PaddingInfo(length, length); }

        boolean isPadded() { return paddedLength > length; }

    }

    private final OnnxEvaluator evaluator;
    private final int[] buckets; // Sorted and distinct
    private final Set<String> sequenceInputs;
    private final String maskName;
    /** Refined at verification to the outputs whose second dimension is the sequence length */
    private volatile Set<String> sequenceOutputs;
    private final String modelName;
    private boolean warnedAboutLengths = false;

    private OnnxPadder(OnnxEvaluator evaluator, int[] buckets, Set<String> sequenceInputs, String maskName,
                            Set<String> sequenceOutputs, String modelName) {
        this.evaluator = evaluator;
        this.buckets = buckets;
        this.sequenceInputs = sequenceInputs;
        this.maskName = maskName;
        this.sequenceOutputs = sequenceOutputs;
        this.modelName = modelName;
    }

    /**
     * Returns the evaluator wrapped in the padding the options ask for, verified not to change its output,
     * or the evaluator itself if it cannot be padded.
     */
    public static OnnxEvaluator of(OnnxPadderOptions options, OnnxEvaluator evaluator, String modelName) {
        var padder = padderOf(options, evaluator, modelName);
        return padder.isPresent() && padder.get().verify() ? padder.get() : evaluator;
    }

    /**
     * Returns the padding of the evaluator the options ask for, not yet verified, or empty if the model cannot be
     * padded. Separate from {@link #of} so that the padding can be tested apart from its verification.
     */
    static Optional<OnnxPadder> padderOf(OnnxPadderOptions options, OnnxEvaluator evaluator, String modelName) {
        var inputTypes = evaluator.getInputInfo();
        var sequenceInputs = inputTypes.entrySet().stream()
                                       .filter(input -> isSequenceType(input.getValue(), 2))
                                       .map(Map.Entry::getKey)
                                       .collect(Collectors.toUnmodifiableSet());
        var mask = options.attentionMaskName();
        if ( ! sequenceInputs.contains(mask)) {
            log.warning("Not padding inputs of " + modelName + ": it has no attention mask input '" + mask +
                        "' of shape [batch, sequence], so it would attend to the padding. Inputs: " + inputTypes);
            return Optional.empty();
        }
        if (sequenceInputs.size() != inputTypes.size()) {
            log.warning("Not padding inputs of " + modelName + ": cannot make a sample to verify that padding does not " +
                        "change its output, since not all inputs are sequences of shape [batch, sequence]: " + inputTypes);
            return Optional.empty();
        }

        var sequenceOutputs = evaluator.getOutputInfo().entrySet().stream()
                                       .filter(output -> isSequenceType(output.getValue(), Integer.MAX_VALUE))
                                       .map(Map.Entry::getKey)
                                       .collect(Collectors.toUnmodifiableSet());

        var buckets = options.buckets().stream().mapToInt(Integer::intValue).toArray(); // Sorted and distinct
        if (buckets[buckets.length - 1] < 2) {
            log.warning("Not padding inputs of " + modelName + " to " + Arrays.toString(buckets) + ": no bucket is longer than a sample");
            return Optional.empty();
        }

        return Optional.of(new OnnxPadder(evaluator, buckets, sequenceInputs, mask, sequenceOutputs, modelName));
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

    /** Returns the buckets, sorted */
    public List<Integer> buckets() { return Arrays.stream(buckets).boxed().toList(); }

    @Override
    public Tensor evaluate(Map<String, Tensor> inputs, String output, Duration timeout) {
        var paddingInfo = paddingInfoFor(inputs);
        return unpad(output, evaluator.evaluate(pad(inputs, paddingInfo), output, timeout), paddingInfo);
    }

    @Override
    public Map<String, Tensor> evaluate(Map<String, Tensor> inputs, Duration timeout) {
        var paddingInfo = paddingInfoFor(inputs);
        return unpad(evaluator.evaluate(pad(inputs, paddingInfo), timeout), paddingInfo);
    }

    @Override public Map<String, IdAndType> getInputs() { return evaluator.getInputs(); }
    @Override public Map<String, IdAndType> getOutputs() { return evaluator.getOutputs(); }
    @Override public Map<String, TensorType> getInputInfo() { return evaluator.getInputInfo(); }
    @Override public Map<String, TensorType> getOutputInfo() { return evaluator.getOutputInfo(); }
    @Override public void close() { evaluator.close(); }

    /**
     * Verifies that padding does not change the output of the model: evaluates a sample unpadded and padded to the
     * largest bucket, and returns whether the outputs are the same within a tolerance for float arithmetic.
     * False, with a warning, if they differ or the sample cannot be evaluated.
     */
    boolean verify() {
        int length = Math.min(SAMPLE_LENGTH, largestBucket() - 1);
        var sample = sampleInputs(length);
        var paddingInfo = new PaddingInfo(length, largestBucket());
        Map<String, Tensor> unpaddedOutputs, paddedOutputs;
        try {
            unpaddedOutputs = evaluator.evaluate(sample);
            // An output typed with an unbound second dimension that is not the sequence length is not cut
            sequenceOutputs = unpaddedOutputs.entrySet().stream()
                                             .filter(output -> sequenceOutputs.contains(output.getKey()))
                                             .filter(output -> ((IndexedTensor) output.getValue()).shape()[1] == length)
                                             .map(Map.Entry::getKey)
                                             .collect(Collectors.toUnmodifiableSet());
            paddedOutputs = unpad(evaluator.evaluate(pad(sample, paddingInfo)), paddingInfo);
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Not padding inputs of " + modelName + " to " + this + ": could not evaluate " +
                                   "a sample to verify that padding does not change its output", e);
            return false;
        }
        var change = changeFromPadding(unpaddedOutputs, paddedOutputs);
        if (change != null) {
            log.warning("Not padding inputs of " + modelName + " to " + this + ": padding changes its output " +
                        change + ". To be padded, a model must ignore tokens with a zero attention mask, " +
                        "and must not be dynamically quantized.");
            return false;
        }
        log.info("Padding inputs of " + modelName + " to " + this + ", cutting outputs " + sequenceOutputs +
                 " back, verified not to change its output");
        return true;
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

    private int largestBucket() { return buckets[buckets.length - 1]; }

    /** Returns the smallest bucket that fits the length, or the length itself if none does */
    private int paddedLength(int length) {
        for (int bucket : buckets) {
            if (bucket >= length) return bucket;
        }
        return length;
    }

    /**
     * Returns the padding of a request with the given inputs: none (length 0) if the request has no attention
     * mask, without which the model would attend to the padding, or sequence inputs of different lengths.
     */
    private PaddingInfo paddingInfoFor(Map<String, Tensor> inputs) {
        if ( ! inputs.containsKey(maskName)) return PaddingInfo.none(0);
        int length = -1;
        for (var input : inputs.entrySet()) {
            if ( ! sequenceInputs.contains(input.getKey())) continue;
            if ( ! (input.getValue() instanceof IndexedTensor tensor)) return PaddingInfo.none(0); // Rejected by the evaluator
            int inputLength = (int) tensor.shape()[1];
            if (length >= 0 && inputLength != length) {
                warnAboutLengths(inputs);
                return PaddingInfo.none(0);
            }
            length = inputLength;
        }
        if (length < 0) return PaddingInfo.none(0);
        return new PaddingInfo(length, paddedLength(length));
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

    /** Returns the inputs with the sequence inputs padded as the padding info says, or the inputs if it is not padded */
    private Map<String, Tensor> pad(Map<String, Tensor> inputs, PaddingInfo paddingInfo) {
        if ( ! paddingInfo.isPadded()) return inputs;
        var padded = new HashMap<String, Tensor>(inputs);
        for (var input : inputs.entrySet()) {
            if (sequenceInputs.contains(input.getKey()))
                padded.put(input.getKey(), pad((IndexedTensor) input.getValue(), paddingInfo));
        }
        return padded;
    }

    /** Returns the [rows, length] tensor with each row zero-padded to the padded length */
    private static IndexedTensor pad(IndexedTensor input, PaddingInfo paddingInfo) {
        long rows = input.shape()[0];
        var type = withSequenceLength(input.type(), paddingInfo.paddedLength());
        var builder = (IndexedTensor.BoundBuilder) Tensor.Builder.of(type, DimensionSizes.of(type));
        for (long row = 0; row < rows; row++) {
            long source = row * paddingInfo.length();
            long destination = row * paddingInfo.paddedLength();
            for (int i = 0; i < paddingInfo.length(); i++)
                builder.cellByDirectIndex(destination + i, input.get(source + i));
            // The rest of the row is already zero
        }
        return builder.build();
    }

    /** Returns the outputs with the sequence outputs of the padded length cut back to the real length */
    private Map<String, Tensor> unpad(Map<String, Tensor> outputs, PaddingInfo paddingInfo) {
        if ( ! paddingInfo.isPadded()) return outputs;
        var unpadded = new HashMap<String, Tensor>(outputs);
        outputs.forEach((name, output) -> unpadded.put(name, unpad(name, output, paddingInfo)));
        return unpadded;
    }

    /** Returns the output cut back to the real length if it is a sequence output of the padded length, else as is */
    private Tensor unpad(String name, Tensor output, PaddingInfo paddingInfo) {
        if ( ! paddingInfo.isPadded() || ! sequenceOutputs.contains(name)
            || ! (output instanceof IndexedTensor tensor)
            || tensor.shape().length < 2 || tensor.shape()[1] != paddingInfo.paddedLength())
            return output;
        var shape = tensor.shape();
        long rest = 1;
        for (int i = 2; i < shape.length; i++) rest *= shape[i];
        long kept = paddingInfo.length() * rest;
        long stride = paddingInfo.paddedLength() * rest;
        var type = withSequenceLength(tensor.type(), paddingInfo.length());
        var builder = (IndexedTensor.BoundBuilder) Tensor.Builder.of(type, DimensionSizes.of(type));
        long destination = 0;
        for (long row = 0; row < shape[0]; row++) {
            long source = row * stride;
            for (long i = 0; i < kept; i++)
                builder.cellByDirectIndex(destination++, tensor.get(source + i));
        }
        return builder.build();
    }

    /** Returns how the padded outputs differ from the unpadded, or null if within the tolerance */
    private static String changeFromPadding(Map<String, Tensor> unpadded, Map<String, Tensor> padded) {
        for (var output : unpadded.entrySet()) {
            var expected = (IndexedTensor) output.getValue();
            var actual = (IndexedTensor) padded.get(output.getKey());
            if (actual == null || ! Arrays.equals(expected.shape(), actual.shape()))
                return "'" + output.getKey() + "' from " + expected.type() + " to " + (actual == null ? "nothing" : actual.type());
            double difference = relativeDifference(expected, actual);
            if (Double.isNaN(difference)) return "'" + output.getKey() + "' to NaN";
            if (difference > VERIFICATION_TOLERANCE)
                return "'" + output.getKey() + "' by " + Math.round(difference * 100) + "%";
        }
        return null;
    }

    /**
     * Returns the root mean square of the cell differences relative to the root mean square of the expected cells,
     * so that a few cells rounded differently do not count as a change. NaN if any cell is NaN.
     */
    private static double relativeDifference(IndexedTensor expected, IndexedTensor actual) {
        double squaredSum = 0;
        double squaredDifference = 0;
        long size = expected.size();
        for (long i = 0; i < size; i++) {
            double value = expected.get(i);
            double cellDifference = value - actual.get(i);
            if (Double.isNaN(cellDifference)) return Double.NaN;
            squaredSum += value * value;
            squaredDifference += cellDifference * cellDifference;
        }
        return squaredSum == 0 ? Math.sqrt(squaredDifference) : Math.sqrt(squaredDifference / squaredSum);
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

    @Override
    public String toString() {
        return "padding-buckets " + Arrays.toString(buckets);
    }

}
