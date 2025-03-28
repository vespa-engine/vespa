package com.yahoo.language.process;

import ai.vespa.llm.completion.Prompt;
import com.yahoo.collections.LazyMap;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.language.Language;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Generates field values given an input text.
 * 
 * @author glebashnik
 */
public interface FieldGenerator {

    // Name of generator when none is explicitly given
    String defaultGeneratorId = "default";

    // An instance of this which throws IllegalStateException if attempted used
    FieldGenerator throwsOnUse = new FailingFieldGenerator();

    // Returns this generator instance as a map with the default generator name
    default Map<String, FieldGenerator> asMap() {
        return asMap(defaultGeneratorId);
    }

    // Returns this generator instance as a map with the given name
    default Map<String, FieldGenerator> asMap(String name) {
        return Map.of(name, this);
    }
    
    FieldValue generate(Prompt prompt, Context context);
    
    class Context extends InvocationContext<Context> {

        private final DataType targetType;

        public Context(String destination, DataType targetType) {
            this(destination, targetType, LazyMap.newHashMap());
        }

        /**
         * @param destination the name of the recipient of the generated output
         * @param cache a cache shared between all generate invocations for a single request
         */
        public Context(String destination, DataType targetType, Map<Object, Object> cache) {
            super(destination, cache);
            this.targetType = targetType;
        }

        /** Returns the target type of the generated content. */
        public DataType getTargetType() { return targetType; }
        
    }
    
    class FailingFieldGenerator implements FieldGenerator {

        private final String message;

        public FailingFieldGenerator() {
            this("No generator has been configured");
        }

        public FailingFieldGenerator(String message) {
            this.message = message;
        }
        
        public FieldValue generate(Prompt prompt, Context context) {
            throw new IllegalStateException(message);
        }
    }
    
}
