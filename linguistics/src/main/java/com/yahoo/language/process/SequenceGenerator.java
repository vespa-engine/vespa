// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import com.yahoo.api.annotations.Beta;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Generates scored discrete token sequences from prompts.
 *
 * @author gdonovan
 */
@Beta
public interface SequenceGenerator {

    /** ID of generator when none is explicitly given. */
    String defaultGeneratorId = "default";

    /** An instance of this which throws IllegalStateException if attempted used. */
    SequenceGenerator throwsOnUse = new FailingSequenceGenerator();

    /** Returns this generator instance as a map with the default generator name. */
    default Map<String, SequenceGenerator> asMap() {
        return asMap(defaultGeneratorId);
    }

    /** Returns this generator instance as a map with the given name. */
    default Map<String, SequenceGenerator> asMap(String name) {
        return Map.of(name, this);
    }

    List<GeneratedSequence> generate(Prompt prompt, Context context, Options options);

    record Options(int maxTokens, int beamWidth, int maxSequences, TokenConstraint constraint) {
        public static final Options DEFAULT = new Options(16, 1, 1, TokenConstraint.none());

        public Options {
            if (maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be positive");
            if (beamWidth <= 0) throw new IllegalArgumentException("beamWidth must be positive");
            if (maxSequences <= 0) throw new IllegalArgumentException("maxSequences must be positive");
            constraint = Objects.requireNonNullElse(constraint, TokenConstraint.none());
        }
    }

    record GeneratedSequence(DiscreteSequence sequence, double score, Completion.FinishReason finishReason) {
        public GeneratedSequence {
            sequence = Objects.requireNonNull(sequence);
            finishReason = Objects.requireNonNull(finishReason);
        }
    }

    @FunctionalInterface
    interface TokenConstraint {

        boolean isAllowed(List<Integer> prefix, int tokenId);

        /**
         * Returns whether the current prefix is a valid completed sequence.
         * For constrained SID decoding this can be backed by a prefix trie.
         */
        default boolean isComplete(List<Integer> prefix) {
            return true;
        }

        static TokenConstraint none() {
            return (prefix, tokenId) -> true;
        }
    }

    class Context extends InvocationContext<Context> {

        public Context(String destination) {
            super(destination);
        }

        public Context(String destination, Map<Object, Object> cache) {
            super(destination, cache);
        }

        public Context(Context other) {
            super(other);
        }

        public Context copy() {
            return new Context(this);
        }

        /** Return the component id or 'unknown' if not set. */
        public String getSequenceGeneratorId() { return getComponentId(); }

        /** Sets the component id. */
        public Context setSequenceGeneratorId(String componentId) {
            return setComponentId(componentId);
        }
    }

    class FailingSequenceGenerator implements SequenceGenerator {

        private final String message;

        public FailingSequenceGenerator() {
            this("No sequence generator has been configured");
        }

        public FailingSequenceGenerator(String message) {
            this.message = message;
        }

        @Override
        public List<GeneratedSequence> generate(Prompt prompt, Context context, Options options) {
            throw new IllegalStateException(message);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof FailingSequenceGenerator;
        }

        @Override
        public int hashCode() {
            return getClass().getName().hashCode();
        }

        public static Function<String, SequenceGenerator> factory() {
            return FailingSequenceGenerator::new;
        }
    }
}
