// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.embedding;

import ai.vespa.modelintegration.evaluator.OnnxEvaluator;
import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;
import com.yahoo.tensor.Tensor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pads token sequences up to a few fixed lengths (buckets), so that requests of different lengths
 * have the same input shapes and can be combined into one inference call by a batching runtime.
 * <p>
 * Padding is appended after the real tokens, with a zero attention mask, so the model does not attend to it.
 * Code reading the model output must then ignore the padded positions, which are always a suffix.
 *
 * @author ingrid
 */
final class SequencePadder {

    private static final Logger log = Logger.getLogger(SequencePadder.class.getName());

    private static final SequencePadder DISABLED = new SequencePadder(new int[0]);

    /**
     * A text that embedders use to verify that padding does not change their model's output, see verifiedWith.
     * A single word, so that the sample, about 3 tokens with special tokens, is shorter than even small buckets.
     */
    static final String SAMPLE_TEXT = "Check";

    /**
     * The largest difference between the outputs for padded and unpadded input, relative to the mean absolute
     * output value, that is attributed to float arithmetic rather than to the model attending to the padding.
     * Differences from arithmetic are around 0.2% at most, while a model that attends to padding,
     * or is dynamically quantized, changes its output by several percent.
     */
    static final double VERIFICATION_TOLERANCE = 0.01;

    private final int[] buckets;

    private SequencePadder(int[] buckets) {
        this.buckets = buckets;
    }

    /** Returns a padder that never pads. */
    static SequencePadder disabled() { return DISABLED; }

    /**
     * Returns the padder to use with the given evaluator: padding to the configured buckets if the evaluator
     * should pad requests for batching, and disabled otherwise, or if no buckets are configured.
     * Buckets larger than maxTokens are replaced by maxTokens, since inputs are truncated to it.
     */
    static SequencePadder of(OnnxEvaluator evaluator, OnnxEvaluatorConfig config, int maxTokens, String modelName) {
        var buckets = config.batching().paddingBuckets();
        if (buckets.isEmpty()) return DISABLED;
        var padder = of(buckets);
        if ( ! evaluator.shouldPadForBatching()) {
            log.info("Not padding inputs of " + modelName + " to " + padder + ": the evaluator does not batch " +
                     "requests in a way padding helps with. Padding requires the Triton runtime with a batching " +
                     "max-size above 1, a GPU device, and a model that is not dynamically quantized.");
            return DISABLED;
        }
        if (padder.largestBucket() > maxTokens) {
            var capped = padder.cappedAt(maxTokens);
            log.warning("Padding inputs of " + modelName + " to " + capped + " instead of " + padder +
                        ", since it accepts at most " + maxTokens + " tokens");
            return capped;
        }
        return padder;
    }

    /** Returns a padder for the given buckets, which is disabled if there are none. */
    static SequencePadder of(List<Integer> buckets) {
        if (buckets.isEmpty()) return DISABLED;
        int[] sorted = buckets.stream().mapToInt(Integer::intValue).sorted().distinct().toArray();
        if (sorted[0] < 1)
            throw new IllegalArgumentException("Padding buckets must be positive, got " + buckets);
        return new SequencePadder(sorted);
    }

    /** Returns a padder where buckets larger than maxLength are replaced by maxLength. */
    SequencePadder cappedAt(int maxLength) {
        return of(Arrays.stream(buckets).map(bucket -> Math.min(bucket, maxLength)).boxed().toList());
    }

