// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "checksumaggregator.h"

namespace proton::bucketdb {

/**
 * Implementations of the legacy bucket checksums.
 **/
class LegacyChecksumAggregator : public ChecksumAggregator {
public:
    explicit LegacyChecksumAggregator(BucketChecksum seed) : _checksum(seed) { }
    LegacyChecksumAggregator * clone() const override;
    LegacyChecksumAggregator & addDoc(const GlobalId &gid, const Timestamp &timestamp) override;
    LegacyChecksumAggregator & removeDoc(const GlobalId &gid, const Timestamp &timestamp) override;
    LegacyChecksumAggregator & addChecksum(const ChecksumAggregator & rhs) override;
    LegacyChecksumAggregator & removeChecksum(const ChecksumAggregator & rhs) override;
    BucketChecksum getChecksum() const override;
    bool empty() const override;
private:
    uint32_t _checksum;
};

/**
 * Implementations of the bucket checksums based on XXHASH64.
 **/
class XXH64ChecksumAggregator : public ChecksumAggregator {
public:
    explicit XXH64ChecksumAggregator(BucketChecksum seed) : _checksum(seed) { }
    XXH64ChecksumAggregator * clone() const override;
    XXH64ChecksumAggregator & addDoc(const GlobalId &gid, const Timestamp &timestamp) override;
    XXH64ChecksumAggregator & removeDoc(const GlobalId &gid, const Timestamp &timestamp) override;
    XXH64ChecksumAggregator & addChecksum(const ChecksumAggregator & rhs) override;
    XXH64ChecksumAggregator & removeChecksum(const ChecksumAggregator & rhs) override;
    BucketChecksum getChecksum() const override;
    bool empty() const override;
private:
    static uint64_t compute(const GlobalId &gid, const Timestamp &timestamp);
    uint64_t _checksum;
};

}
