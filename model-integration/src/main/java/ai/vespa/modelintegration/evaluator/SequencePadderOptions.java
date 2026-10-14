// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.evaluator;

import ai.vespa.modelintegration.evaluator.config.OnnxEvaluatorConfig;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Options for padding sequence inputs to fixed lengths, see {@link SequencePadder}.
 * The buckets come from the application's configuration; the rest is what the caller knows about the model.
 * Absent padding options mean no padding, so there is always at least one bucket.
 *
 * @param buckets       lengths to pad up to: the smallest that fits, or no padding if none fits
 * @param attentionMask the attention mask input, which must exist for padding to be safe. Default attention_mask.
 * @param maxLength     the longest input the model gets, which caps the buckets
 *
 * @author glebashnik
 */
public record SequencePadderOptions(List<Integer> buckets,
                                    Optional<String> attentionMask,
                                    OptionalInt maxLength) {

    private static final String DEFAULT_ATTENTION_MASK = "attention_mask";

    public SequencePadderOptions {
        Objects.requireNonNull(buckets, "buckets cannot be null");
        Objects.requireNonNull(attentionMask, "attentionMask cannot be null");
        Objects.requireNonNull(maxLength, "maxLength cannot be null");
        buckets = List.copyOf(buckets);
        if (buckets.isEmpty())
            throw new IllegalArgumentException("Padding needs at least one bucket");
        if (buckets.stream().anyMatch(bucket -> bucket < 1))
            throw new IllegalArgumentException("Padding buckets must be positive, got " + buckets);
    }

    /** Returns options padding to the given buckets */
    public static SequencePadderOptions of(List<Integer> buckets) {
        return new SequencePadderOptions(buckets, Optional.empty(), OptionalInt.empty());
    }

    /** Returns the options the config asks for, if it configures padding buckets */
    public static Optional<SequencePadderOptions> of(OnnxEvaluatorConfig config) {
        var buckets = config.batching().paddingBuckets();
        return buckets.isEmpty() ? Optional.empty() : Optional.of(of(buckets));
    }

    public String attentionMaskName() { return attentionMask.orElse(DEFAULT_ATTENTION_MASK); }

    public SequencePadderOptions withAttentionMask(String name) {
        return new SequencePadderOptions(buckets, Optional.of(name), maxLength);
    }

    /** Caps the buckets at the given length, if it is known: a length below 1 means unknown */
    public SequencePadderOptions withMaxLength(int length) {
        return length < 1 ? this : new SequencePadderOptions(buckets, attentionMask, OptionalInt.of(length));
    }

}
