// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "checksumaggregator.h"
#include <vespa/searchcore/proton/common/subdbtype.h>
#include <vespa/vespalib/util/memory.h>

namespace proton::bucketdb {

class ChecksumAggregator;

/**
 * Class BucketState represent the known state of a bucket in raw form.
 */
class BucketState
{
public:
    using ChecksumType = ChecksumAggregator::ChecksumType;
    using GlobalId = document::GlobalId;
    using Timestamp = storage::spi::Timestamp;
    using BucketChecksum = storage::spi::BucketChecksum;

private:
    static constexpr uint32_t READY = static_cast<uint32_t>(SubDbType::READY);
    static constexpr uint32_t REMOVED = static_cast<uint32_t>(SubDbType::REMOVED);
    static constexpr uint32_t NOTREADY = static_cast<uint32_t>(SubDbType::NOTREADY);
    static constexpr uint32_t COUNTS = static_cast<uint32_t>(SubDbType::COUNT);
    union { uint32_t _legacy; uint64_t _xxh64;} _ch;
    size_t   _docSizes[COUNTS];
    uint32_t _docCount[COUNTS];
    bool     _active;

    static ChecksumType _checksumType;
    static std::unique_ptr<ChecksumAggregator> createChecksum(BucketChecksum seed);

public:
    static void setChecksumType(ChecksumType checksum);
    BucketState();
    BucketState(const BucketState & rhs);
    BucketState(BucketState && rhs) noexcept;
    BucketState & operator=(BucketState && rhs) noexcept;
    ~BucketState();

    static BucketChecksum addChecksum(BucketChecksum a, BucketChecksum b);

    void add(const GlobalId &gid, const Timestamp &timestamp, uint32_t docSize, SubDbType subDbType);
    void remove(const GlobalId &gid, const Timestamp &timestamp, uint32_t docSize, SubDbType subDbType);

    void modify(const GlobalId &gid,
                const Timestamp &oldTimestamp, uint32_t oldDocSize,
                const Timestamp &newTimestamp, uint32_t newDocSize,
                SubDbType subDbType);

    BucketState &
    setActive(bool active) {
        _active = active;
        return *this;
    }

    bool isActive() const { return _active; }
    uint32_t getReadyCount() const { return _docCount[READY]; }
    uint32_t getRemovedCount() const { return _docCount[REMOVED]; }
    uint32_t getNotReadyCount() const { return _docCount[NOTREADY]; }
    size_t getReadyDocSizes() const { return _docSizes[READY]; }
    size_t getRemovedDocSizes() const { return _docSizes[REMOVED]; }
    size_t getNotReadyDocSizes() const { return _docSizes[NOTREADY]; }
    uint32_t getDocumentCount() const { return getReadyCount() + getNotReadyCount(); }
    uint32_t getActiveDocumentCount() const { return isActive() ? getDocumentCount() : 0u;}
    uint32_t getEntryCount() const { return getDocumentCount() + getRemovedCount(); }
    BucketChecksum getChecksum() const;
    bool empty() const;
    BucketState &operator+=(const BucketState &rhs);
    BucketState &operator-=(const BucketState &rhs);
    void applyDelta(BucketState *src, BucketState *dst) const;
    operator storage::spi::BucketInfo() const;
};

}
