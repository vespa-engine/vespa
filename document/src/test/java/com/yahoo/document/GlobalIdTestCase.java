// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Einar M R Rosenvinge
 */
public class GlobalIdTestCase {

    private final byte[] raw0 = new byte[0];
    private final byte[] raw1_0 = new byte[]{(byte) 0};
    private final byte[] raw2_11 = new byte[]{(byte) 1, (byte) 1};
    private final byte[] raw2_minus1_1 = new byte[]{(byte) -1, (byte) 1};
    private final byte[] raw12_1to12 = new byte[]{(byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5, (byte) 6, (byte) 7,
            (byte) 8, (byte) 9, (byte) 10, (byte) 11, (byte) 12};
    private final byte[] raw13 = new byte[]{(byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5, (byte) 6, (byte) 7,
            (byte) 8, (byte) 9, (byte) 10, (byte) 11, (byte) 12, (byte) 13};

    private final BucketIdFactory bucketIdFactory = new BucketIdFactory();

    @Test
    public void testRaw0() {
        GlobalId gid = new GlobalId(raw0);
        assertEquals(12, gid.getRawId().length);
        byte[] raw = gid.getRawId();
        for (byte b : raw) {
            assertEquals((byte) 0, b);
        }

        GlobalId gid2 = new GlobalId(raw1_0);
        assertEquals(12, gid2.getRawId().length);
        byte[] raw2 = gid2.getRawId();
        for (byte b : raw2) {
            assertEquals((byte) 0, b);
        }

        assertEquals(gid, gid2);
        assertTrue(Arrays.equals(raw, raw2));
        assertEquals(gid.hashCode(), gid2.hashCode());
    }

    @Test
    public void testLonger() {
        GlobalId gid1 = new GlobalId(raw2_11);
        GlobalId gid2 = new GlobalId(raw2_minus1_1);

        assertFalse(gid1.equals(gid2));
        assertFalse(gid1.hashCode() == gid2.hashCode());

        GlobalId gid3 = new GlobalId(raw13);
        GlobalId gid4 = new GlobalId(raw12_1to12);
        assertEquals(gid3, gid4);
        assertEquals(gid3.hashCode(), gid4.hashCode());
    }

    @Test
    public void testCompareTo() {
        GlobalId gid0 = new GlobalId(raw1_0);
        GlobalId gid11 = new GlobalId(raw2_11);
        GlobalId gidminus11 = new GlobalId(raw2_minus1_1);

        assertEquals(-1, gid0.compareTo(gid11));
        assertEquals(1, gid11.compareTo(gid0));

        assertEquals(-1, gid0.compareTo(gidminus11));
        assertEquals(1, gidminus11.compareTo(gid0));

        assertEquals(-1, gid11.compareTo(gidminus11));
        assertEquals(1, gidminus11.compareTo(gid11));
    }

    private void verifyGidToBucketIdMapping(String idString) {
        DocumentId documentId = new DocumentId(idString);
        GlobalId globalId = new GlobalId(documentId.getGlobalId());
        BucketId bucketIdThroughGlobalId = globalId.toBucketId();
        BucketId bucketIdThroughFactory = bucketIdFactory.getBucketId(documentId);
        assertEquals(bucketIdThroughFactory, bucketIdThroughGlobalId);
    }

    @Test
    public void testToBucketId() {
        verifyGidToBucketIdMapping("userdoc:ns:1:abc");
        verifyGidToBucketIdMapping("userdoc:ns:1000:abc");
        verifyGidToBucketIdMapping("userdoc:hsgf:18446744073700000000:dfdfsdfg");
        verifyGidToBucketIdMapping("groupdoc:ns:somegroup:hmm");
        verifyGidToBucketIdMapping("doc:foo:test");
        verifyGidToBucketIdMapping("doc:myns:http://foo.bar");
        verifyGidToBucketIdMapping("doc:jsrthsdf:a234aleingzldkifvasdfgadf");
    }

}
