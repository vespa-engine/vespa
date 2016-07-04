// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.searcher.test;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.searcher.QrQuotetableConfig;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.Relevance;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.hitfield.HitField;
import com.yahoo.search.Searcher;
import com.yahoo.prelude.searcher.DocumentSourceSearcher;
import com.yahoo.prelude.searcher.QuotingSearcher;
import com.yahoo.search.searchchain.Execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests hit property quoting.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
@SuppressWarnings("deprecation")
public class QuotingSearcherTestCase extends junit.framework.TestCase {

    public QuotingSearcherTestCase (String name) {
        super(name);
    }

    public static QuotingSearcher createQuotingSearcher(String configId) {
        QrQuotetableConfig config = new ConfigGetter<>(QrQuotetableConfig.class).getConfig(configId);
        return new QuotingSearcher(new ComponentId("QuotingSearcher"), config);
    }

    public void testBasicQuoting() {
        Map<Searcher, Searcher> chained = new HashMap<>();
        Searcher s = createQuotingSearcher("file:src/test/java/com/yahoo/prelude/"
                                                + "searcher/test/testquoting.cfg");
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(s, docsource);
        Query q = new Query("?query=a");
        Result r = new Result(q);
        Hit hit = new FastHit();
        hit.setId("http://abc.html");
        hit.setRelevance(new Relevance(1));
        hit.setField("title", "smith & jones");
        r.hits().add(hit);
        docsource.addResultSet(q, r);
        Result check = doSearch(s, q, 0, 10, chained);
        assertEquals("smith &amp; jones", check.hits().get(0).getField("title").toString());
        assertTrue(check.hits().get(0).fields().containsKey("title"));
    }

    public void testBasicQuotingWithNoisyStrings() {
        Map<Searcher, Searcher> chained = new HashMap<>();
        Searcher s = createQuotingSearcher("file:src/test/java/com/yahoo/prelude/"
                                                + "searcher/test/testquoting.cfg");
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(s, docsource);
        Query q = new Query("?query=a");
        Result r = new Result(q);
        Hit hit = new FastHit();
        hit.setId("http://abc.html");
        hit.setRelevance(new Relevance(1));
        hit.setField("title", "&smith &jo& nes");
        r.hits().add(hit);
        docsource.addResultSet(q, r);
        Result check = doSearch(s, q, 0, 10, chained);
        assertEquals("&amp;smith &amp;jo&amp; nes", check.hits().get(0).getField("title").toString());
        assertTrue(check.hits().get(0).fields().containsKey("title"));
    }

    public void testFieldQuotingWithNoisyStrings() {
        Map<Searcher, Searcher> chained = new HashMap<>();
        Searcher s = createQuotingSearcher("file:src/test/java/com/yahoo/prelude/"
                                                + "searcher/test/testquoting.cfg");
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(s, docsource);
        Query q = new Query("?query=a");
        Result r = new Result(q);
        Hit hit = new FastHit();
        hit.setId("http://abc.html");
        hit.setRelevance(new Relevance(1));
        hit.setField("title", new HitField("title", "&smith &jo& nes"));
        r.hits().add(hit);
        docsource.addResultSet(q, r);
        Result check = doSearch(s, q, 0, 10, chained);
        assertEquals("&amp;smith &amp;jo&amp; nes", check.hits().get(0).getField("title").toString());
        assertTrue(check.hits().get(0).fields().containsKey("title"));
    }


    public void testNoQuotingWithOtherTypes() {
        Map<Searcher, Searcher> chained = new HashMap<>();
        Searcher s = createQuotingSearcher("file:src/test/java/com/yahoo/prelude/"
                                                + "searcher/test/testquoting.cfg");
        DocumentSourceSearcher docsource = new DocumentSourceSearcher();
        chained.put(s, docsource);
        Query q = new Query("?query=a");
        Result r = new Result(q);
        Hit hit = new FastHit();
        hit.setId("http://abc.html");
        hit.setRelevance(new Relevance(1));
        hit.setField("title", new Integer(42));
        r.hits().add(hit);
        docsource.addResultSet(q, r);
        Result check = doSearch(s, q, 0, 10, chained);
        // should not quote non-string properties
        assertEquals(new Integer(42), check.hits().get(0).getField("title"));
    }

    private Result doSearch(Searcher searcher, Query query, int offset, int hits, Map<Searcher, Searcher> chained) {
        query.setOffset(offset);
        query.setHits(hits);
        return createExecution(searcher, chained).search(query);
    }

    private Chain<Searcher> chainedAsSearchChain(Searcher topOfChain, Map<Searcher, Searcher> chained) {
        List<Searcher> searchers = new ArrayList<>();
        for (Searcher current = topOfChain; current != null; current = chained.get(current)) {
            searchers.add(current);
        }
        return new Chain<>(searchers);
    }

    private Execution createExecution(Searcher searcher, Map<Searcher, Searcher> chained) {
        Execution.Context context = new Execution.Context(null, null, null, new RendererRegistry(MoreExecutors.directExecutor()), new SimpleLinguistics());
        return new Execution(chainedAsSearchChain(searcher, chained), context);
    }

}
