// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.idstring.DocIdString;
import com.yahoo.document.idstring.IdIdString;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
    @SuppressWarnings("deprecation")
    public void testBucketGeneration() {
        // TODO Rewrite in time for VESPA 8 to use IdIdString
        BucketIdFactory factory = new BucketIdFactory(32, 26, 6);
        DocumentId doc1 = new DocumentId(new DocIdString("ns", "spec"));
        DocumentId doc2 = new DocumentId(new DocIdString("ns2", "spec"));
        DocumentId doc3 = new DocumentId(new DocIdString("ns", "spec2"));
        DocumentId userDoc1 = new DocumentId(new IdIdString("ns", "mytype","n=18", "spec"));
        DocumentId userDoc2 = new DocumentId(new IdIdString("ns", "mytype","n=18", "spec2"));
        DocumentId userDoc3 = new DocumentId(new IdIdString("ns", "mytype","n=19", "spec"));
        DocumentId groupDoc1 = new DocumentId(new IdIdString("ns", "mytype", "g=yahoo.com", "spec"));
        DocumentId groupDoc2 = new DocumentId(new IdIdString("ns2", "mytype", "g=yahoo.com", "spec2"));
        DocumentId groupDoc3 = new DocumentId(new IdIdString("ns", "mytype", "g=yahoo", "spec"));


        BucketId docBucket1 = factory.getBucketId(doc1);
        BucketId docBucket2 = factory.getBucketId(doc2);
        BucketId docBucket3 = factory.getBucketId(doc3);
        BucketId userDocBucket1 = factory.getBucketId(userDoc1);
        BucketId userDocBucket2 = factory.getBucketId(userDoc2);
        BucketId userDocBucket3 = factory.getBucketId(userDoc3);
        BucketId groupDocBucket1 = factory.getBucketId(groupDoc1);
        BucketId groupDocBucket2 = factory.getBucketId(groupDoc2);
        BucketId groupDocBucket3 = factory.getBucketId(groupDoc3);

        assertEquals(new Hex(0xeb3089a300000012L), new Hex(userDocBucket1.getRawId()));
        assertEquals(new Hex(0xea780a8700000012L), new Hex(userDocBucket2.getRawId()));
        assertEquals(new Hex(0xe80d16fc00000013L), new Hex(userDocBucket3.getRawId()));

        assertEquals(new Hex(0xeb82f2be9a1acd50L), new Hex(groupDocBucket1.getRawId()));
        assertEquals(new Hex(0xebff6e379a1acd50L), new Hex(groupDocBucket2.getRawId()));
        assertEquals(new Hex(0xe91b9600afe81f24L), new Hex(groupDocBucket3.getRawId()));

        assertEquals(new Hex(0xe980c9abd5fd8d11L), new Hex(docBucket1.getRawId()));
        assertEquals(new Hex(0xeafe870c5f9c37b9L), new Hex(docBucket2.getRawId()));
        assertEquals(new Hex(0xeaebe9473ecbcd69L), new Hex(docBucket3.getRawId()));
    }

    //Actually a BucketId testcase ...
    @Test
    public void testBidContainsBid() {
        BucketId bid = new BucketId(18, 0x123456789L);

        assert(bid.contains(new BucketId(20, 0x123456789L)));
        assert(bid.contains(new BucketId(18, 0x888f56789L)));
        assert(bid.contains(new BucketId(24, 0x888456789L)));
        assert(!bid.contains(new BucketId(24, 0x888886789L)));
        assert(!bid.contains(new BucketId(16, 0x123456789L)));
    }

    @Test
    public void testBidContainsDocId() {
        DocumentId docId = new DocumentId("id:ns:recovery:n=18:99999");
        BucketIdFactory factory = new BucketIdFactory(32, 26, 6);
        BucketId bid = new BucketId(16, 0x12L);
        assert(bid.contains(docId, factory));
        //split on '0'
        bid = new BucketId(17, 0x12L);
        assert(bid.contains(docId, factory));
        //split on '1'
        bid = new BucketId(17, (1L<<16) + 0x12L);
        assert(!bid.contains(docId, factory));
    }

    @Test
    public void testBucketIdSerializationAndCompare() {
        BucketId bid = new BucketId(18, 0x123456789L);

        assertEquals(bid, new BucketId(bid.toString()));
        assertEquals(0, bid.compareTo(new BucketId(18, 0x123456789L)));
    }

}

