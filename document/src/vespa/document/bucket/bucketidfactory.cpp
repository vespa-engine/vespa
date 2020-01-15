// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketidfactory.h"
#include "bucketid.h"
#include <vespa/document/base/documentid.h>
#include <ostream>
#include <cassert>
#include <limits>

namespace document {

BucketIdFactory::BucketIdFactory()
    : _locationBits(32),
      _gidBits(26),
      _countBits(6),
      _locationMask(0),
      _gidMask(0),
      _initialCount(0)
{
    initializeMasks();
}

void
BucketIdFactory::initializeMasks()
{
    assert(_countBits == 6);
    _locationMask = _gidMask = std::numeric_limits<uint64_t>::max();

    _locationMask <<= (_gidBits + _countBits);
    _locationMask >>= (_gidBits + _countBits);

    _gidMask >>= _locationBits;
    _gidMask <<= (_locationBits + _countBits);
    _gidMask >>= _countBits;

    _initialCount = _locationBits + _gidBits;
    _initialCount <<= 58;
}

BucketId
BucketIdFactory::getBucketId(const DocumentId& id) const
{
    uint64_t location = id.getScheme().getLocation();
    assert(GlobalId::LENGTH >= sizeof(uint64_t) + 4u);
    uint64_t gid = reinterpret_cast<const uint64_t&>(*(id.getGlobalId().get() + 4));



    return BucketId(_locationBits + _gidBits,
        _initialCount | (_gidMask & gid) | (_locationMask & location));
}

void
BucketIdFactory::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    out << "BucketIdFactory("
        << _locationBits << " location bits, "
        << _gidBits << " gid bits, "
        << _countBits << " count bits";
    if (verbose) {
        out << std::hex;
        out << ",\n" << indent << "                location mask: "
            << _locationMask;
        out << ",\n" << indent << "                gid mask: "
            << _gidMask;
        out << ",\n" << indent << "                initial count: "
            << _initialCount;
        out << std::dec;
    }
    out << ")";
}

} // document
