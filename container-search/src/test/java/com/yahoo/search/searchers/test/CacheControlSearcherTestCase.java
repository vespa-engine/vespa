// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers.test;

import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchers.CacheControlSearcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yahoo.search.searchers.CacheControlSearcher.CACHE_CONTROL_HEADER;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cases for CacheControlSearcher.
 *
 * @author frodelu
 */
public class CacheControlSearcherTestCase {

    private Searcher getDocSource() {
        return new Searcher() {
            public Result search(Query query, Execution execution) {
                Result res = new Result(query);
                res.setTotalHitCount(1);
                Hit hit = new Hit("http://document/", 1000);
                hit.setField("url", "http://document/");
                hit.setField("title", "Article title");
                hit.setField("extsourceid", "12345");
                res.hits().add(hit);
                return res;
            }
        };
    }

    private Chain<Searcher> getSearchChain() {
        return new Chain<>(new CacheControlSearcher(), getDocSource());
    }

    private List<String> getCacheControlHeaders(Result result) {
        return result.getHeaders(true).get(CACHE_CONTROL_HEADER);
    }

    /**
     * Assert that cache header ListMap exactly match given array of expected cache headers
     * @param values - Array of cache control headers expected, e.g. {"max-age=120", "stale-while-revalidate=3600"}
     * @param cacheheaders - The "Cache-Control" headers from the response ListMap
     */
    private void assertCacheHeaders(String[] values, List<String> cacheheaders) {
        assertNotNull(values, "No headers to test for (was null)");
        assertTrue(values.length > 0, "No headers to test for (no elements in array)");
        assertNotNull(cacheheaders, "No cache headers set in response");
        assertEquals(values.length, cacheheaders.size());
        for (String header : values) {
            assertTrue(cacheheaders.contains(header), "Cache header does not contain header '" + header + "'");
        }
    }

    @Test
    void testNoHeader() {
        Chain<Searcher> chain = getSearchChain();
        Query query = new Query("?query=foo&custid=foo");
        Result result = new Execution(chain, Execution.Context.createContextStub()).search(query);
        assertEquals(0, getCacheControlHeaders(result).size());
    }

    @Test
    void testInvalidAgeParams() {
        Chain<Searcher> chain = getSearchChain();

        try {
            Query query = new Query("?query=foo&custid=foo&cachecontrol.maxage=foo");
            Result result = new Execution(chain, Execution.Context.createContextStub()).search(query);
            assertEquals(0, getCacheControlHeaders(result).size());
            fail("Expected exception");
        }
        catch (NumberFormatException e) {
            // success
        }

        try {
            Query query = new Query("?query=foo&custid=foo&cachecontrol.staleage=foo");
            Result result = new Execution(chain, Execution.Context.createContextStub()).search(query);
            assertEquals(0, getCacheControlHeaders(result).size());
            fail("Expected exception");
        }
        catch (NumberFormatException e) {
            // success
        }
    }

    @Test
    void testMaxAge() {
        Chain<Searcher> chain = getSearchChain();

        Query query = new Query("?query=foo&custid=foo&cachecontrol.maxage=120");
        Result result = new Execution(chain, Execution.Context.createContextStub()).search(query);
        assertCacheHeaders(new String[]{"max-age=120"}, getCacheControlHeaders(result));
    }

    @Test
    void testNoCache() {
        Chain<Searcher> chain = getSearchChain();

        Query query = new Query("?query=foo&custid=foo&cachecontrol.maxage=120&noCache");
        Result result = new Execution(chain, Execution.Context.createContextStub()).search(query);
        assertCacheHeaders(new String[]{"no-cache"}, getCacheControlHeaders(result));

        query = new Query("?query=foo&custid=foo&cachecontrol.maxage=120&cachecontrol.nocache=true");
        result = new Execution(chain, Execution.Context.createContextStub()).search(query);
        assertCacheHeaders(new String[]{"no-cache"}, getCacheControlHeaders(result));
    }

    @Test
    void testStateWhileRevalidate() {
        Chain<Searcher> chain = getSearchChain();

        Query query = new Query("?query=foo&custid=foo&cachecontrol.staleage=3600");
        Result result = new Execution(chain, Execution.Context.createContextStub()).search(query);
        assertCacheHeaders(new String[]{"stale-while-revalidate=3600"}, getCacheControlHeaders(result));
    }

    @Test
    void testStaleAndMaxAge() {
        Chain<Searcher> chain = getSearchChain();

        Query query = new Query("?query=foo&custid=foo&cachecontrol.maxage=60&cachecontrol.staleage=3600");
        Result result = new Execution(chain, Execution.Context.createContextStub()).search(query);
        assertCacheHeaders(new String[]{"max-age=60", "stale-while-revalidate=3600"}, getCacheControlHeaders(result));
    }

}
