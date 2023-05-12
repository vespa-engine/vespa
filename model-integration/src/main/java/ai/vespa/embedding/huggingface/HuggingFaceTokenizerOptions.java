// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.embedding.huggingface;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * @author bjorncs
 */
public class HuggingFaceTokenizerOptions {

    private final Boolean addSpecialToken;
    private final Boolean truncation;
    private final boolean truncateFirstOnly;
    private final boolean truncateSecondOnly;
    private final Boolean padding;
    private final boolean padToMaxLength;
    private final Integer maxLength;
    private final Integer padToMultipleOf;
    private final Integer stride;

    private HuggingFaceTokenizerOptions(Builder b) {
        addSpecialToken = b.addSpecialToken;
        truncation = b.truncation;
        truncateFirstOnly = b.truncateFirstOnly;
        truncateSecondOnly = b.truncateSecondOnly;
        padding = b.padding;
        padToMaxLength = b.padToMaxLength;
        maxLength = b.maxLength;
        padToMultipleOf = b.padToMultipleOf;
        stride = b.stride;
    }

    public static Builder custom() { return new Builder(); }
    public static HuggingFaceTokenizerOptions defaults() { return new Builder().build(); }

    Optional<Boolean> addSpecialToken() { return Optional.ofNullable(addSpecialToken); }
    Optional<Boolean> truncation() { return Optional.ofNullable(truncation); }
    boolean truncateFirstOnly() { return truncateFirstOnly; }
    boolean truncateSecondOnly() { return truncateSecondOnly; }
    Optional<Boolean> padding() { return Optional.ofNullable(padding); }
    boolean padToMaxLength() { return padToMaxLength; }
    OptionalInt maxLength() { return maxLength != null ? OptionalInt.of(maxLength) : OptionalInt.empty(); }
    OptionalInt padToMultipleOf() { return padToMultipleOf != null ? OptionalInt.of(padToMultipleOf) : OptionalInt.empty(); }
    OptionalInt stride() { return stride != null ? OptionalInt.of(stride) : OptionalInt.empty(); }

    public static class Builder {
        private Boolean addSpecialToken;
        private Boolean truncation;
        private boolean truncateFirstOnly;
        private boolean truncateSecondOnly;
        private Boolean padding;
        private boolean padToMaxLength;
        private Integer maxLength;
        private Integer padToMultipleOf;
        private Integer stride;

        public Builder addSpecialToken(boolean enabled) { addSpecialToken = enabled; return this; }
        public Builder truncation(boolean enabled) { truncation = enabled; return this; }
        public Builder truncateFirstOnly() { truncateFirstOnly = true; return this; }
        public Builder truncateSecondOnly() { truncateSecondOnly = true; return this; }
        public Builder padding(boolean enabled) { padding = enabled; return this; }
        public Builder padToMaxLength() { padToMaxLength = true; return this; }
        public Builder maxLength(int length) { maxLength = length; return this; }
        public Builder padToMultipleOf(int num) { padToMultipleOf = num; return this; }
        public Builder stride(int stride) { this.stride = stride; return this; }
        public HuggingFaceTokenizerOptions build() { return new HuggingFaceTokenizerOptions(this); }
    }

}
