// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/buffer.h>
#include <vespa/vespalib/util/time.h>

namespace search::transactionlog {

/// This represents a type of the entry. Fx update,remove
using Type = uint32_t;

class SerialNumRange
{
public:
    SerialNumRange() : _from(0), _to(0) { }
    explicit SerialNumRange(SerialNum f) : _from(f), _to(f ? f-1 : f) { }
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
        Entry(SerialNum u, Type t, const vespalib::ConstBufferRef & d)
            : _unique(u),
              _type(t),
              _valid(true),
              _data(d)
        { }
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
    explicit Packet(size_t reserved) : _count(0), _range(), _buf(reserved) { }
    Packet(const void * buf, size_t sz);
    Packet(const Packet &) = delete;
    Packet & operator =(const Packet &) = delete;
    Packet(Packet &&) noexcept = default;
    Packet & operator =(Packet &&) noexcept = default;
    ~Packet();
    void add(const Entry & data);
    void clear() { _buf.clear(); _count = 0; _range.from(0); _range.to(0); }
    const SerialNumRange & range() const { return _range; }
    const vespalib::nbostream & getHandle() const { return _buf; }
    size_t                  size() const { return _count; }
    bool                   empty() const { return _count == 0; }
    size_t             sizeBytes() const { return _buf.size(); }
    void merge(const Packet & packet);
    void shrinkToFit();
private:
    size_t                            _count;
    SerialNumRange                    _range;
    vespalib::nbostream_longlivedbuf  _buf;
};

int makeDirectory(const char * dir);

class Writer {
public:
    using DoneCallback = std::shared_ptr<vespalib::IDestructorCallback>;
    using DoneCallbacksList = std::vector<DoneCallback>;
    using CommitPayload = std::shared_ptr<DoneCallbacksList>;
    class CommitResult {
    public:
        CommitResult();
        CommitResult(CommitPayload callBacks);
        CommitResult(CommitResult &&) noexcept = default;
        CommitResult & operator = (CommitResult &&) noexcept = default;
        CommitResult(const CommitResult &) = delete;
        CommitResult & operator = (const CommitResult &) = delete;
        ~CommitResult();
        size_t getNumOperations() const { return _callBacks->size(); }
    private:
        CommitPayload _callBacks;
    };
    virtual ~Writer() = default;
    virtual void append(const Packet & packet, DoneCallback done) = 0;
    [[nodiscard]] virtual CommitResult startCommit(DoneCallback onDone) = 0;
};

class WriterFactory {
public:
    virtual ~WriterFactory() = default;
    virtual std::shared_ptr<Writer> getWriter(const vespalib::string & domainName) const = 0;
};

class Destination {
public:
    virtual ~Destination() = default;
    virtual bool send(int32_t id, const vespalib::string & domain, const Packet & packet) = 0;
    virtual bool sendDone(int32_t id, const vespalib::string & domain) = 0;
    virtual bool connected() const = 0;
    virtual bool ok() const = 0;
};

class CommitChunk {
public:
    CommitChunk(size_t reserveBytes, size_t reserveCount);
    CommitChunk(size_t reserveBytes, Writer::CommitPayload postponed);
    ~CommitChunk();
    bool empty() const { return _callBacks->empty(); }
    void add(const Packet & packet, Writer::DoneCallback onDone);
    size_t sizeBytes() const { return _data.sizeBytes(); }
    const Packet & getPacket() const { return _data; }
    Packet stealPacket() { return std::move(_data); }
    size_t getNumCallBacks() const { return _callBacks->size(); }
    Writer::CommitResult createCommitResult() const;
    void setCommitDoneCallback(Writer::DoneCallback onDone) { _onCommitDone = std::move(onDone); }
    Writer::CommitPayload stealCallbacks() { return std::move(_callBacks); }
    void shrinkPayloadToFit();
private:
    Packet                 _data;
    Writer::CommitPayload  _callBacks;
    Writer::DoneCallback   _onCommitDone;
};

}
