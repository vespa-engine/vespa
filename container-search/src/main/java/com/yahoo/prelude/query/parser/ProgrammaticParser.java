// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.parser;

import com.yahoo.language.Language;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.textserialize.TextSerialize;

/**
 * @author Simon Thoresen Hult
 */
public final class ProgrammaticParser implements CustomParser {

    @Override
    public QueryTree parse(Parsable query) {
        Item root = parse(query.getQuery(), null, null, null, null, null);
        if (root == null) {
            root = new NullItem();
        }
        return new QueryTree(root);

    }

    @Override
    public Item parse(String queryToParse, String filterToParse, Language parsingLanguage,
                      IndexFacts.Session indexFacts, String defaultIndexName) {
        if (queryToParse == null)  return null;
        return TextSerialize.parse(queryToParse);
    }

}
