// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.language.Language;
import com.yahoo.language.process.LinguisticsParameters;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.TokenType;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.TermItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;

import static com.yahoo.prelude.query.parser.Token.Kind.NUMBER;
import static com.yahoo.prelude.query.parser.Token.Kind.WORD;

/**
 * A parser which delegates all tokenization and processing to the linguistics component.
 * The full string is given as-is to the linguistics component for tokenization, and
 * what comes back is assumes fully processed including stemming (if applicable).
 * The returned tokens are collected into a single parent item.
 *
 * @author bratseth
 */
public final class LinguisticsParser extends AbstractParser {

    public LinguisticsParser(ParserEnvironment environment) {
        super(environment);
    }

    @Override
    Item parse(String queryToParse, String filterToParse, Language parsingLanguage,
               IndexFacts.Session indexFacts, String defaultIndex, Parsable parsable) {
        var parameters = new LinguisticsParameters(parsingLanguage,
                                                   StemMode.BEST,
                                                   true,
                                                   true);
        var parent = newComposite();
        for (com.yahoo.language.process.Token token : environment.getLinguistics().getTokenizer().tokenize(queryToParse, parameters)) {
            if ( ! token.getType().isIndexable()) continue;
            parent.addItem(toItem(token, defaultIndex));
        }
        return parent;
    }

    @Override
    protected Item parseItems() {
        throw new RuntimeException(); // Not used since this overrides the parse method to delegate tokenization
    }

    private Item toItem(com.yahoo.language.process.Token token, String defaultIndex) {
        TermItem item;
        if (token.getType() == TokenType.NUMERIC) {
            item = new IntItem(token.getTokenString());
        }
        else {
            WordItem word = new WordItem(token.getTokenString());
            word.setStemmed(true); // Disable downstream stemming
            word.setNormalizable(false); // Disable downstream normaliing
            item = word;
        }
        item.setIndexName(defaultIndex);
        return item;
    }

}
