// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.Language;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.List;

/**
 * An embedder converts a text string to a tensor
 *
 * @author bratseth
 */
public interface Embedder {

    /** An instance of this which throws IllegalStateException if attempted used */
    Embedder throwsOnUse = new FailingEmbedder();

    /**
     * Converts text into a list of token id's (a vector embedding)
     *
     * @param text the text to embed
     * @param language the language of the text, or UNKNOWN to use language independent embedding
     * @param destination the name of the recipient of this tensor, either a query feature name
     *                    ("query(feature)"), or a schema and field name concatenated by a dot ("schema.field").
     *                    This is useful for embedder components that alters behavior depending on the receiver.
     * @return the text embedded as a list of token ids
     * @throws IllegalArgumentException if the language is not supported by this embedder
     */
    List<Integer> embed(String text, Language language, String destination);

    /**
     * Converts text into tokens in a tensor.
     * The information contained in the embedding may depend on the tensor type.
     *
     * @param text the text to embed
     * @param language the language of the text, or UNKNOWN to use language independent embedding
     * @param destination the name of the recipient of this tensor, either a query feature name
     *                    ("query(feature)"), or a schema and field name concatenated by a dot ("schema.field").
     *                    This is useful for embedder components that alters behavior depending on the receiver.
     * @param tensorType the type of the tensor to be returned
     * @return the tensor embedding of the text, as the spoecified tensor type
     * @throws IllegalArgumentException if the language or tensor type is not supported by this embedder
     */
    Tensor embed(String text, Language language, String destination, TensorType tensorType);

    class FailingEmbedder implements Embedder {

        @Override
        public List<Integer> embed(String text, Language language, String destination) {
            throw new IllegalStateException("No embedder has been configured");
        }

        @Override
        public Tensor embed(String text, Language language, String destination, TensorType tensorType) {
            throw new IllegalStateException("No embedder has been configured");
        }

    }

}
