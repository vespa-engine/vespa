// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/globalid.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/hash_set.hpp>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".document.base.globalid");

namespace {

bool
validateHex(char c)
{
    return
        (c >= '0' && c <= '9') ||
        (c >= 'a' && c <= 'f') ||
        (c >= 'A' && c <= 'F');
}

uint16_t
getHexVal(char c)
{
    if (c >= '0' && c <= '9') {
        return (c - '0');
    } else if (c >= 'a' && c <= 'f') {
        return (c - 'a' + 10);
    } else if (c >= 'A' && c <= 'F') {
        return (c - 'A' + 10);
    }
    assert(validateHex(c));
    LOG_ABORT("should not be reached");
}

}

namespace document {

bool
GlobalId::BucketOrderCmp::operator()(const GlobalId &lhs, const GlobalId &rhs) const
{
    const unsigned char * __restrict__ a = lhs._gid._buffer;
    const unsigned char * __restrict__ b = rhs._gid._buffer;
    int diff;
    if ((diff = compare(a[0], b[0])) != 0) {
        return diff < 0;
    }
    if ((diff = compare(a[1], b[1])) != 0) {
        return diff < 0;
    }
    if ((diff = compare(a[2], b[2])) != 0) {
        return diff < 0;
    }
    if ((diff = compare(a[3], b[3])) != 0) {
        return diff < 0;
    }
    if ((diff = compare(a[8], b[8])) != 0) {
        return diff < 0;
    }
    if ((diff = compare(a[9], b[9])) != 0) {
        return diff < 0;
    }
    if ((diff = compare(a[10], b[10])) != 0) {
        return diff < 0;
    }
    if ((diff = compare(a[11], b[11])) != 0) {
        return diff < 0;
    }
    return lhs < rhs;
}

vespalib::string GlobalId::toString() const {
    vespalib::asciistream out;
    out << "gid(0x" << vespalib::hex;
    for (int i = 0; i < (int)LENGTH; ++i) {
        unsigned short s1 = _gid._buffer[i] >> 4;
        unsigned short s2 = _gid._buffer[i] & 0xF;
        out << s1 << s2;
    }
    out << ")" << vespalib::dec;
    return out.str();
}

GlobalId
GlobalId::parse(const vespalib::stringref & source)
{
    if (source.substr(0, 6) != "gid(0x") {
        throw vespalib::IllegalArgumentException(
                "A gid must start with \"gid(0x\". Invalid source: '" + source
                + "'.", VESPA_STRLOC);
    }
    if (source.size() != 2 * LENGTH + 7) {
        vespalib::asciistream ost;
        ost << "A gid string representation must be exactly "
            << (2 * LENGTH + 7) << " bytes long. Invalid source: '"
            << source << "'.";
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
    if (source.substr(2 * LENGTH + 6, 1) != ")") {
        throw vespalib::IllegalArgumentException(
                "A gid must end in \")\". Invalid source: '" + source
                + "'.", VESPA_STRLOC);
    }
    GlobalId id;
    for (uint32_t i = 0; i < LENGTH; ++i) {
        char c1 = source[6 + 2*i];
        char c2 = source[6 + 2*i + 1];
        if (!validateHex(c1) || !validateHex(c2)) {
            throw vespalib::IllegalArgumentException(
                    "A gid can only contain hexidecimal characters [0-9a-fA-F]."
                    " Invalid source: '" + source + "'.", VESPA_STRLOC);
        }
        id._gid._buffer[i] = (getHexVal(c1) << 4) | getHexVal(c2);
        /*
        std::cerr << "Hexval of " << source[6 + 2*i] << " and "
                  << source[6 + 2*i + 1] << " is " << getHexVal(c1) << " and "
                  << getHexVal(c2) << ", which forms the byte "
                  << (int) id._gid._buffer[i] << "\n";
        */
    }
    return id;
}

bool
GlobalId::containedInBucket(const BucketId &bucket) const
{
    return bucket.contains(convertToBucketId());
}

GlobalId
GlobalId::calculateFirstInBucket(const BucketId& bucket)
{
    //std::cerr << "First: Calculating gid from " << bucket << "\n" << std::hex;
    BucketId::Type location, gid;
    uint32_t usedBits(bucket.getUsedBits());
    if (usedBits > 32) {
        BucketId::Type gidMask(0x03ffffff00000000ull);
        BucketId::Type locationMask(0x00000000ffffffffull);
        uint32_t usedGidBits = usedBits - 32;
        gidMask = gidMask << (32 - usedGidBits) >> (32 - usedGidBits);
        gid = bucket.getRawId() & gidMask;
        location = bucket.getRawId() & locationMask;
    } else {
        BucketId::Type locationMask(0x00000000ffffffffull);
        locationMask = locationMask << (64 - usedBits) >> (64 - usedBits);
        gid = 0;
        location = bucket.getRawId() & locationMask;
    }
    //std::cerr << "First: Got location " << location << " and gid " << gid
    //          << "\n";
    uint8_t raw[GlobalId::LENGTH];
    memcpy(&raw[0], &location, 4);
    memcpy(&raw[0] + 4, &gid, 8);
    //std::cerr << "First: " << GlobalId(raw) << ", bucket "
    //          << GlobalId(raw).convertToBucketId() << "\n" << std::dec;
    return GlobalId(raw);
}

GlobalId
GlobalId::calculateLastInBucket(const BucketId& bucket)
{
    //std::cerr << "Last: Calculating gid from " << bucket << "\n" << std::hex;
    BucketId::Type location, gid;
    uint32_t usedBits(bucket.getUsedBits());
    if (usedBits > 32) {
        BucketId::Type gidMask(0x03ffffff00000000ull);
        BucketId::Type locationMask(0x00000000ffffffffull);
        uint32_t usedGidBits = usedBits - 32;
        gidMask = gidMask << (32 - usedGidBits) >> (32 - usedGidBits);
        BucketId::Type gidRevMask(gidMask ^ 0xffffffffffffffffull);
        gid = (bucket.getRawId() & gidMask) | gidRevMask;
        location = bucket.getRawId() & locationMask;
    } else {
        BucketId::Type locationMask(0x00000000ffffffffull);
        locationMask = locationMask << (64 - usedBits) >> (64 - usedBits);
        BucketId::Type locationRevMask(locationMask ^ 0xffffffffffffffffull);
        gid = 0xffffffffffffffffull;
        location = (bucket.getRawId() & locationMask) | locationRevMask;
    }
    //std::cerr << "Last: Got location " << location << " and gid " << gid
    //          << "\n";
    uint8_t raw[GlobalId::LENGTH];
    memcpy(&raw[0], &location, 4);
    memcpy(&raw[0] + 4, &gid, 8);
    //std::cerr << "Last: " << GlobalId(raw) << ", bucket "
    //          << GlobalId(raw).convertToBucketId() << "\n" << std::dec;
    return GlobalId(raw);
}

vespalib::asciistream & operator << (vespalib::asciistream & os, const GlobalId & gid)
{
    return os << gid.toString();
}

std::ostream & operator << (std::ostream & os, const GlobalId & gid)
{
    return os << gid.toString();
}

} // document

VESPALIB_HASH_SET_INSTANTIATE_H(document::GlobalId, document::GlobalId::hash);
