// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.cache.test;

import com.yahoo.search.result.Hit;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.statistics.Statistics;
import com.yahoo.prelude.cache.Cache;
import com.yahoo.prelude.cache.QueryCacheKey;
import org.junit.Test;

import static org.junit.Assert.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class CacheTestCase {

    private Result getSomeResult(Query q, String id) {
        Result r = new Result(q);
        r.hits().add(new Hit(id, 10));
        return r;
    }

    @Test
    public void testBasicGet() {
        Cache<QueryCacheKey, Result> cache=new Cache<>(100*1024,3600, 100000, Statistics.nullImplementation);
        Query q = new Query("/std_xmls_a00?hits=5&offset=5&query=flowers+shop&tracelevel=4&objid=ffffffffffffffff");
        Query q2 = new Query("/std_xmls_a00?hits=5&offset=5&query=flowers+shop&tracelevel=4&objid=ffffffffffffffff");
        QueryCacheKey qk = new QueryCacheKey(q);
        QueryCacheKey qk2 = new QueryCacheKey(q2);
        Result r = getSomeResult(q, "foo");
        Result r2 = getSomeResult(q, "bar");
        assertNull(cache.get(qk));
        cache.put(qk, r);
        assertNotNull(cache.get(qk));
        assertEquals(cache.get(qk), r);
        cache.put(qk2, r);
        assertEquals(cache.get(qk2), r);
        cache.put(qk, r2);
        assertEquals(cache.get(qk), r2);
    }

    @Test
    public void testPutTooLarge() {
        byte[] tenKB = new byte[10*1024];
        for (int i = 0 ; i <10*1024 ; i++) {
                tenKB[i]=127;
        }
        byte[] sevenKB = new byte[7*1024];
        for (int i = 0 ; i <7*1024 ; i++) {
                sevenKB[i]=127;
        }
        Cache cache=new Cache(9*1024,3600, 100*1024, Statistics.nullImplementation); // 9 KB
        assertFalse(cache.put("foo", tenKB));
        assertTrue(cache.put("foo", sevenKB));
        assertEquals(cache.get("foo"), sevenKB);
    }

    @Test
    public void testInvalidate() {
        byte[] tenKB = new byte[10*1024];
        for (int i = 0 ; i <10*1024 ; i++) {
                tenKB[i]=127;
        }
        byte[] sevenKB = new byte[7*1024];
        for (int i = 0 ; i <7*1024 ; i++) {
                sevenKB[i]=127;
        }
        Cache cache=new Cache(11*1024,3600, 100*1024, Statistics.nullImplementation); // 11 KB
        assertTrue(cache.put("foo", sevenKB));
        assertTrue(cache.put("bar", tenKB));
        assertNull(cache.get("foo"));
        assertEquals(cache.get("bar"), tenKB);
    }

    @Test
    public void testInvalidateLRU() {
        Cache cache=new Cache(10*1024,3600, 100*1024, Statistics.nullImplementation); // 10 MB
        byte[] fiveKB = new byte[5*1024];
        for (int i = 0 ; i <5*1024 ; i++) {
                fiveKB[i]=127;
        }

        byte[] twoKB = new byte[2*1024];
        for (int i = 0 ; i <2*1024 ; i++) {
                twoKB[i]=127;
        }

        byte[] fourKB = new byte[4*1024];
        for (int i = 0 ; i <4*1024 ; i++) {
                fourKB[i]=127;
        }
        assertTrue(cache.put("five", fiveKB));
        assertTrue(cache.put("two", twoKB));
        Object dummy = cache.get("five"); // Makes two LRU
        assertEquals(dummy, fiveKB);
        assertTrue(cache.put("four", fourKB));
        assertNull(cache.get("two"));
        assertEquals(cache.get("five"), fiveKB);
        assertEquals(cache.get("four"), fourKB);

        // Same, without the access, just to check
        cache=new Cache(10*1024,3600, 100*1024, Statistics.nullImplementation); // 10 KB
        assertTrue(cache.put("five", fiveKB));
        assertTrue(cache.put("two", twoKB));
        assertTrue(cache.put("four", fourKB));
        assertEquals(cache.get("two"), twoKB);
        assertNull(cache.get("five"));
        assertEquals(cache.get("four"), fourKB);
    }

    @Test
    public void testPutSameKey() {
        Cache cache=new Cache(10*1024,3600, 100*1024, Statistics.nullImplementation); // 10 MB
        byte[] fiveKB = new byte[5*1024];
        for (int i = 0 ; i <5*1024 ; i++) {
            fiveKB[i]=127;
        }

        byte[] twoKB = new byte[2*1024];
        for (int i = 0 ; i <2*1024 ; i++) {
            twoKB[i]=127;
        }

        byte[] fourKB = new byte[4*1024];
        for (int i = 0 ; i <4*1024 ; i++) {
            fourKB[i]=127;
        }
        assertTrue(cache.put("five", fiveKB));
        assertTrue(cache.put("two", twoKB));
        assertEquals(cache.get("two"), twoKB);
        assertEquals(cache.get("five"), fiveKB);
        assertTrue(cache.put("five", twoKB));
        assertEquals(cache.get("five"), twoKB);
        assertEquals(cache.get("two"), twoKB);
    }

    @Test
    public void testExpire() throws InterruptedException {
        Cache cache=new Cache(10*1024,50, 10000, Statistics.nullImplementation); // 10 KB, 50ms expire
        boolean success = false;
        for (int tries = 0; tries < 10; tries++) {
            long before = System.currentTimeMillis();
            cache.put("foo", "bar");
            cache.put("hey", "ho");
            Object got1 = cache.get("foo");
            Object got2 = cache.get("hey");
            long after = System.currentTimeMillis();
            if (after - before < 50) {
                assertEquals(got1, "bar");
                assertEquals(got2, "ho");
                success = true;
                break;
            }
        }
        assertTrue(success);
        Thread.sleep(100);
        assertNull(cache.get("foo"));
        assertNull(cache.get("hey"));
    }

    @Test
    public void testInsertSame() {
        Cache cache=new Cache(100*1024,500, 100000, Statistics.nullImplementation); // 100 KB, .5 sec expire
        Query q =  new Query("/std_xmls_a00?hits=5&offset=5&query=flowers+shop&tracelevel=4&objid=ffffffffffffffff");
        Result r = getSomeResult(q, "foo");
        QueryCacheKey k = new QueryCacheKey(q);
        cache.put(k, r);
        assertEquals(1, cache.size());
        q =  new Query("/std_xmls_a00?hits=5&offset=5&query=flowers+shop&tracelevel=4&objid=ffffffffffffffff");
        k = new QueryCacheKey(q);
        cache.put(k, r);
        assertEquals(1, cache.size());
    }

    @Test
    public void testMaxSize() {
        Cache cache=new Cache(20*1024,500, 3*1024, Statistics.nullImplementation);
        byte[] fourKB = new byte[4*1024];
        for (int i = 0 ; i <4*1024 ; i++) {
            fourKB[i]=127;
        }
        byte[] twoKB = new byte[2*1024];
        for (int i = 0 ; i <2*1024 ; i++) {
            twoKB[i]=127;
        }
        assertFalse(cache.put("four", fourKB));
        assertTrue(cache.put("two", twoKB));
        assertNull(cache.get("four"));
        assertNotNull(cache.get("two"));
    }

}
