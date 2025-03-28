// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import com.yahoo.language.Language;

/**
 * Language-sensitive tokenization of a text string.
 *
 * @author Mathias Mølster Lidal
 */
public interface Tokenizer {

    /**
     * Tokenizes the given input string.
     *
     * @param input the string to tokenize. May be arbitrarily large.
     * @param language the language of the input string.
     * @param stemMode the stem mode applied on the returned tokens
     * @param removeAccents whether to normalize accents and similar
     * @return the tokens of the input String
     * @throws ProcessingException If the underlying library throws an Exception.
     * @deprecated use #tokenize(String, LinguisticsParameters)
     */
    @Deprecated // TODO: Remove on Vespa 9
    default Iterable<Token> tokenize(String input, Language language, StemMode stemMode, boolean removeAccents) {
        return tokenize(input, new LinguisticsParameters(language, stemMode, removeAccents, true));
    }

    /**
     * Tokenizes the given input string.
     * This default implementation calls tokenize(input, language, stemMode, removeAccents)
     */
     default Iterable<Token> tokenize(String input, LinguisticsParameters parameters) {
        return tokenize(input, parameters.language(), parameters.stemMode(), parameters.removeAccents());
    }

}
