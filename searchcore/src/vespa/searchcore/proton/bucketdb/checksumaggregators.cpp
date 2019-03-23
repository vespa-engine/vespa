// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "checksumaggregators.h"
#include <xxhash.h>

namespace proton::bucketdb {

using BucketChecksum = ChecksumAggregator::BucketChecksum;
using Timestamp = ChecksumAggregator::Timestamp;
using GlobalId = ChecksumAggregator::GlobalId;

namespace {

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
timestampChecksum(const Timestamp &timestamp)
{
    return (timestamp >> 32) + timestamp;
}


uint32_t
calcChecksum(const GlobalId &gid, const Timestamp &timestamp)
{
    return gidChecksum(gid) + timestampChecksum(timestamp);
}

}

LegacyChecksumAggregator *
LegacyChecksumAggregator::clone() const {
    return new LegacyChecksumAggregator(*this);
}
LegacyChecksumAggregator &
LegacyChecksumAggregator::addDoc(const GlobalId &gid, const Timestamp &timestamp) {
    _checksum += calcChecksum(gid, timestamp);
    return *this;
}
LegacyChecksumAggregator &
LegacyChecksumAggregator::removeDoc(const GlobalId &gid, const Timestamp &timestamp) {
    _checksum -= calcChecksum(gid, timestamp);
    return *this;
}
LegacyChecksumAggregator &
LegacyChecksumAggregator::addChecksum(const ChecksumAggregator & rhs) {
    _checksum += dynamic_cast<const LegacyChecksumAggregator &>(rhs)._checksum;
    return *this;
}
LegacyChecksumAggregator &
LegacyChecksumAggregator::removeChecksum(const ChecksumAggregator & rhs) {
    _checksum -= dynamic_cast<const LegacyChecksumAggregator &>(rhs)._checksum;
    return *this;
}
BucketChecksum
LegacyChecksumAggregator::getChecksum() const {
    return BucketChecksum(_checksum);
}
bool
LegacyChecksumAggregator::empty() const { return _checksum == 0; }



XXHChecksumAggregator *
XXHChecksumAggregator::clone() const {
    return new XXHChecksumAggregator(*this);
}
XXHChecksumAggregator &
XXHChecksumAggregator::addDoc(const GlobalId &gid, const Timestamp &timestamp) {
    _checksum ^= compute(gid, timestamp);
    return *this;
}
XXHChecksumAggregator &
XXHChecksumAggregator::removeDoc(const GlobalId &gid, const Timestamp &timestamp) {
    _checksum ^= compute(gid, timestamp);
    return *this;
}
XXHChecksumAggregator &
XXHChecksumAggregator::addChecksum(const ChecksumAggregator & rhs) {
    _checksum ^= dynamic_cast<const XXHChecksumAggregator &>(rhs)._checksum;
    return *this;
}
XXHChecksumAggregator &
XXHChecksumAggregator::removeChecksum(const ChecksumAggregator & rhs) {
    _checksum ^= dynamic_cast<const XXHChecksumAggregator &>(rhs)._checksum;
    return *this;
}
BucketChecksum
XXHChecksumAggregator::getChecksum() const {
    return BucketChecksum((_checksum >> 32) ^ (_checksum & 0xffffffffL));
}
bool
XXHChecksumAggregator::empty() const { return _checksum == 0; }

uint64_t
XXHChecksumAggregator::compute(const GlobalId &gid, const Timestamp &timestamp) {
    char buffer[20];
    memcpy(&buffer[0], gid.get(), 12);
    reinterpret_cast<uint64_t *>(&buffer[12])[0] = timestamp.getValue();
    return XXH64(buffer, sizeof(buffer), 0);
}

}
