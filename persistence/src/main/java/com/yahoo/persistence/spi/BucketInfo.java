// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi;

/**
 * Class to represents information about the buckets stored by the persistence provider.
 */
public class BucketInfo {
    public enum ReadyState {
        NOT_READY,
        READY
    }

    public enum ActiveState {
        NOT_ACTIVE,
        ACTIVE
    }

    /** Create an empty bucket info object. */
    public BucketInfo() {
    }

    /**
     * @param checksum The checksum of the bucket contents.
     * @param docCount The number of documents stored
     * @param docSize The total size of the documents stored
     * @param metaEntryCount The number of different versions of documents that are stored (including document removes)
     * @param size The total size of entries in this bucket.
     * @param ready Whether the bucket is ready or not
     * @param active Whether the bucket is active or not
     */
    public BucketInfo(int checksum,
               int docCount,
               int docSize,
               int metaEntryCount,
               int size,
               ReadyState ready,
               ActiveState active) {
        this.checksum = checksum;
        this.documentCount = docCount;
        this.documentSize = docSize;
        this.entryCount = metaEntryCount;
        this.size = size;
        this.ready = ready;
        this.active = active;
    }

    /**
     * Constructor for bucketinfo for providers that don't care about the READY/ACTIVE paradigm.
     *
     * @param checksum The checksum of the bucket contents.
     * @param docCount The number of documents stored
     * @param docSize The total size of the documents stored
     * @param metaEntryCount The number of different versions of documents that are stored (including document removes)
     * @param size The total size of entries in this bucket.
     */
    public BucketInfo(int checksum,
               int docCount,
               int docSize,
               int metaEntryCount,
               int size) {
        this(checksum, docCount, docSize, metaEntryCount, size, ReadyState.NOT_READY, ActiveState.NOT_ACTIVE);
    }

    public boolean equals(BucketInfo other) {
        return checksum == other.checksum &&
                documentCount == other.documentCount &&
                documentSize == other.documentSize &&
                entryCount == other.entryCount &&
                size == other.size &&
                ready == other.ready &&
                active == other.active;
    }

    @Override
    public String toString() {
        String retVal = "BucketInfo(";
        if (valid()) {
            retVal += "crc " + checksum + ", uniqueCount " + documentCount +
                      ", uniqueSize " + documentSize + ", entry count " + entryCount +
                      ", usedSize " + size + ", ready " + isReady() +
                      ", active " + isActive();
        } else {
            retVal += "invalid";
        }
        retVal += ")";
        return retVal;
    }

    /**
     * @return Get the checksum of the bucket. An empty bucket should have checksum of
     * zero. The checksum should only include data from the latest versions of
     * non-removed documents. Otherwise, the checksum implementation is up to
     * the persistence implementation. (Unless one wants to run multiple
     * persistence implementations in the same cluster, in which case they have
     * to match).
     */
    public int getChecksum() { return checksum; }

    /**
     * The number of unique documents that have not been removed from the
     * bucket. A unique document count above the splitting threshold will cause
     * the bucket to be split.
     */
    public int getDocumentCount() { return documentCount; }

    /**
     * The total size of all the unique documents in this bucket. A size above
     * the splitting threshold will cause the bucket to be split. Knowing size
     * is optional, but a bucket with more than zero unique documents should
     * always return a non-zero value for size. If splitting on size is not
     * required or desired, a simple solution here is to just set the number
     * of unique documents as the size.
     */
    public int  getDocumentSize() { return documentSize; }

    /**
     * The number of meta entries in the bucket. For a persistence layer
     * keeping history of data (multiple versions of a document or remove
     * entries), it may use more meta entries in the bucket than it has unique
     * documents If the sum of meta entries from a pair of joinable buckets go
     * below the join threshold, the buckets will be joined.
     */
    public int  getEntryCount() { return entryCount; }

    /**
     * The total size used by the persistence layer to store all the documents
     * for a given bucket. Possibly excluding pre-allocated space not currently
     * in use. Knowing size is optional, but if the bucket contains more than
     * zero meta entries, it should return a non-zero value for used size.
     */
    public int  getUsedSize() { return size; }

    /**
     * @return Returns true if this bucket is considered "ready". Ready buckets
     * are prioritized before non-ready buckets to be set active.
     */
    public boolean isReady() { return ready == ReadyState.READY; }

    /**
     * @return Returns true if this bucket is "active". If it is, the bucket should
     * be included in read operations outside of the persistence provider API.
     */
    public boolean isActive() { return active == ActiveState.ACTIVE; }

    public boolean valid()
    { return (documentCount > 0 || documentSize == 0); }

    int checksum = 0;
    int documentCount = 0;
    int documentSize = 0;
    int entryCount = 0;
    int size = 0;
    ReadyState ready = ReadyState.READY;
    ActiveState active = ActiveState.NOT_ACTIVE;
}
