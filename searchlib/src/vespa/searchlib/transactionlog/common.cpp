// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "common.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fastos/file.h>

namespace search::transactionlog {

using vespalib::nbostream;
using vespalib::nbostream_longlivedbuf;
using vespalib::make_string;
using std::runtime_error;

namespace {

void throwRangeError(SerialNum prev, SerialNum next) __attribute__((noinline));

void throwRangeError(SerialNum prev, SerialNum next) {
    if (prev < next) return;
    throw runtime_error(make_string("The new serialnum %zu is not higher than the old one %zu", next, prev));
}

}

int
makeDirectory(const char * dir)
{
    int retval(-1);

    FastOS_StatInfo st;
    if ( FastOS_File::Stat(dir, &st) ) {
        retval = st._isDirectory ? 0 : -2;
    } else {
        retval = FastOS_File::MakeDirectory(dir) ? 0 : -3;
    }

    return retval;
}

int64_t
SerialNumRange::cmp(const SerialNumRange & b) const
{
    int64_t diff(0);
    if ( ! (contains(b) || b.contains(*this)) ) {
        diff = _from - b._from;
    }
    return diff;
}

Packet::Packet(const void * buf, size_t sz) :
     _count(0),
     _range(),
     _buf(static_cast<const char *>(buf), sz)
{
    nbostream_longlivedbuf os(_buf.c_str(), sz);
    while ( os.size() > 0 ) {
        Entry e;
        e.deserialize(os);
        if (_range.to() == 0) {
            _range.from(e.serial());
        }
        _range.to(e.serial());
        _count++;
    }
}

void
Packet::merge(const Packet & packet)
{
    if (_range.to() >= packet.range().from()) {
        throwRangeError(_range.to(), packet.range().from());
    }
    if (_buf.empty()) {
        _range.from(packet.range().from());
    }
    _count += packet._count;
    _range.to(packet._range.to());
    _buf.write(packet.getHandle().c_str(), packet.getHandle().size());
}

nbostream &
Packet::Entry::deserialize(nbostream & os)
{
    _valid = false;
    int32_t len(0);
    os >> _unique >> _type >> len;
    _data = vespalib::ConstBufferRef(os.peek(), len);
    os.adjustReadPos(len);
    _valid = true;
    return os;
}

nbostream &
Packet::Entry::serialize(nbostream & os) const
{
    os << _unique << _type << static_cast<uint32_t>(_data.size());
    os.write(_data.c_str(), _data.size());
    return os;
}

Packet::Entry::Entry(SerialNum u, Type t, const vespalib::ConstBufferRef & d) :
    _unique(u),
    _type(t),
    _valid(true),
    _data(d)
{ }

void
Packet::add(const Packet::Entry & e)
{
    if (_range.to() >= e.serial()) {
        throwRangeError(_range.to(), e.serial());
    }

    if (_buf.empty()) {
        _range.from(e.serial());
    }
    e.serialize(_buf);
    _count++;
    _range.to(e.serial());
}

}
