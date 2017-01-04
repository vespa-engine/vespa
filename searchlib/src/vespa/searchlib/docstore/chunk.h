// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/docstore/chunkformat.h>
#include <vespa/searchlib/util/memoryusage.h>
#include <vespa/vespalib/util/memory.h>

namespace search {

class ChunkMeta {
public:
    ChunkMeta() :
        _offset(0),
        _lastSerial(0),
        _size(0),
        _numEntries(0)
    { }
    ChunkMeta(uint64_t offset, uint32_t size, uint64_t lastSerial, uint32_t numEntries) :
        _offset(offset),
        _lastSerial(lastSerial),
        _size(size),
        _numEntries(numEntries)
    { }
    uint32_t getNumEntries() const { return _numEntries; }
    uint32_t getSize() const { return _size; }
    uint64_t getLastSerial() const { return _lastSerial; }
    uint64_t getOffset() const { return _offset; }
    vespalib::nbostream & deserialize(vespalib::nbostream & is);
    vespalib::nbostream & serialize(vespalib::nbostream & os) const;
    bool operator < (const ChunkMeta & b) const { return _lastSerial < b._lastSerial; }
private:
    uint64_t _offset;
    uint64_t _lastSerial;
    uint32_t _size;
    uint32_t _numEntries;
};

class LidMeta {
public:
    LidMeta() : _lid(0), _size(0) { }
    LidMeta(uint32_t lid, uint32_t sz) : _lid(lid), _size(sz) { }
    uint32_t     getLid() const { return _lid; }
    uint32_t       size() const { return _size; }
    vespalib::nbostream & deserialize(vespalib::nbostream & is);
    vespalib::nbostream & serialize(vespalib::nbostream & os) const;
private:
    uint32_t _lid;
    uint32_t _size;
};

class Chunk {
public:
    typedef std::unique_ptr<Chunk> UP;
    class Config {
    public:
        Config(size_t maxBytes, size_t maxCount) : _maxBytes(maxBytes), _maxCount(maxCount) { }
        size_t getMaxBytes() const { return _maxBytes; }
        size_t getMaxCount() const { return _maxCount; }
    private:
      size_t _maxBytes;
      size_t _maxCount;
    };
    class Entry {
    public:
        Entry() : _lid(0), _sz(0), _offset(0) { }
        Entry(uint32_t lid, uint32_t sz, uint32_t offset) : _lid(lid), _sz(sz), _offset(offset) { }
        uint32_t       getLid() const { return _lid; }
        uint32_t         size() const { return _sz + 2*4; }
        uint32_t      netSize() const { return _sz; }
        uint32_t getNetOffset() const { return _offset + 2*4; }
        uint32_t    getOffset() const { return _offset; }
    private:
        uint32_t _lid;
        uint32_t _sz;
        uint32_t _offset;
    };
    typedef std::vector<Entry> LidList;
    Chunk(uint32_t id, const Config & config);
    Chunk(uint32_t id, const void * buffer, size_t len, bool skipcrc=false);
    LidMeta append(uint32_t lid, const void * buffer, size_t len);
    ssize_t read(uint32_t lid, vespalib::DataBuffer & buffer) const;
    size_t count() const { return _lids.size(); }
    bool empty() const { return count() == 0; }
    size_t size() const { return getData().size(); }
    const LidList & getLids() const { return _lids; }
    LidList getUniqueLids() const;
    size_t getMaxPackSize(const document::CompressionConfig & compression) const { return _format->getMaxPackSize(compression); }
    void pack(uint64_t lastSerial, vespalib::DataBuffer & buffer, const document::CompressionConfig & compression);
    uint64_t getLastSerial() const { return _lastSerial; }
    uint32_t getId() const { return _id; }
    bool validSerial() const { return getLastSerial() != static_cast<uint64_t>(-1l); }
    vespalib::ConstBufferRef getLid(uint32_t lid) const;
    const vespalib::nbostream & getData() const { return _format->getBuffer(); }
    bool hasRoom(size_t len) const;
    MemoryUsage getMemoryUsage() const;
private:
    vespalib::nbostream & getData() { return _format->getBuffer(); }

    uint32_t           _id;
    uint32_t           _nextOffset;
    uint64_t           _lastSerial;
    ChunkFormat::UP    _format;
    LidList            _lids;
};

typedef std::vector<ChunkMeta> ChunkMetaV;

} // namespace search

