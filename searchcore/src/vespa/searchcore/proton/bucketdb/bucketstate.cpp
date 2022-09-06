// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

void
BucketState::setChecksumType(ChecksumType type) {
    _checksumType = type;
}

BucketState::~BucketState() = default;
BucketState::BucketState(const BucketState & rhs) = default;
BucketState::BucketState(BucketState && rhs) noexcept = default;
BucketState & BucketState::operator=(BucketState && rhs) noexcept = default;

BucketState::BucketState()
    : _ch(),
      _docSizes(),
      _docCount(),
      _active(false)
{
    memset(&_ch, 0, sizeof(_ch));
    for (uint32_t i = 0; i < COUNTS; ++i) {
        _docCount[i] = 0;
        _docSizes[i] = 0;
    }
}

BucketState::BucketChecksum
BucketState::getChecksum() const {
    switch (_checksumType) {
        case ChecksumAggregator::ChecksumType::LEGACY:
            return LegacyChecksumAggregator::get(_ch._legacy);
        case ChecksumAggregator::ChecksumType::XXHASH64:
            return XXH64ChecksumAggregator::get(_ch._xxh64);
    }
    abort();
}

BucketState::BucketChecksum
BucketState::addChecksum(BucketChecksum a, BucketChecksum b) {
    switch (_checksumType) {
        case ChecksumAggregator::ChecksumType::LEGACY:
            return LegacyChecksumAggregator::get(LegacyChecksumAggregator::add(b, a));
        case ChecksumAggregator::ChecksumType::XXHASH64:
            return XXH64ChecksumAggregator::get(XXH64ChecksumAggregator::update(b, a));
    }
    abort();
}

void
BucketState::add(const GlobalId &gid, const Timestamp &timestamp, uint32_t docSize, SubDbType subDbType)
{
    assert(subDbType < SubDbType::COUNT);
    if (subDbType != SubDbType::REMOVED) {
        switch (_checksumType) {
            case ChecksumAggregator::ChecksumType::LEGACY:
                _ch._legacy = LegacyChecksumAggregator::addDoc(gid, timestamp, _ch._legacy);
                break;
            case ChecksumAggregator::ChecksumType::XXHASH64:
                _ch._xxh64 = XXH64ChecksumAggregator::update(gid, timestamp, _ch._xxh64);
                break;
        }
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
        switch (_checksumType) {
            case ChecksumAggregator::ChecksumType::LEGACY:
                _ch._legacy = LegacyChecksumAggregator::removeDoc(gid, timestamp, _ch._legacy);
                break;
            case ChecksumAggregator::ChecksumType::XXHASH64:
                _ch._xxh64 = XXH64ChecksumAggregator::update(gid, timestamp, _ch._xxh64);
                break;
        }
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
        switch (_checksumType) {
            case ChecksumAggregator::ChecksumType::LEGACY:
                _ch._legacy = LegacyChecksumAggregator::removeDoc(gid, oldTimestamp, _ch._legacy);
                _ch._legacy = LegacyChecksumAggregator::addDoc(gid, newTimestamp, _ch._legacy);
                break;
            case ChecksumAggregator::ChecksumType::XXHASH64:
                _ch._xxh64 = XXH64ChecksumAggregator::update(gid, oldTimestamp, _ch._xxh64);
                _ch._xxh64 = XXH64ChecksumAggregator::update(gid, newTimestamp, _ch._xxh64);
                break;
        }
    }
    _docSizes[subDbTypeIdx] = _docSizes[subDbTypeIdx] + newDocSize - oldDocSize;
}

bool
BucketState::empty() const
{
    if (getReadyCount() != 0 || getRemovedCount() != 0 ||
        getNotReadyCount() != 0)
        return false;
    assert((_checksumType == ChecksumAggregator::ChecksumType::LEGACY) ? (_ch._legacy == 0) : (_ch._xxh64 == 0));
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
        _docSizes[i] += rhs._docSizes[i];
    }

    switch (_checksumType) {
        case ChecksumAggregator::ChecksumType::LEGACY:
            _ch._legacy = LegacyChecksumAggregator::add(rhs._ch._legacy, _ch._legacy);
            break;
        case ChecksumAggregator::ChecksumType::XXHASH64:
            _ch._xxh64 = XXH64ChecksumAggregator::update(rhs._ch._xxh64, _ch._xxh64);
            break;
    }
    return *this;
}

BucketState &
BucketState::operator-=(const BucketState &rhs)
{
    for (uint32_t i = 0; i < COUNTS; ++i) {
        assert(_docCount[i] >= rhs._docCount[i]);
        assert(_docSizes[i] >= rhs._docSizes[i]);
    }
    for (uint32_t i = 0; i < COUNTS; ++i) {
        _docCount[i] -= rhs._docCount[i];
        _docSizes[i] -= rhs._docSizes[i];
    }
    switch (_checksumType) {
        case ChecksumAggregator::ChecksumType::LEGACY:
            _ch._legacy = LegacyChecksumAggregator::remove(rhs._ch._legacy, _ch._legacy);
            break;
        case ChecksumAggregator::ChecksumType::XXHASH64:
            _ch._xxh64 = XXH64ChecksumAggregator::update(rhs._ch._xxh64, _ch._xxh64);
            break;
    }
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

    return {getChecksum(), documentCount, uint32_t(docSizes), entryCount, uint32_t(entrySizes),
            notReady > 0 ? BucketInfo::NOT_READY : BucketInfo::READY,
            _active ? BucketInfo::ACTIVE : BucketInfo::NOT_ACTIVE};
}

}

