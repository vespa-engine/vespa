// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

uint64_t
compute(const GlobalId &gid, const Timestamp &timestamp) {
    char buffer[20];
    memcpy(&buffer[0], gid.get(), GlobalId::LENGTH);
    uint64_t tmp = timestamp.getValue();
    memcpy(&buffer[GlobalId::LENGTH], &tmp, sizeof(tmp));
    return XXH64(buffer, sizeof(buffer), 0);
}

}

uint32_t
LegacyChecksumAggregator::addDoc(const GlobalId &gid, const Timestamp &timestamp, uint32_t checkSum) {
    return add(calcChecksum(gid, timestamp), checkSum);
}

uint32_t
LegacyChecksumAggregator::removeDoc(const GlobalId &gid, const Timestamp &timestamp, uint32_t checkSum) {
    return remove(calcChecksum(gid, timestamp), checkSum);
}

uint64_t
XXH64ChecksumAggregator::update(const GlobalId &gid, const Timestamp &timestamp, uint64_t checkSum) {
    return update(compute(gid, timestamp), checkSum);
}

}
