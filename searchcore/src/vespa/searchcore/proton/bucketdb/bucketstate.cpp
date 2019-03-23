// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketstate.h"
#include "checksumaggregators.h"
#include <cassert>
#include <xxhash.h>

namespace proton::bucketdb {

namespace {

uint32_t
toIdx(SubDbType subDbType) {
    return static_cast<uint32_t>(subDbType);
}

}

BucketState::ChecksumType  BucketState::_checksumType = BucketState::ChecksumType::LEGACY;

std::unique_ptr<ChecksumAggregator>
BucketState::createChecksum(BucketChecksum seed) {
    return ChecksumAggregator::create(_checksumType, seed);
}

void
BucketState::setChecksumType(ChecksumType type) {
    _checksumType = type;
}

BucketState::~BucketState() = default;
BucketState::BucketState(const BucketState & rhs) = default;
BucketState::BucketState(BucketState && rhs) noexcept = default;
BucketState & BucketState::operator=(BucketState && rhs) noexcept = default;

BucketState::BucketState()
    : _docCount(),
      _docSizes(),
      _checksum(createChecksum(BucketChecksum(0))),
      _active(false)
{
    for (uint32_t i = 0; i < COUNTS; ++i) {
        _docCount[i] = 0;
        _docSizes[i] = 0;
    }
}

BucketState::BucketChecksum
BucketState::addChecksum(BucketChecksum a, BucketChecksum b) {
    return createChecksum(a)->addChecksum(*createChecksum(b)).getChecksum();
}

void
BucketState::add(const GlobalId &gid, const Timestamp &timestamp, uint32_t docSize, SubDbType subDbType)
{
    assert(subDbType < SubDbType::COUNT);
    if (subDbType != SubDbType::REMOVED) {
        _checksum->addDoc(gid, timestamp);
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
        _checksum->removeDoc(gid, timestamp);
    }
    --_docCount[subDbTypeIdx];
    _docSizes[subDbTypeIdx] -= docSize;
}

void
BucketState::modify(const GlobalId &gid,
                    const Timestamp &oldTimestamp, uint32_t oldDocSize,
                    const Timestamp &newTimestamp, uint32_t newDocSize,
                    SubDbType subDbType)
{
    assert(subDbType < SubDbType::COUNT);
    uint32_t subDbTypeIdx = toIdx(subDbType);
    assert(_docCount[subDbTypeIdx] > 0);
    assert(_docSizes[subDbTypeIdx] >= oldDocSize);
    if (subDbType != SubDbType::REMOVED) {
        _checksum->removeDoc(gid, oldTimestamp);
        _checksum->addDoc(gid, newTimestamp);
    }
    _docSizes[subDbTypeIdx] = _docSizes[subDbTypeIdx] + newDocSize - oldDocSize;
}

bool
BucketState::empty() const
{
    if (getReadyCount() != 0 || getRemovedCount() != 0 ||
        getNotReadyCount() != 0)
        return false;
    assert(_checksum->empty());
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
    _checksum->addChecksum(*rhs._checksum);
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
    _checksum->removeChecksum(*rhs._checksum);
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

    return BucketInfo(getChecksum(),
                      documentCount, docSizes,
                      entryCount, entrySizes,
                      notReady > 0 ? BucketInfo::NOT_READY : BucketInfo::READY,
                      _active ? BucketInfo::ACTIVE : BucketInfo::NOT_ACTIVE);
}

}

