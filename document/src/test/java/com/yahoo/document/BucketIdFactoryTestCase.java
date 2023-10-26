// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.idstring.IdIdString;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * Date: Sep 7, 2007
 *
 * @author Håkon Humberset
 */
public class BucketIdFactoryTestCase {

    @Test
    public void testNormalUsage() {
        BucketIdFactory factory = new BucketIdFactory();
        assertEquals(BucketId.COUNT_BITS, factory.getCountBitCount());

        assertEquals(64, factory.getLocationBitCount() + factory.getGidBitCount() + BucketId.COUNT_BITS);
    }

    private class Hex {
        long value;

        Hex(long val) {
            value = val;
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Hex && value == ((Hex) o).value);
        }
        @Override
        public int hashCode() { return (int)value; }

        public String toString() {
            return Long.toHexString(value);
        }
    }

    @Test
    public void testBucketGeneration() {
        BucketIdFactory factory = new BucketIdFactory(32, 26, 6);
        DocumentId doc1 = new DocumentId(new IdIdString("ns", "mytype", "", "1"));
        DocumentId doc2 = new DocumentId(new IdIdString("ns2", "mytype", "", "1"));
        DocumentId doc3 = new DocumentId(new IdIdString("ns", "mytype2", "", "1"));
        DocumentId doc4 = new DocumentId(new IdIdString("ns", "mytype", "", "2"));
        DocumentId userDoc1 = new DocumentId(new IdIdString("ns", "mytype","n=18", "spec"));
        DocumentId userDoc2 = new DocumentId(new IdIdString("ns", "mytype","n=18", "spec2"));
        DocumentId userDoc3 = new DocumentId(new IdIdString("ns", "mytype","n=19", "spec"));
        DocumentId groupDoc1 = new DocumentId(new IdIdString("ns", "mytype", "g=yahoo.com", "spec"));
        DocumentId groupDoc2 = new DocumentId(new IdIdString("ns2", "mytype", "g=yahoo.com", "spec2"));
        DocumentId groupDoc3 = new DocumentId(new IdIdString("ns", "mytype", "g=yahoo", "spec"));

        assertEquals(new Hex(0xeb3089a300000012L), new Hex(factory.getBucketId(userDoc1).getRawId()));
        assertEquals(new Hex(0xea780a8700000012L), new Hex(factory.getBucketId(userDoc2).getRawId()));
        assertEquals(new Hex(0xe80d16fc00000013L), new Hex(factory.getBucketId(userDoc3).getRawId()));

        assertEquals(new Hex(0xeb82f2be9a1acd50L), new Hex(factory.getBucketId(groupDoc1).getRawId()));
        assertEquals(new Hex(0xebff6e379a1acd50L), new Hex(factory.getBucketId(groupDoc2).getRawId()));
        assertEquals(new Hex(0xe91b9600afe81f24L), new Hex(factory.getBucketId(groupDoc3).getRawId()));

        assertEquals(new Hex(0xe96b22a03842cac4L), new Hex(factory.getBucketId(doc1).getRawId()));
        assertEquals(new Hex(0xeb8ea3dd3842cac4L), new Hex(factory.getBucketId(doc2).getRawId()));
        assertEquals(new Hex(0xe9a1b4ac3842cac4L), new Hex(factory.getBucketId(doc3).getRawId()));
        assertEquals(new Hex(0xe8222c758d721ec8L), new Hex(factory.getBucketId(doc4).getRawId()));

    }

    //Actually a BucketId testcase ...
    @Test
    public void testBidContainsBid() {
        BucketId bid = new BucketId(18, 0x123456789L);

        assertTrue(bid.contains(new BucketId(20, 0x123456789L)));
        assertTrue(bid.contains(new BucketId(18, 0x888f56789L)));
        assertTrue(bid.contains(new BucketId(24, 0x888456789L)));
        assertTrue(!bid.contains(new BucketId(24, 0x888886789L)));
        assertTrue(!bid.contains(new BucketId(16, 0x123456789L)));
    }

    @Test
    public void testBidContainsDocId() {
        DocumentId docId = new DocumentId("id:ns:recovery:n=18:99999");
        BucketIdFactory factory = new BucketIdFactory(32, 26, 6);
        BucketId bid = new BucketId(16, 0x12L);
        assertTrue(bid.contains(docId, factory));
        //split on '0'
        bid = new BucketId(17, 0x12L);
        assertTrue(bid.contains(docId, factory));
        //split on '1'
        bid = new BucketId(17, (1L<<16) + 0x12L);
        assertTrue(!bid.contains(docId, factory));
    }

    @Test
    public void testBucketIdSerializationAndCompare() {
        BucketId bid = new BucketId(18, 0x123456789L);

        assertEquals(bid, new BucketId(bid.toString()));
        assertEquals(0, bid.compareTo(new BucketId(18, 0x123456789L)));
    }

}

