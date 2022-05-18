// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::spi::BucketInfo
 * \ingroup spi
 */

#pragma once

#include <vespa/persistence/spi/types.h>

namespace vespalib { class asciistream; }

namespace storage::spi {

class BucketInfo {
public:
    enum ReadyState {
        NOT_READY,
        READY
    };

    enum ActiveState {
        NOT_ACTIVE,
        ACTIVE
    };

    /** Create an invalid bucket info object. */
    BucketInfo();

    BucketInfo(BucketChecksum checksum,
               uint32_t docCount,
               uint32_t docSize,
               uint32_t entryCount,
               uint32_t size,
               ReadyState ready = READY,
               ActiveState active = NOT_ACTIVE);

    bool operator==(const BucketInfo& o) const;

    vespalib::string toString() const;

    /**
     * Get the checksum of the bucket. An empty bucket should have checksum of
     * zero. The checksum should only include data from the latest versions of
     * non-removed documents. Otherwise, the checksum implementation is up to
     * the persistence implementation. (Unless one wants to run multiple
     * persistence implementations in the same cluster, in which case they have
     * to match).
     */
    BucketChecksum getChecksum() const { return _checksum; }

    /**
     * The number of unique documents that have not been removed from the
     * bucket. A unique document count above the splitting threshold will cause
     * the bucket to be split.
     */
    uint32_t getDocumentCount() const { return _documentCount; }

    /**
     * The total size of all the unique documents in this bucket. A size above
     * the splitting threshold will cause the bucket to be split. Knowing size
     * is optional, but a bucket with more than zero unique documents should
     * always return a non-zero value for size. If splitting on size is not
     * required or desired, a simple solution here is to just set the number
     * of unique documents as the size.
     */
    uint32_t getDocumentSize() const { return _documentSize; }

    /**
     * The number of entries in the bucket. For a persistence layer
     * keeping history of data (multiple versions of a document or remove
     * entries), it may use more meta entries in the bucket than it has unique
     * documents If the sum of meta entries from a pair of joinable buckets go
     * below the join threshold, the buckets will be joined.
     */
    uint32_t getEntryCount() const { return _entryCount; }

    /**
     * The total size used by the persistence layer to store all the documents
     * for a given bucket. Possibly excluding pre-allocated space not currently
     * in use. Knowing size is optional, but if the bucket contains more than
     * zero entries, it should return a non-zero value for used size.
     */
    uint32_t getUsedSize() const { return _size; }

    ReadyState getReady() const { return _ready; }
    ActiveState getActive() const { return _active; }

    bool isReady() const { return _ready == READY; }
    bool isActive() const { return _active == ACTIVE; }

private:
    BucketChecksum _checksum;
    uint32_t _documentCount;
    uint32_t _documentSize;
    uint32_t _entryCount;
    uint32_t _size;
    ReadyState _ready;
    ActiveState _active;
};

vespalib::asciistream& operator<<(vespalib::asciistream& out, const BucketInfo& info);
std::ostream& operator<<(std::ostream& out, const BucketInfo& info);

}
