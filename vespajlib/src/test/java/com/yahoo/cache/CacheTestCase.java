// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.cache;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collection;

public class CacheTestCase extends TestCase {

    public void testBasicGet() {
        Cache<String, String> cache = new Cache<>(100 * 1024 * 1024, 3600, 10000);
        String q = "/std_xmls_a00?hits=5&offset=5&query=flowers+shop&tracelevel=4&objid=ffffffffffffffff";
        String q2 = "/std_xmls_a00?hits=5&offset=5&query=flowers+shop&tracelevel=4&objid=ffffffffffffffff";
        String r = "result";
        String r2 = "result2";
        assertNull(cache.get(q));
        cache.put(q, r);
        assertNotNull(cache.get(q));
        assertEquals(cache.get(q), r);
        cache.put(q2, r);
        assertEquals(cache.get(q2), r);
        cache.put(q, r2);
        assertEquals(cache.get(q), r2);
    }

    public void testPutTooLarge() {
        byte[] tenMB = new byte[10*1024*1024];
        for (int i = 0 ; i <10*1024*1024 ; i++) {
                tenMB[i]=127;
        }
        byte[] sevenMB = new byte[7*1024*1024];
        for (int i = 0 ; i <7*1024*1024 ; i++) {
                sevenMB[i]=127;
        }
        Cache<String, byte[]> cache=new Cache<>(9*1024*1024,3600, 100*1024*1024); // 9 MB
        assertFalse(cache.put("foo", tenMB));
        assertTrue(cache.put("foo", sevenMB));
        assertEquals(cache.get("foo"), sevenMB);
    }

    public void testInvalidate() {
        byte[] tenMB = new byte[10*1024*1024];
        for (int i = 0 ; i <10*1024*1024 ; i++) {
                tenMB[i]=127;
        }
        byte[] sevenMB = new byte[7*1024*1024];
        for (int i = 0 ; i <7*1024*1024 ; i++) {
                sevenMB[i]=127;
        }
        //log.info("10 MB: "+calc.sizeOf(tenMB));
        //log.info("7 MB: "+calc.sizeOf(sevenMB));
        Cache<String, byte[]> cache=new Cache<>(11*1024*1024,3600, 100*1024*1024); // 11 MB
        assertTrue(cache.put("foo", sevenMB));
        assertTrue(cache.put("bar", tenMB));
        assertNull(cache.get("foo"));
        assertEquals(cache.get("bar"), tenMB);
    }

    public void testInvalidateLRU() {
        Cache<String, byte[]> cache=new Cache<>(10*1024*1024,3600, 100*1024*1024); // 10 MB
        byte[] fiveMB = new byte[5*1024*1024];
        for (int i = 0 ; i <5*1024*1024 ; i++) {
                fiveMB[i]=127;
        }

        byte[] twoMB = new byte[2*1024*1024];
        for (int i = 0 ; i <2*1024*1024 ; i++) {
                twoMB[i]=127;
        }

        byte[] fourMB = new byte[4*1024*1024];
        for (int i = 0 ; i <4*1024*1024 ; i++) {
                fourMB[i]=127;
        }
        assertTrue(cache.put("five", fiveMB));
        assertTrue(cache.put("two", twoMB));
        Object dummy = cache.get("five"); // Makes two LRU
        assertEquals(dummy, fiveMB);
        assertTrue(cache.put("four", fourMB));
        assertNull(cache.get("two"));
        assertEquals(cache.get("five"), fiveMB);
        assertEquals(cache.get("four"), fourMB);

        // Same, without the access, just to check
        cache=new Cache<>(10*1024*1024,3600, 100*1024*1024); // 10 MB
        assertTrue(cache.put("five", fiveMB));
        assertTrue(cache.put("two", twoMB));
        assertTrue(cache.put("four", fourMB));
        assertEquals(cache.get("two"), twoMB);
        assertNull(cache.get("five"));
        assertEquals(cache.get("four"), fourMB);
    }