    /**
     * Verifies that padding does not change what the model outputs for the real tokens of a sample input.
     * The sample is padded to the largest bucket, so that it is verified also when its length equals a bucket.
     * Returns this padder if the outputs for padded and unpadded input are the same within
     * {@link #VERIFICATION_TOLERANCE}, and otherwise a disabled padder, after logging a warning.
     * Padding is also disabled if it cannot be verified, because no bucket is longer than the sample.
     * The outputs for the real tokens must come first, with any outputs for padded tokens after them.
     *
     * @param inputsOf     creates the model inputs of the sample, padded by the given padder
     * @param sampleLength the number of tokens in the sample before padding
     * @param modelName    the model, for logging
     */
    SequencePadder verifiedWith(OnnxEvaluator evaluator, Function<SequencePadder, Map<String, Tensor>> inputsOf,
                                int sampleLength, String modelName) {
        if (isDisabled()) return this;
        if (largestBucket() <= sampleLength) {
            log.warning("Not padding inputs of " + modelName + " to " + this + ": could not verify that padding " +
                        "does not change its output, since no bucket is longer than the sample of " + sampleLength + " tokens");
            return DISABLED;
        }
        Map<String, Tensor> unpadded, padded;
        try {
            unpadded = evaluator.evaluate(inputsOf.apply(DISABLED));
            padded = evaluator.evaluate(inputsOf.apply(of(List.of(largestBucket()))));
        }
        catch (RuntimeException e) { // Not worth failing the embedder over, as unpadded inputs are always correct
            log.log(Level.WARNING, "Not padding inputs of " + modelName + " to " + this +
                                   ": could not verify that padding does not change its output", e);
            return DISABLED;
        }
        for (var output : unpadded.entrySet()) {
            double difference = largestDifference(output.getValue(), padded.get(output.getKey()));
            if (Double.isNaN(difference) || difference > VERIFICATION_TOLERANCE) { // Comparisons with NaN are false
                String change = Double.isNaN(difference) ? "to NaN"
                              : Double.isInfinite(difference) ? "by leaving some of them out"
                              : "by up to " + Math.round(difference * 100) + "%";
                log.warning("Not padding inputs of " + modelName + " to " + this + ": padding changes its output '" +
                            output.getKey() + "' for the real tokens " + change + ". " +
                            "To be padded, a model must ignore tokens with a zero attention mask, " +
                            "and must not be dynamically quantized.");
                return DISABLED;
            }
        }
        log.info("Padding inputs of " + modelName + " to " + this + ", which was verified not to change its output");
        return this;
    }

    /**
     * Returns the largest difference between the cells of the first tensor and the same cells of the second,
     * relative to the mean absolute value in the first, so that a few large outlier values, as transformer outputs
     * often have, do not hide changes to the typical values. The second may have more cells than the first.
     */
    private static double largestDifference(Tensor unpadded, Tensor padded) {
        double absoluteSum = 0;
        long cells = 0;
        double difference = 0;
        for (var i = unpadded.cellIterator(); i.hasNext(); ) {
            var cell = i.next();
            absoluteSum += Math.abs(cell.getValue());
            cells++;
            try {
                difference = Math.max(difference, Math.abs(cell.getValue() - padded.get(cell.getKey())));
            } catch (RuntimeException e) { // The padded output lacks this cell
                return Double.POSITIVE_INFINITY;
            }
        }
        double scale = cells == 0 ? 0 : absoluteSum / cells;
        return scale == 0 ? difference : difference / scale;
    }

    boolean isDisabled() { return buckets.length == 0; }

    private int largestBucket() { return buckets[buckets.length - 1]; }

    /** Returns the smallest bucket that fits the given length, or the length itself if it fits no bucket. */
    int paddedLength(int length) {
        for (int bucket : buckets) {
            if (bucket >= length) return bucket;
        }
        return length;
    }

    /** Returns the values with padValue appended up to the padded length of their size. */
    <T> List<T> pad(List<T> values, T padValue) {
        int padded = paddedLength(values.size());
        if (padded == values.size()) return values;
        var result = new ArrayList<T>(padded);
        result.addAll(values);
        while (result.size() < padded) result.add(padValue);
        return result;
    }

    @Override
    public String toString() { return isDisabled() ? "no padding-buckets" : "padding-buckets " + Arrays.toString(buckets); }

}
