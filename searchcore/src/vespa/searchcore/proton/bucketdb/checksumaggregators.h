// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "checksumaggregator.h"

namespace proton::bucketdb {

/**
 * Implementations of the legacy bucket checksums.
 **/
class LegacyChecksumAggregator : public ChecksumAggregator {
public:
    static uint32_t addDoc(const GlobalId &gid, const Timestamp &timestamp, uint32_t checkSum);
    static uint32_t removeDoc(const GlobalId &gid, const Timestamp &timestamp, uint32_t checkSum);
    static uint32_t add(uint32_t checksum, uint32_t aggr) { return aggr + checksum; }
    static uint32_t remove(uint32_t checksum, uint32_t aggr) { return aggr - checksum; }
    static BucketChecksum get(uint32_t checkSum) { return BucketChecksum(checkSum); }
};

/**
 * Implementations of the bucket checksums based on XXHASH64.
 **/
class XXH64ChecksumAggregator : public ChecksumAggregator {
public:
    static uint64_t update(const GlobalId &gid, const Timestamp &timestamp, uint64_t checkSum);
    static uint64_t update(uint64_t a, uint64_t b) { return a ^ b; }
    static BucketChecksum get(uint64_t checkSum) {
        return BucketChecksum((checkSum >> 32) ^ (checkSum & 0xffffffffL));
    }
};

}
