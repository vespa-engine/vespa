// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>

#include "bucketdb.h"

namespace proton
{

namespace bucketdb
{

namespace
{

uint32_t
gidChecksum(const document::GlobalId &gid)
{
    union {
        const unsigned char *_c;
        const uint32_t *_i;
    } u;
    u._c = gid.get();
    const uint32_t *i = u._i;
    return i[0] + i[1] + i[2];
}


uint32_t
timestampChecksum(const storage::spi::Timestamp &timestamp)
{
    return (timestamp >> 32) + timestamp;
}

inline uint32_t toIdx(SubDbType subDbType)
{
    return static_cast<uint32_t>(subDbType);
}

}

BucketState::BucketState()
    : _docCount(),
      _checksum(0),
      _docSizes(),
      _active(false)
{
    for (uint32_t i = 0; i < COUNTS; ++i) {
        _docCount[i] = 0;
        _docSizes[i] = 0;
    }
}

uint32_t
BucketState::calcChecksum(const GlobalId &gid, const Timestamp &timestamp)
{
    return gidChecksum(gid) + timestampChecksum(timestamp);
}


void
BucketState::add(const GlobalId &gid, const Timestamp &timestamp, uint32_t docSize, SubDbType subDbType)
{
    assert(subDbType < SubDbType::COUNT);
    if (subDbType != SubDbType::REMOVED) {
        _checksum += calcChecksum(gid, timestamp);
    }
    uint32_t subDbTypeIdx = toIdx(subDbType);
    ++_docCount[subDbTypeIdx];
    _docSizes[subDbTypeIdx] += docSize;
}


void
BucketState::remove(const GlobalId &gid, const Timestamp &timestamp, uint32_t docSize, SubDbType subDbType)
{
    assert(subDbType < SubDbType::COUNT);
    uint32_t subDbTypeIdx = toIdx(subDbType);
    assert(_docCount[subDbTypeIdx] > 0);
    assert(_docSizes[subDbTypeIdx] >= docSize);
    if (subDbType != SubDbType::REMOVED) {
        _checksum -= calcChecksum(gid, timestamp);
    }
    --_docCount[subDbTypeIdx];
    _docSizes[subDbTypeIdx] -= docSize;
}


void
BucketState::modify(const Timestamp &oldTimestamp, uint32_t oldDocSize,
                    const Timestamp &newTimestamp, uint32_t newDocSize,
                    SubDbType subDbType)
{
    assert(subDbType < SubDbType::COUNT);
    uint32_t subDbTypeIdx = toIdx(subDbType);
    assert(_docCount[subDbTypeIdx] > 0);
    assert(_docSizes[subDbTypeIdx] >= oldDocSize);
    if (subDbType != SubDbType::REMOVED) {
        _checksum = _checksum - timestampChecksum(oldTimestamp) +
                    timestampChecksum(newTimestamp);
    }
    _docSizes[subDbTypeIdx] = _docSizes[subDbTypeIdx] + newDocSize - oldDocSize;
}


bool
BucketState::empty() const
{
    if (getReadyCount() != 0 || getRemovedCount() != 0 ||
        getNotReadyCount() != 0)
        return false;
    assert(_checksum == 0);
    for (uint32_t i = 0; i < COUNTS; ++i) {
        assert(_docSizes[i] == 0);
    }
    return true;
}


BucketState &
BucketState::operator+=(const BucketState &rhs)
{
    for (uint32_t i = 0; i < COUNTS; ++i) {
        _docCount[i] += rhs._docCount[i];
    }
    for (uint32_t i = 0; i < COUNTS; ++i) {
        _docSizes[i] += rhs._docSizes[i];
    }
    _checksum += rhs._checksum;
    return *this;
}


BucketState &
BucketState::operator-=(const BucketState &rhs)
{
    for (uint32_t i = 0; i < COUNTS; ++i) {
        assert(_docCount[i] >= rhs._docCount[i]);
    }
    for (uint32_t i = 0; i < COUNTS; ++i) {
        assert(_docSizes[i] >= rhs._docSizes[i]);
    }
    for (uint32_t i = 0; i < COUNTS; ++i) {
        _docCount[i] -= rhs._docCount[i];
    }
    for (uint32_t i = 0; i < COUNTS; ++i) {
        _docSizes[i] -= rhs._docSizes[i];
    }
    _checksum -= rhs._checksum;
    return *this;
}


void
BucketState::applyDelta(BucketState *src, BucketState *dst) const
{
    if (empty())
        return;
    assert(src);
    assert(dst);
    *src -= *this;
    *dst += *this;
}

BucketState::operator storage::spi::BucketInfo() const
{
    uint32_t notReady = getNotReadyCount();
    uint32_t documentCount = getReadyCount() + notReady;
    uint32_t entryCount = documentCount + getRemovedCount();
    size_t docSizes = getReadyDocSizes() + getNotReadyDocSizes();
    size_t entrySizes = docSizes + getRemovedDocSizes();

    using BucketInfo = storage::spi::BucketInfo;

    return BucketInfo(storage::spi::BucketChecksum(_checksum),
                      documentCount,
                      docSizes,
                      entryCount,
                      entrySizes,
                      notReady > 0 ? BucketInfo::NOT_READY : BucketInfo::READY,
                      _active ? BucketInfo::ACTIVE : BucketInfo::NOT_ACTIVE);
}


}

}
