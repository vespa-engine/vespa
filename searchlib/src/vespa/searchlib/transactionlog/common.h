// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchlib/common/idestructorcallback.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/buffer.h>

namespace search::transactionlog {

/// This represents a type of the entry. Fx update,remove
typedef uint32_t Type;
/// A channel represents one data stream.

class RPC
{
public:
enum Result { OK, FULL, ERROR };
};

class SerialNumRange
{
public:
    SerialNumRange() : _from(0), _to(0) { }
    SerialNumRange(SerialNum f) : _from(f), _to(f ? f-1 : f) { }
    SerialNumRange(SerialNum f, SerialNum t) : _from(f), _to(t) { }
    bool operator == (const SerialNumRange & b) const { return cmp(b) == 0; }
    bool operator <  (const SerialNumRange & b) const { return cmp(b) < 0; }
    bool operator >  (const SerialNumRange & b) const { return cmp(b) > 0; }
    bool operator <= (const SerialNumRange & b) const { return cmp(b) <= 0; }
    bool operator >= (const SerialNumRange & b) const { return cmp(b) >= 0; }
    SerialNum from()                        const { return _from; }
    SerialNum   to()                        const { return _to; }
    void from(SerialNum v)                { _from = v; }
    void to(SerialNum v)                  { _to = v; }

    bool contains(SerialNum s) const {
        return (_from <= s) && (s <= _to);
    }

    bool contains(const SerialNumRange & b) const {
        return (_from <= b._from) && (b._to <= _to);
    }
private:
    int64_t cmp(const SerialNumRange & b) const;
    SerialNum _from;
    SerialNum _to;
};

class Packet
{
public:
    class Entry
    {
    public:
        Entry() : _unique(0), _type(0), _valid(false), _data() { }
        Entry(SerialNum u, Type t, const vespalib::ConstBufferRef & d);
        SerialNum            serial() const { return _unique; }
        Type                   type() const { return _type; }
        bool                  valid() const { return _valid; }
        size_t                serializedSize() const { return sizeof(SerialNum) + sizeof(Type) + sizeof(uint32_t) + _data.size(); }
        const vespalib::ConstBufferRef & data() const { return _data; }
        vespalib::nbostream & deserialize(vespalib::nbostream & is);
        vespalib::nbostream & serialize(vespalib::nbostream & os) const;
    private:
        SerialNum                _unique;
        Type                     _type;
        bool                     _valid;
        vespalib::ConstBufferRef _data;
    };
public:
    Packet(size_t m=0xf000) : _count(0), _range(), _limit(m), _buf(m) { }
    Packet(const void * buf, size_t sz);
    bool add(const Entry & data);
    void close() { }
    void clear() { _buf.clear(); _count = 0; _range.from(0); _range.to(0); }
    const SerialNumRange & range() const { return _range; }
    const vespalib::nbostream & getHandle() const { return _buf; }
    size_t                  size() const { return _count; }
    bool                   empty() const { return _count == 0; }
    size_t             sizeBytes() const { return _buf.size(); }
    bool merge(const Packet & packet);
private:
    size_t                            _count;
    SerialNumRange                    _range;
    size_t                            _limit;
    vespalib::nbostream_longlivedbuf  _buf;
};

int makeDirectory(const char * dir);

class Writer {
public:
    using DoneCallback = std::shared_ptr<IDestructorCallback>;
    virtual ~Writer() { }
    virtual void commit(const vespalib::string & domainName, const Packet & packet, DoneCallback done) = 0;
};

}
