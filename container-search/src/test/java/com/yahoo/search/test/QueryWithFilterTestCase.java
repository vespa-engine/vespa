// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.test;

import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.SearchDefinition;
import com.yahoo.search.Query;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class QueryWithFilterTestCase {

    /** Tests that default-index is not applied to ALL filters */
    @Test
    void testRankFilter() {
        Query q = newQueryFromEncoded("?query=trump" +
                                      "&model.type=all" +
                                      "&model.defaultIndex=text" +
                                      "&filter=filterattribute%3Afrontpage_US_en-US");
        assertEquals("RANK text:trump |filterattribute:frontpage_US_en-US",
                     q.getModel().getQueryTree().toString());
    }

    /** Tests that default-index is not applied to NOT filters */
    @Test
    void testAndFilter() {
        Query q = newQueryFromEncoded("?query=trump" +
                                      "&model.type=all" +
                                      "&model.defaultIndex=text" +
                                      "&filter=%2B%28filterattribute%3Afrontpage_US_en-US%29");
        assertEquals("AND text:trump |filterattribute:frontpage_US_en-US",
                     q.getModel().getQueryTree().toString());
    }

    /** Tests that default-index is not applied to NOT filters */
    @Test
    void testAndFilterWithoutExplicitIndex() {
        Query q = newQueryFromEncoded("?query=trump" +
                                      "&model.type=all" +
                                      "&model.defaultIndex=text" +
                                      "&filter=%2B%28filterTerm%29");
        assertEquals("AND text:trump |text:filterTerm",
                     q.getModel().getQueryTree().toString());
    }

    private Query newQueryFromEncoded(String queryString) {
        return newQueryFromEncoded(queryString, null, new SimpleLinguistics());
    }

    private Query newQueryFromEncoded(String encodedQueryString, Language language, Linguistics linguistics) {
        Query query = new Query(encodedQueryString);
        query.getModel().setExecution(new Execution(Execution.Context.createContextStub(createIndexFacts(),
                                                                                        linguistics)));
        query.getModel().setLanguage(language);
        return query;
    }

    private IndexFacts createIndexFacts() {
        SearchDefinition sd = new SearchDefinition("test");
        sd.addIndex(new Index("test"));
        sd.addIndex(attribute("filterattribute"));
        return new IndexFacts(new IndexModel(sd));
    }

    private Index attribute(String name) {
        Index index = new Index(name);
        index.setExact(true, null);
        return index;
    }

}
