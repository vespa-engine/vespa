// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

/**
 * Representation of a bucket identifier.
 */
public class BucketId implements Comparable<BucketId> {
    public static final int COUNT_BITS = 6;
    private long id = 0;
    private static long[] usedMask;

    static {
        usedMask = new long[59];
        long val = 0;
        for (int i=0; i<usedMask.length; ++i) {
            usedMask[i] = val;
            val = (val << 1) | 1;
        }
    }

    /**
     * Default-constructed BucketId signifies an invalid bucket ID.
     */
    public BucketId() {
    }

    /**
     * Creates a bucket id with the given raw bucket id. This is a 64 bit mask
     * where the first 6 MSB bits set how many LSB bits should actually be used.
     * Right now it only have simple functionality. More will be added for it
     * to be configurable.
     */
    public BucketId(long id) {
        this.id = id;
    }

    public BucketId(int usedBits, long id) {
        long usedMask = ((long) usedBits) << (64 - COUNT_BITS);
        id <<= COUNT_BITS;
        id >>>= COUNT_BITS;
        this.id = id | usedMask;
    }

    public BucketId(String serialized) {
        if (!serialized.startsWith("BucketId(0x")) {
            throw new IllegalArgumentException("Serialized bucket id must start with 'BucketId(0x'");
        }
        if (!serialized.endsWith(")")) {
            throw new IllegalArgumentException("Serialized bucket id must end with ')'");
        }

        // Parse hex string after "0x"
        int index;
        char c;
        long id = 0;
        for (index = 11; index < serialized.length()-1; index++) {
            c = serialized.charAt(index);
            if (!((c>=48 && c<=57) || // digit
                  (c>=97 && c<=102))) { // a-f
                throw new IllegalArgumentException("Serialized bucket id (" + serialized + ") contains illegal character at position " + index);
            }
            id <<= 4;
            id += Integer.parseInt(String.valueOf(c),16);
        }
        this.id = id;
        if (getUsedBits() == 0) {
            throw new IllegalArgumentException("Created bucket id "+id+", but no countbits are set");
        }
    }

    public boolean equals(Object o) {
        return (o instanceof BucketId && ((BucketId) o).getId() == this.getId());
    }

    public int compareTo(BucketId other) {
        if (id >>> 32 == other.id >>> 32) {
            if ((id & 0xFFFFFFFFL) > (other.id & 0xFFFFFFFFL)) {
                return 1;
            } else if ((id & 0xFFFFFFFFL) < (other.id & 0xFFFFFFFFL)) {
                return -1;
            }
            return 0;
        } else if ((id >>> 32) > (other.id >>> 32)) {
            return 1;
        } else {
            return -1;
        }
    }

    public int hashCode() {
        return (int) id;
    }

    public int getUsedBits() { return (int) (id >>> (64 - COUNT_BITS)); }

    public long getRawId() { return id; }

    public long getId() {
        int notUsed = 64 - getUsedBits();
        long usedMask  = (0xFFFFFFFFFFFFFFFFL << notUsed) >>> notUsed;
        long countMask = (0xFFFFFFFFFFFFFFFFL >>> (64 - COUNT_BITS)) << (64 - COUNT_BITS);
        return id & (usedMask | countMask);
    }

    public long withoutCountBits() {
        return id & usedMask[getUsedBits()];
    }

    public String toString() {
        StringBuilder sb = new StringBuilder().append("BucketId(0x");
        String number = Long.toHexString(getId());
        for (int i=number.length(); i<16; ++i) {
            sb.append('0');
        }
        sb.append(number).append(')');
        return sb.toString();
    }

    public boolean contains(BucketId id) {
        if (id.getUsedBits() < getUsedBits()) {
            return false;
        }
        BucketId copy = new BucketId(getUsedBits(), id.getRawId());
        return (copy.getId() == getId());
    }

    public boolean contains(DocumentId docId, BucketIdFactory factory) {
        return contains(factory.getBucketId(docId));
    }
}
