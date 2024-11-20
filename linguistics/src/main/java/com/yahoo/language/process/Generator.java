package com.yahoo.language.process;

import com.yahoo.collections.LazyMap;
import com.yahoo.language.Language;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public interface Generator {

    // Name of generator when none is explicitly given
    String defaultGeneratorId = "default";

    // An instance of this which throws IllegalStateException if attempted used
    Generator throwsOnUse = new FailingGenerator();

    // Returns this generator instance as a map with the default generator name
    default Map<String, Generator> asMap() {
        return asMap(defaultGeneratorId);
    }

    // Returns this generator instance as a map with the given name
    default Map<String, Generator> asMap(String name) {
        return Map.of(name, this);
    }

    String generate(String prompt, Context context);


    class Context {
        private Language language = Language.UNKNOWN;
        private String destination;
        private String generatorId = "unknown";
        private final Map<Object, Object> cache;

        public Context(String destination) {
            this(destination, LazyMap.newHashMap());
        }

        /**
         * @param destination the name of the recipient of the generated output
         * @param cache a cache shared between all generate invocations for a single request
         */
        public Context(String destination, Map<Object, Object> cache) {
            this.destination = destination;
            this.cache = Objects.requireNonNull(cache);
        }

        private Context(Context other) {
            language = other.language;
            destination = other.destination;
            generatorId = other.generatorId;
            this.cache = other.cache;
        }

        public Generator.Context copy() { return new Context(this); }

        /** Returns the language of the text, or UNKNOWN (default) to use a language independent generation */
        public Language getLanguage() { return language; }

        /** Sets the language of the text, or UNKNOWN to use language independent generation */
        public Context setLanguage(Language language) {
            this.language = language != null ? language : Language.UNKNOWN;
            return this;
        }

        /**
         * Returns the name of the recipient of this tensor.
         * This is either a query feature name
         * ("query(feature)"), or a schema and field name concatenated by a dot ("schema.field").
         * This cannot be null.
         */
        public String getDestination() { return destination; }

        /**
         * Sets the name of the recipient of this tensor.
         * This is either a query feature name
         * ("query(feature)"), or a schema and field name concatenated by a dot ("schema.field").
         */
        public Context setDestination(String destination) {
            this.destination = destination;
            return this;
        }

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
    
    class FailingGenerator implements Generator {
        private final String message;

        public FailingGenerator() {
            this("No generator has been configured");
        }

        public FailingGenerator(String message) {
            this.message = message;
        }
        
        public String generate(String prompt, Context context) {
            throw new IllegalStateException(message);
        }
    }

}