    public void testPutSameKey() {
        Cache<String, byte[]> cache=new Cache<>(10*1024*1024,3600, 100*1024*1024); // 10 MB
        byte[] fiveMB = new byte[5*1024*1024];
        for (int i = 0 ; i <5*1024*1024 ; i++) {
            fiveMB[i]=127;
        }

        byte[] twoMB = new byte[2*1024*1024];
        for (int i = 0 ; i <2*1024*1024 ; i++) {
            twoMB[i]=127;
        }

        byte[] fourMB = new byte[4*1024*1024];
        for (int i = 0 ; i <4*1024*1024 ; i++) {
            fourMB[i]=127;
        }
        assertTrue(cache.put("five", fiveMB));
        assertTrue(cache.put("two", twoMB));
        assertEquals(cache.get("two"), twoMB);
        assertEquals(cache.get("five"), fiveMB);
        assertTrue(cache.put("five", twoMB));
        assertEquals(cache.get("five"), twoMB);
        assertEquals(cache.get("two"), twoMB);
    }

    public void testExpire() throws InterruptedException {
        Cache<String, String> cache=new Cache<>(10*1024*1024,400, 10000); // 10 MB, .4 sec expire
        cache.put("foo", "bar");
        cache.put("hey", "ho");
        assertEquals(cache.get("foo"), "bar");
        assertEquals(cache.get("hey"), "ho");
        Thread.sleep(600);
        assertNull(cache.get("foo"));
        assertNull(cache.get("hey"));
    }

    public void testInsertSame() {
        Cache<String, String> cache=new Cache<>(10*1024*1024,500, 10000); // 10 MB, .5 sec expire
        String k = "foo";
        String r = "bar";
        cache.put(k, r);
        assertEquals(cache.size(), 1);
        cache.put(k, r);
        assertEquals(cache.size(), 1);
    }

    public void testMaxSize() {
        Cache<String, byte[]> cache=new Cache<>(20*1024*1024,500, 3*1024*1024);
        byte[] fourMB = new byte[4*1024*1024];
        for (int i = 0 ; i <4*1024*1024 ; i++) {
            fourMB[i]=127;
        }
        byte[] twoMB = new byte[2*1024*1024];
        for (int i = 0 ; i <2*1024*1024 ; i++) {
            twoMB[i]=127;
        }
        assertFalse(cache.put("four", fourMB));
        assertTrue(cache.put("two", twoMB));
        assertNull(cache.get("four"));
        assertNotNull(cache.get("two"));
    }

    public void testMaxSizeNoLimit() {
        Cache<String, byte[]> cache=new Cache<>(20*1024*1024,500, -1);
        byte[] fourMB = new byte[4*1024*1024];
        for (int i = 0 ; i <4*1024*1024 ; i++) {
            fourMB[i]=127;
        }
        byte[] twoMB = new byte[2*1024*1024];
        for (int i = 0 ; i <2*1024*1024 ; i++) {
            twoMB[i]=127;
        }
        assertTrue(cache.put("four", fourMB));
        assertTrue(cache.put("two", twoMB));
        assertNotNull(cache.get("four"));
        assertNotNull(cache.get("two"));
    }

    public void testGetKeysAndValuesAndClear() {
        Cache<String, String> cache=new Cache<>(10*1024*1024,500, 10000); // 10 MB, .5 sec expire
        assertEquals(cache.getKeys().size(), 0);
        assertEquals(cache.getValues().size(), 0);
        cache.put("a", "b");
        cache.put("c", "d");
        cache.put("e", "f");
        Collection<String> keys = new ArrayList<>();
        keys.add("a");
        keys.add("c");
        keys.add("e");
        Collection<String> values = new ArrayList<>();
        values.add("b");
        values.add("d");
        values.add("f");
        assertEquals(cache.getKeys().size(), 3);
        assertEquals(cache.getValues().size(), 3);
        assertTrue(cache.getKeys().containsAll(keys));
        assertTrue(cache.getValues().containsAll(values));
        cache.clear();
        assertEquals(cache.getKeys().size(), 0);
        assertEquals(cache.getValues().size(), 0);
    }

}
