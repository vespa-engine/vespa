// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "checksumaggregator.h"

namespace proton::bucketdb {

class LegacyChecksumAggregator : public ChecksumAggregator {
public:
    LegacyChecksumAggregator(BucketChecksum seed) : _checksum(seed) { }
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

class XXHChecksumAggregator : public ChecksumAggregator {
public:
    XXHChecksumAggregator(BucketChecksum seed) : _checksum(seed) { }
    XXHChecksumAggregator * clone() const override;
    XXHChecksumAggregator & addDoc(const GlobalId &gid, const Timestamp &timestamp) override;
    XXHChecksumAggregator & removeDoc(const GlobalId &gid, const Timestamp &timestamp) override;
    XXHChecksumAggregator & addChecksum(const ChecksumAggregator & rhs) override;
    XXHChecksumAggregator & removeChecksum(const ChecksumAggregator & rhs) override;
    BucketChecksum getChecksum() const override;
    bool empty() const override;
private:
    static uint64_t compute(const GlobalId &gid, const Timestamp &timestamp);
    uint64_t _checksum;
};

}
