// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test;

import com.yahoo.fs4.BasicPacket;
import com.yahoo.fs4.BufferTooSmallException;
import com.yahoo.fs4.PacketDecoder;
import com.yahoo.fs4.QueryPacket;
import com.yahoo.search.Query;
import com.yahoo.prelude.fastsearch.CacheKey;
import com.yahoo.prelude.fastsearch.PacketCache;
import com.yahoo.prelude.fastsearch.PacketWrapper;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Tests the packet cache. Also tested in FastSearcherTestCase.
 *
 * @author  bratseth
 */
public class PacketCacheTestCase {

    static byte[] queryResultPacketData = new byte[] {
        0, 0, 0, 104,
        0, 0, 0,217 - 256,
        0, 0, 0, 1,
        0, 0, 0, 0,
        0, 0, 0, 2,
        0, 0, 0, 0,
        0, 0, 0, 5,
        0x40,0x39,0,0,0, 0, 0, 25,
        0, 0, 0, 111,
        0, 0, 0, 97,
        0,0,0,3, 1,1,1,1,1,1,1,1,1,1,1,1, 0x40,0x37,0,0,0,0,0,0, 0,0,0,7, 0,0,0,36,
        0,0,0,4, 2,2,2,2,2,2,2,2,2,2,2,2, 0x40,0x35,0,0,0,0,0,0, 0,0,0,8, 0,0,0,37};
    static int length = queryResultPacketData.length; // 4 + 68 + 2*12 bytes

    static CacheKey key1 = new CacheKey(QueryPacket.create(new Query("/?query=key1")));
    static CacheKey key2 = new CacheKey(QueryPacket.create(new Query("/?query=key2")));
    static CacheKey key3 = new CacheKey(QueryPacket.create(new Query("/?query=key3")));
    static CacheKey key4 = new CacheKey(QueryPacket.create(new Query("/?query=key4")));

    @Test
    public void testPutAndGet() throws BufferTooSmallException {
        PacketCache cache = new PacketCache(0, (length + 30) * 3 - 1, 1e64);

        cache.setMaxCacheItemPercentage(50);

        final int keysz = 36;

        // first control assumptions
        assertEquals(keysz, key1.byteSize());
        assertEquals(keysz, key2.byteSize());
        assertEquals(keysz, key3.byteSize());

        cache.put(key1, createCacheEntry(key1));
        assertNotNull(cache.get(key1));
        assertEquals(keysz + length, cache.totalPacketSize());

        cache.put(key2, createCacheEntry(key2));
        assertNotNull(cache.get(key1));
        assertNotNull(cache.get(key2));
        assertEquals(keysz*2 + length*2, cache.totalPacketSize());

        cache.put(key1, createCacheEntry(key1));
        assertNotNull(cache.get(key1));
        assertNotNull(cache.get(key2));
        assertEquals(keysz*2 + length*2, cache.totalPacketSize());

        // This should cause key1 (the eldest accessed) to be removed, as 3 is 1 2 many
        cache.put(key3, createCacheEntry(key3));
        assertEquals(keysz*2 + length*2, cache.totalPacketSize());
        assertNull(cache.get(key1));
        assertNotNull(cache.get(key2));
        assertNotNull(cache.get(key3));
        assertEquals(keysz*2 + length*2, cache.totalPacketSize());
    }

    // more control that delete code does not change internal access order
    @Test
    public void testInternalOrdering() throws BufferTooSmallException {
        // room for three entries
        PacketCache cache = new PacketCache(0, length * 4 - 1, 1e64);
        cache.setMaxCacheItemPercentage(50);

        cache.put(key1, createCacheEntry());
        cache.put(key2, createCacheEntry());
        cache.put(key3, createCacheEntry());
        cache.put(key4, createCacheEntry());

        assertNull(cache.get(key1));
        assertEquals(3, cache.size());
        cache.get(key2);
        cache.put(key1, createCacheEntry());
        assertNull(cache.get(key3));
        assertNotNull(cache.get(key1));
        assertNotNull(cache.get(key2));
        assertNotNull(cache.get(key4));
        assertNotNull(cache.get(key1));
        cache.put(key3, createCacheEntry());
        assertNotNull(cache.get(key1));
        assertNotNull(cache.get(key4));
        assertNotNull(cache.get(key3));
    }

    @Test
    public void testTooLargeItem() throws BufferTooSmallException {
        PacketCache cache = new PacketCache(0, 100, 1e64); // 100 bytes cache

        cache.setMaxCacheItemPercentage(50);

        cache.put(key1, createCacheEntry());
        assertNull(cache.get(key1)); // 68 is more than 50% of the size
        assertEquals(0, cache.totalPacketSize());
    }

    @Test
    public void testClearing() throws BufferTooSmallException {
        PacketCache cache = new PacketCache(0, 140, 1e64); // 140 bytes cache

        cache.setMaxCacheItemPercentage(50);

        cache.put(key1, createCacheEntry());
        cache.put(key2, createCacheEntry());

        cache.clear();
        assertNull(cache.get(key1));
        assertNull(cache.get(key2));
        assertEquals(0, cache.totalPacketSize());
    }

    @Test
    public void testRemoving() throws BufferTooSmallException {
        PacketCache cache = new PacketCache(0, length*2, 1e64); // 96*2 bytes cache

        cache.setMaxCacheItemPercentage(50);

        cache.put(key1, createCacheEntry());
        cache.put(key2, createCacheEntry());

        cache.remove(key1);
        assertNull(cache.get(key1));
        assertNotNull(cache.get(key2));
        assertEquals(length, cache.totalPacketSize());
    }

    @Test
    public void testEntryAging() throws BufferTooSmallException {
        // 1k bytes cache, 5h timeout
        PacketCache cache = new PacketCache(0, 1024, 5 * 3600);

        cache.setMaxCacheItemPercentage(50);
        cache.put(key1, createCacheEntry(),
                System.currentTimeMillis() - 10 * 3600 * 1000);
        cache.put(key2, createCacheEntry(), System.currentTimeMillis());
        assertNull(cache.get(key1));
        assertNotNull(cache.get(key2));
    }

    private PacketWrapper createCacheEntry() throws BufferTooSmallException {
        return createCacheEntry(null);
    }

    @Test
    public void testTooBigCapacity() {
        PacketCache cache = new PacketCache(2048, 0, 5 * 3600);
        assertEquals(Integer.MAX_VALUE, cache.getByteCapacity());
    }

    /** Creates a 64-byte packet in an array wrapped in a PacketWrapper */
    private PacketWrapper createCacheEntry(CacheKey key) throws BufferTooSmallException {
        ByteBuffer data = ByteBuffer.allocate(length);

        data.put(queryResultPacketData);
        data.flip();
        BasicPacket[] content = new BasicPacket[] { PacketDecoder.extractPacket(data).packet };

        return new PacketWrapper(key, content);
    }

}
