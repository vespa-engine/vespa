package com.yahoo.language.process;

import com.yahoo.collections.LazyMap;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.language.Language;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Generates field value given a prompt.
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
    
    FieldValue generate(String input, Context context);
    
    class Context {
        private Language language = Language.UNKNOWN;
        private final String destination;
        private final DataType targetType;
        private String generatorId = "unknown";
        private final Map<Object, Object> cache;

        public Context(String destination, DataType targetType) {
            this(destination, targetType, LazyMap.newHashMap());
        }

        /**
         * @param destination the name of the recipient of the generated output
         * @param cache a cache shared between all generate invocations for a single request
         */
        public Context(String destination, DataType targetType, Map<Object, Object> cache) {
            this.destination = destination;
            this.targetType = targetType;
            this.cache = Objects.requireNonNull(cache);
        }

        private Context(Context other) {
            language = other.language;
            destination = other.destination;
            targetType = other.targetType;
            generatorId = other.generatorId;
            this.cache = other.cache;
        }

        public FieldGenerator.Context copy() { return new Context(this); }

        /** Returns the language of the text, or UNKNOWN (default) to use a language independent generation */
        public Language getLanguage() { return language; }

        /** Sets the language of the text, or UNKNOWN to use language independent generation */
        public Context setLanguage(Language language) {
            this.language = language != null ? language : Language.UNKNOWN;
            return this;
        }

        /**
         * Returns the name of the recipient of the generated text.
         * This is a schema and a field name concatenated by a dot ("schema.field").
         * This cannot be null.
         */
        public String getDestination() { return destination; }

        /** Returns the target type of the generated content. */
        public DataType getTargetType() { return targetType; }
        
        /** Return the generator id or 'unknown' if not set */
        public String getGeneratorId() { return generatorId; }

        /** Sets the generator id */
        public Context setGeneratorId(String generatorId) {
            this.generatorId = generatorId;
            return this;
        }

        public void putCachedValue(Object key, Object value) {
            cache.put(key, value);
        }

        /** Returns a cached value, or null if not present. */
        public Object getCachedValue(Object key) {
            return cache.get(key);
        }

        /** Returns the cached value, or computes and caches it if not present. */
        @SuppressWarnings("unchecked")
        public <T> T computeCachedValueIfAbsent(Object key, Supplier<? extends T> supplier) {
            return (T) cache.computeIfAbsent(key, __ -> supplier.get());
        }

    }
    
    class FailingFieldGenerator implements FieldGenerator {
        private final String message;

        public FailingFieldGenerator() {
            this("No generator has been configured");
        }

        public FailingFieldGenerator(String message) {
            this.message = message;
        }
        
        public FieldValue generate(String input, Context context) {
            throw new IllegalStateException(message);
        }
    }
    
}
