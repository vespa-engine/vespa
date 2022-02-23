// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.prelude.query.IntItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.search.query.parser.ParserEnvironment;

import static com.yahoo.prelude.query.parser.Token.Kind.NUMBER;
import static com.yahoo.prelude.query.parser.Token.Kind.WORD;

/**
 * A parser which turns contiguous searchable character into tokens and filters out other characters.
 * The resulting tokens are collected into a WeakAnd item.
 *
 * @author bratseth
 */
public final class TokenizeParser extends AbstractParser {

    public TokenizeParser(ParserEnvironment environment) {
        super(environment);
    }

    @Override
    protected Item parseItems() {
        WeakAndItem weakAnd = new WeakAndItem();
        Token token;
        while (null != (token = tokens.next())) {
            Item termItem = toTerm(token);
            if (termItem != null)
                weakAnd.addItem(termItem);
        }
        return weakAnd;
    }

    /** Returns the item representing this token if it is searchable, and null otherwise */
    private Item toTerm(Token token) {
        if (token.kind == WORD)
            return segment("", token, false);
        else if (token.kind == NUMBER)
            return new IntItem(token.image);
        else
            return null;
    }

}
