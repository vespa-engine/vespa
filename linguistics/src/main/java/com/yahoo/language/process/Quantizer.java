// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.api.annotations.Beta;
import com.yahoo.tensor.Tensor;

import java.util.Map;
import java.util.function.Function;

/**
 * Converts embeddings into discrete token sequences, such as semantic IDs.
 *
 * @author gdonovan
 */
@Beta
public interface Quantizer {

    /** ID of quantizer when none is explicitly given. */
    String defaultQuantizerId = "default";

    /** An instance of this which throws IllegalStateException if attempted used. */
    Quantizer throwsOnUse = new FailingQuantizer();

    /** Returns this quantizer instance as a map with the default quantizer name. */
    default Map<String, Quantizer> asMap() {
        return asMap(defaultQuantizerId);
    }

    /** Returns this quantizer instance as a map with the given name. */
    default Map<String, Quantizer> asMap(String name) {
        return Map.of(name, this);
    }

    DiscreteSequence quantize(Tensor embedding, Context context);

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
        public String getQuantizerId() { return getComponentId(); }

        /** Sets the component id. */
        public Context setQuantizerId(String componentId) {
            return setComponentId(componentId);
        }
    }

    class FailingQuantizer implements Quantizer {

        private final String message;

        public FailingQuantizer() {
            this("No quantizer has been configured");
        }

        public FailingQuantizer(String message) {
            this.message = message;
        }

        @Override
        public DiscreteSequence quantize(Tensor embedding, Context context) {
            throw new IllegalStateException(message);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof FailingQuantizer;
        }

        @Override
        public int hashCode() {
            return getClass().getName().hashCode();
        }

        public static Function<String, Quantizer> factory() {
            return FailingQuantizer::new;
        }
    }
}
