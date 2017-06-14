// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexFactsFactory;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.parser.TestLinguistics;
import com.yahoo.prelude.querytransform.CJKSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.Parser;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.query.parser.ParserFactory;
import com.yahoo.search.searchchain.Execution;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class CJKSearcherTestCase {

    private final IndexFacts indexFacts = IndexFactsFactory.newInstance("file:src/test/java/com/yahoo/prelude/" +
                                                                        "querytransform/test/cjk-index-info.cfg", null);

    @Test
    public void testTermWeight() {
        assertTransformed("efg!10", "SAND e!10 fg!10",
                          Query.Type.ALL, Language.CHINESE_SIMPLIFIED, Language.CHINESE_TRADITIONAL, TestLinguistics.INSTANCE);
    }

    /**
     * Overlapping tokens splits some sequences of "bcd" into "bc" "cd" instead of e.g. "b",
     * "cd". This improves recall in some cases. Vespa
     * must combine overlapping tokens as PHRASE, not AND to avoid a too high recall because of the token overlap.
     */
    @Test
    public void testCjkQueryWithOverlappingTokens() {
        // The test language segmenter will segment "bcd" into the overlapping tokens "bc" "cd"
        assertTransformed("bcd", "'bc cd'", Query.Type.ALL, Language.CHINESE_SIMPLIFIED, Language.CHINESE_TRADITIONAL,
                          TestLinguistics.INSTANCE);

        // While "efg" will be segmented into one of the standard options, "e" "fg"
        assertTransformed("efg", "SAND e fg", Query.Type.ALL, Language.CHINESE_SIMPLIFIED, Language.CHINESE_TRADITIONAL,
                          TestLinguistics.INSTANCE);
    }

    private void assertTransformed(String queryString, String expected, Query.Type mode, Language actualLanguage,
                                   Language queryLanguage, Linguistics linguistics) {
        Parser parser = ParserFactory.newInstance(mode, new ParserEnvironment()
                .setIndexFacts(indexFacts)
                .setLinguistics(linguistics));
        Item root = parser.parse(new Parsable().setQuery(queryString).setLanguage(actualLanguage)).getRoot();
        assertFalse(root instanceof NullItem);

        Query query = new Query("?language=" + queryLanguage.languageCode());
        query.getModel().getQueryTree().setRoot(root);

        new Execution(new Chain<Searcher>(new CJKSearcher()),
                      new Execution.Context(null, indexFacts, null, null, linguistics)).search(query);
        assertEquals(expected, query.getModel().getQueryTree().getRoot().toString());
    }

}
