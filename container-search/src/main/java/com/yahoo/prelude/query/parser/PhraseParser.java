// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.search.query.parser.ParserEnvironment;

/**
 * Parser for queries of type phrase.
 *
 * @author Steinar Knutsen
 */
public class PhraseParser extends AbstractParser {

    public PhraseParser(ParserEnvironment environment) {
        super(environment);
    }

    @Override
    protected Item parseItems() {
        return forcedPhrase();
    }

    /**
     * Ignores everything but words and numbers
     *
     * @return a phrase item if several words/numbers was found, a word item if only one was found
     */
    private Item forcedPhrase() {
        Item firstWord = null;
        PhraseItem phrase = null;

        while (tokens.hasNext()) {
            Token token = tokens.next();

            if (token.kind != Token.Kind.WORD && token.kind != Token.Kind.NUMBER) {
                continue;
            }
            // Note, this depends on segment never creating AndItems when quoted
            // (the second argument) is true.
            Item newWord = segment(null, token, true);

            if (firstWord == null) { // First pass
                firstWord = newWord;
            } else if (phrase == null) { // Second pass
                phrase = new PhraseItem();
                phrase.addItem(firstWord);
                phrase.addItem(newWord);
            } else { // Following passes
                phrase.addItem(newWord);
            }
        }
        if (phrase != null) {
            return phrase;
        } else {
            return firstWord;
        }
    }

}
