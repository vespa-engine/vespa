// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.test;

import static com.yahoo.search.searchchain.test.SimpleSearchChain.searchChain;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import com.yahoo.component.ComponentId;
import com.yahoo.component.Version;
import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.SearchChain;
import org.junit.jupiter.api.Test;

/**
 * Tests basic search chain functionality - creation, inheritance and ordering
 *
 * @author bratseth
 */
public class SearchChainTestCase {

    @Test
    void testEmptySearchChain() {
        SearchChain empty = new SearchChain(new ComponentId("empty"));
        assertEquals("empty", empty.getId().getName());
    }

    @Test
    void testSearchChainCreation() {
        assertEquals("test", searchChain.getId().stringValue());
        assertEquals("test", searchChain.getId().getName());
        assertEquals(Version.emptyVersion, searchChain.getId().getVersion());
        assertEquals(new Version(), searchChain.getId().getVersion());
        assertEqualMembers(Arrays.asList("one", "two"), searcherNames(searchChain.searchers()));
    }

    public List<String> searcherNames(Collection<Searcher> searchers) {
        List<String> names = new ArrayList<>();

        for (Searcher searcher: searchers) {
            names.add(searcher.getId().stringValue());
        }

        Collections.sort(names);
        return names;
    }

    private void assertEqualMembers(List<String> correct,List<?> test) {
        assertEquals(new HashSet<>(correct),new HashSet<>(test));
    }

    @Test
    void testSearchChainToStringEmpty() {
        assertEquals("chain 'test' []", new Chain<>(new ComponentId("test"), createSearchers(0)).toString());
    }

    @Test
    void testSearchChainToStringVeryShort() {
        assertEquals("chain 'test' [s1]", new Chain<>(new ComponentId("test"), createSearchers(1)).toString());
    }

    @Test
    void testSearchChainToStringShort() {
        assertEquals("chain 'test' [s1 -> s2 -> s3]", new Chain<>(new ComponentId("test"), createSearchers(3)).toString());
    }

    @Test
    void testSearchChainToStringLong() {
        assertEquals("chain 'test' [s1 -> s2 -> ... -> s4]", new Chain<>(new ComponentId("test"), createSearchers(4)).toString());
    }

    @Test
    void testSearchChainToStringVeryLong() {
        assertEquals("chain 'test' [s1 -> s2 -> ... -> s10]", new Chain<>(new ComponentId("test"), createSearchers(10)).toString());
    }

    private List<Searcher> createSearchers(int count) {
        List<Searcher> searchers=new ArrayList<>(count);
        for (int i=0; i<count; i++)
            searchers.add(new TestSearcher("s" + (i+1)));
        return searchers;
    }

    private static class TestSearcher extends Searcher {

        private TestSearcher(String id) {
            super(new ComponentId(id));
        }

        public Result search(Query query, Execution execution) {
            return execution.search(query);
        }

    }

}
