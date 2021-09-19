// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.Language;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.List;

/**
 * An encoder converts a text string to a tensor or list of tokens
 *
 * @author bratseth
 */
public interface Encoder {

    /** An instance of this which throws IllegalStateException if attempted used */
    Encoder throwsOnUse = new FailingEncoder();

    /**
     * Encodes text into tokens in a list of ids.
     *
     * @param text the text to encode
     * @param language the language of the text, or UNKNOWN to use language independent encoding
     * @return the text encoded to a list of segment ids
     * @throws IllegalArgumentException if the language is not supported by this encoder
     */
    List<Integer> encode(String text, Language language);

    /**
     * Encodes text into tokens in a tensor.
     * The information contained in the encoding may depend on the tensor type.
     *
     * @param text the text to encode
     * @param language the language of the text, or UNKNOWN to use language independent encoding
     * @param tensorType the type of the ttensor to be returned
     * @return the tex encoded into a tensor of the supplied type
     * @throws IllegalArgumentException if the language or tensor type is not supported by this encoder
     */
    Tensor encode(String text, Language language, TensorType tensorType);

    class FailingEncoder implements Encoder {

        @Override
        public List<Integer> encode(String text, Language language) {
            throw new IllegalStateException("No encoder has been configured");
        }

        @Override
        public Tensor encode(String text, Language language, TensorType tensorType) {
            throw new IllegalStateException("No encoder has been configured");
        }

    }

}
