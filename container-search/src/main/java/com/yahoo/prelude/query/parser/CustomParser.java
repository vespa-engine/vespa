// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.language.Language;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.Item;
import com.yahoo.search.query.parser.Parser;

import java.util.Collections;
import java.util.Set;

/**
 * @author Simon Thoresen Hult
 */
public interface CustomParser extends Parser {

    /**
     * Returns the raw result from parsing, <i>not</i> wrapped in a QueryTree
     * instance. This may also be null, as opposed to using
     * {@link Parser#parse(com.yahoo.search.query.parser.Parsable)}.
     */
    default Item parse(String queryToParse, String filterToParse, Language parsingLanguage,
                       Set<String> toSearch, IndexFacts indexFacts, String defaultIndexName) {
        if (indexFacts == null)
            indexFacts = new IndexFacts();
        return parse(queryToParse, filterToParse, parsingLanguage, indexFacts.newSession(toSearch, Collections.emptySet()), defaultIndexName);
    }

    Item parse(String queryToParse, String filterToParse, Language parsingLanguage,
               IndexFacts.Session indexFacts, String defaultIndexName);

}
