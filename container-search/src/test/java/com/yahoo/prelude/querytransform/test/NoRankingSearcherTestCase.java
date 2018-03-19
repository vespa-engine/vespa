// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.querytransform.test;

import com.yahoo.search.Query;
import com.yahoo.prelude.querytransform.NoRankingSearcher;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NoRankingSearcherTestCase {

    Searcher s = new NoRankingSearcher();

    @Test
    public void testDoSearch() {
        Query q = new Query("?query=a&sorting=%2ba%20-b&ranking=hello");
        assertEquals("hello", q.getRanking().getProfile());
        new Execution(s, Execution.Context.createContextStub()).search(q);
        assertEquals("unranked", q.getRanking().getProfile());
     }

    @Test
    public void testSortOnRelevanceAscending() {
        Query q = new Query("?query=a&sorting=%2ba%20-b%20-[rank]&ranking=hello");
        new Execution(s, Execution.Context.createContextStub()).search(q);
        assertEquals("hello", q.getRanking().getProfile());
     }

    @Test
    public void testSortOnRelevanceDescending() {
        Query q = new Query("?query=a&sorting=%2ba%20-b%20-[rank]&ranking=hello");
        new Execution(s, Execution.Context.createContextStub()).search(q);
        assertEquals("hello", q.getRanking().getProfile());
     }

    @Test
    public void testNoSorting() {
        Query q = new Query("?query=a");
        new Execution(s, Execution.Context.createContextStub()).search(q);
        assertEquals("default", q.getRanking().getProfile());
     }

}
