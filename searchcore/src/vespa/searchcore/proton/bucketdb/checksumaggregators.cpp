// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "checksumaggregators.h"
#include <xxhash.h>

namespace proton::bucketdb {

using BucketChecksum = ChecksumAggregator::BucketChecksum;
using Timestamp = ChecksumAggregator::Timestamp;
using GlobalId = ChecksumAggregator::GlobalId;

namespace {

uint32_t
gidChecksum(const GlobalId &gid)
{
    uint32_t i[3];
    memcpy(i, gid.get(), GlobalId::LENGTH);
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



XXH64ChecksumAggregator *
XXH64ChecksumAggregator::clone() const {
    return new XXH64ChecksumAggregator(*this);
}
XXH64ChecksumAggregator &
XXH64ChecksumAggregator::addDoc(const GlobalId &gid, const Timestamp &timestamp) {
    _checksum ^= compute(gid, timestamp);
    return *this;
}
XXH64ChecksumAggregator &
XXH64ChecksumAggregator::removeDoc(const GlobalId &gid, const Timestamp &timestamp) {
    _checksum ^= compute(gid, timestamp);
    return *this;
}
XXH64ChecksumAggregator &
XXH64ChecksumAggregator::addChecksum(const ChecksumAggregator & rhs) {
    _checksum ^= dynamic_cast<const XXH64ChecksumAggregator &>(rhs)._checksum;
    return *this;
}
XXH64ChecksumAggregator &
XXH64ChecksumAggregator::removeChecksum(const ChecksumAggregator & rhs) {
    _checksum ^= dynamic_cast<const XXH64ChecksumAggregator &>(rhs)._checksum;
    return *this;
}
BucketChecksum
XXH64ChecksumAggregator::getChecksum() const {
    return BucketChecksum((_checksum >> 32) ^ (_checksum & 0xffffffffL));
}
bool
XXH64ChecksumAggregator::empty() const { return _checksum == 0; }

uint64_t
XXH64ChecksumAggregator::compute(const GlobalId &gid, const Timestamp &timestamp) {
    char buffer[20];
    memcpy(&buffer[0], gid.get(), GlobalId::LENGTH);
    uint64_t tmp = timestamp.getValue();
    memcpy(&buffer[GlobalId::LENGTH], &tmp, sizeof(tmp));
    return XXH64(buffer, sizeof(buffer), 0);
}

}
