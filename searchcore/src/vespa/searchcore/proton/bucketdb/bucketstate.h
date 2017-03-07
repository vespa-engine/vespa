// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton
{

namespace bucketdb
{

/**
 * Class BucketState represent the known state of a bucket in raw form.
 */
class BucketState
{
public:
    typedef document::GlobalId GlobalId;
    typedef storage::spi::Timestamp Timestamp;

private:
    static constexpr uint32_t READY = static_cast<uint32_t>(SubDbType::READY);
    static constexpr uint32_t REMOVED =
        static_cast<uint32_t>(SubDbType::REMOVED);
    static constexpr uint32_t NOTREADY =
        static_cast<uint32_t>(SubDbType::NOTREADY);
    static constexpr uint32_t COUNTS = static_cast<uint32_t>(SubDbType::COUNT);
    uint32_t _docCount[COUNTS];
    uint32_t _checksum;
    size_t   _docSizes[COUNTS];
    bool _active;

public:
    BucketState();

    static uint32_t calcChecksum(const GlobalId &gid, const Timestamp &timestamp);

    void
    add(const GlobalId &gid, const Timestamp &timestamp, uint32_t docSize, SubDbType subDbType);

    void
    remove(const GlobalId &gid, const Timestamp &timestamp, uint32_t docSize, SubDbType subDbType);

    void
    modify(const Timestamp &oldTimestamp, uint32_t oldDocSize,
           const Timestamp &newTimestamp, uint32_t newDocSize,
           SubDbType subDbType);

    bool
    isActive() const
    {
        return _active;
    }

    BucketState &
    setActive(bool active)
    {
        _active = active;
        return *this;
    }

    uint32_t
    getReadyCount() const
    {
        return _docCount[READY];
    }

    uint32_t
    getRemovedCount() const
    {
        return _docCount[REMOVED];
    }

    uint32_t
    getNotReadyCount() const
    {
        return _docCount[NOTREADY];
    }

    size_t getReadyDocSizes() const { return _docSizes[READY]; }
    size_t getRemovedDocSizes() const { return _docSizes[REMOVED]; }
    size_t getNotReadyDocSizes() const { return _docSizes[NOTREADY]; }

    uint32_t
    getDocumentCount() const
    {
        return getReadyCount() + getNotReadyCount();
    }

    uint32_t
    getEntryCount() const
    {
        return getDocumentCount() + getRemovedCount();
    }

    storage::spi::BucketChecksum
    getChecksum() const
    {
        return storage::spi::BucketChecksum(_checksum);
    }

    bool
    empty() const;

    BucketState &
    operator+=(const BucketState &rhs);

    BucketState &
    operator-=(const BucketState &rhs);

    void
    applyDelta(BucketState *src, BucketState *dst) const;

    operator storage::spi::BucketInfo() const;
};

}

}
