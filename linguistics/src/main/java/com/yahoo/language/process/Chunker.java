// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.collections.LazyMap;

import java.util.List;
import java.util.Map;

/**
 * A chunker converts splits a text string into multiple smaller strings (chunks).
 * This is typically used for large pieces of text that should be split into many chunks for
 * vector embedding.
 *
 * @author bratseth
 */
public interface Chunker {

    /** ID of chunker when none is explicitly given */
    String defaultChunkerId = "default";

    /** An instance of this which throws IllegalStateException if attempted used */
    Chunker throwsOnUse = new FailingChunker();

    /** Returns this chunker instance as a map with the default chunked name */
    default Map<String, Chunker> asMap() {
        return asMap(defaultChunkerId);
    }

    /** Returns this chunker instance as a map with the given name */
    default Map<String, Chunker> asMap(String name) {
        return Map.of(name, this);
    }

    /**
     * Splits a text into multiple chunks. The chunks should preferably contain all the content
     * of the original text, and can be overlapping.
     *
     * @param text the text to split into chunks
     * @param context the context which may influence a chunker's behavior
     * @return the resulting chunks
     * @throws IllegalArgumentException if the language is not supported by this
     */
    List<Chunk> chunk(String text, Context context);

    record Chunk(String text) {

        @Override
        public String toString() {
            return "chunk '" + text() + "'";
        }

    }

    class Context extends InvocationContext<Context> {

        public Context(String destination) {
            this(destination, LazyMap.newHashMap());
        }

        public Context(String destination, Map<Object, Object> cache) {
            super(destination, cache);
        }

    }

    class FailingChunker implements Chunker {

        private final String message;

        public FailingChunker() {
            this("No chunker has been configured");
        }

        public FailingChunker(String message) {
            this.message = message;
        }

        @Override
        public List<Chunk> chunk(String text, Context context) {
            throw new IllegalStateException(message);
        }

    }

}
