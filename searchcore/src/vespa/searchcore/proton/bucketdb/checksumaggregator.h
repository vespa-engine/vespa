// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/globalid.h>
#include <vespa/persistence/spi/bucketinfo.h>

namespace proton::bucketdb {

class ChecksumAggregator {
public:
    enum class ChecksumType {LEGACY, XXHASH64};
    using GlobalId = document::GlobalId;
    using Timestamp = storage::spi::Timestamp;
    using BucketChecksum = storage::spi::BucketChecksum;
    virtual ~ChecksumAggregator() = default;
    virtual ChecksumAggregator & addDoc(const GlobalId &gid, const Timestamp &timestamp) = 0;
    virtual ChecksumAggregator & removeDoc(const GlobalId &gid, const Timestamp &timestamp) = 0;
    virtual ChecksumAggregator & addChecksum(const ChecksumAggregator & rhs) = 0;
    virtual ChecksumAggregator & removeChecksum(const ChecksumAggregator & rhs) = 0;
    virtual BucketChecksum getChecksum() const = 0;
    virtual bool empty() const = 0;;
    virtual ChecksumAggregator * clone() const = 0;
    static std::unique_ptr<ChecksumAggregator> create(ChecksumType type, BucketChecksum seed);
};

}
