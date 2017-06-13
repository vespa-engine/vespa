// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memslot.h"
#include <iostream>
#include <iomanip>

#define ASSERT_FLAG(a) \
{ \
    if (!(_flags & a)) { \
        std::ostringstream error; \
        error << "Expected one of flags " << std::hex << a << " to be set at " \
              << "this point, but only the given flags are set: " << _flags \
              << ", " << toString(true); \
        throw vespalib::IllegalStateException(error.str(), VESPA_STRLOC); \
    } \
}

namespace storage {
namespace memfile {

MemSlot::MemSlot(const MemSlot& other)
    : _timestamp(other._timestamp),
      _header(other._header),
      _body(other._body),
      _gid(other._gid),
      _flags(other._flags),
      _checksum(other._checksum)
{ }

MemSlot::MemSlot(const GlobalId& gid, Timestamp time,
                 DataLocation header, DataLocation body,
                 uint16_t flags, uint16_t checksum)
    : _timestamp(time),
      _header(header),
      _body(body),
      _gid(gid),
      _flags(flags),
      _checksum(checksum)
{ }

MemSlot::~MemSlot() { }

MemSlot::MemoryUsage
MemSlot::getCacheSize() const
{
    MemoryUsage retVal;
    retVal.metaSize = sizeof(MemSlot);
    retVal.headerSize = _header._size;
    retVal.bodySize = _body._size;
    return retVal;
}

MemSlot&
MemSlot::operator=(const MemSlot& other)
{
    _timestamp = other._timestamp;
    _header = other._header;
    _body = other._body;
    _gid = other._gid;
    _checksum = other._checksum;

    // Flags must be copied after cache.
    _flags = other._flags;
    return *this;
}

void
MemSlot::swap(MemSlot& other)
{
    std::swap(_timestamp, other._timestamp);
    std::swap(_header, other._header);
    std::swap(_body, other._body);
    std::swap(_gid, other._gid);
    std::swap(_checksum, other._checksum);
    std::swap(_flags, other._flags);
}

bool
MemSlot::hasBodyContent() const
{
    return _body._size > 0;
}

bool
MemSlot::operator==(const MemSlot& other) const
{
    if (_checksum != other._checksum
        || _timestamp != other._timestamp
        || _header != other._header
        || _body != other._body
        || _flags != other._flags
        || _gid != other._gid)
    {
        return false;
    }
    return true;
}

void
MemSlot::print(std::ostream& out, bool verbose,
               const std::string& /*indent*/) const
{
    if (verbose) {
        out << "MemSlot(";
    }
    out << std::dec << _timestamp << ", " << _gid.toString() << ", h "
        << _header._pos << " - " << _header._size << ", b "
        << _body._pos << " - " << _body._size << ", f "

        << std::hex << _flags << ", c " << _checksum;
    if (verbose) {
        out << ")";
    }
}

std::string
MemSlot::MemoryUsage::toString() const
{
    std::ostringstream ss;
    ss << "MemoryUsage(meta=" << metaSize
       << ", header=" << headerSize
       << ", body=" << bodySize
       << ")";
    return ss.str();
}

std::string
MemSlot::toString(bool verbose) const {
    std::ostringstream ost;
    print(ost, verbose, "");
    return ost.str();
}

std::ostream&
operator<<(std::ostream& out, const MemSlot& slot) {
    slot.print(out, false, "");
    return out;
}


} // memfile
} // storage
