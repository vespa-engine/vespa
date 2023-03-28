// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/buffer.h>
#include <vespa/vespalib/util/compressionconfig.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <memory>
#include <mutex>
#include <vector>

namespace vespalib {
    class nbostream;
    class DataBuffer;
}
namespace vespalib::alloc { class Alloc; }

namespace search {

class ChunkFormat;

class ChunkMeta {
public:
    ChunkMeta() noexcept : ChunkMeta(0, 0, 0, 0) { }
    ChunkMeta(uint64_t offset, uint32_t size, uint64_t lastSerial, uint32_t numEntries) noexcept
        : _offset(offset),
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
    LidMeta() noexcept : LidMeta(0, 0) { }
    LidMeta(uint32_t lid, uint32_t sz) noexcept : _lid(lid), _size(sz) { }
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
    using UP = std::unique_ptr<Chunk>;
    using CompressionConfig = vespalib::compression::CompressionConfig;
    class Config {
    public:
        Config(size_t maxBytes) noexcept : _maxBytes(maxBytes) { }
        size_t getMaxBytes() const { return _maxBytes; }
    private:
      size_t _maxBytes;
    };
    class Entry {
    public:
        Entry() noexcept : Entry(0, 0, 0 ) { }
        Entry(uint32_t lid, uint32_t sz, uint32_t offset) noexcept : _lid(lid), _sz(sz), _offset(offset) { }
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
    using LidList = std::vector<Entry>;
    Chunk(uint32_t id, const Config & config);
    Chunk(uint32_t id, const void * buffer, size_t len);
    ~Chunk();
    LidMeta append(uint32_t lid, const void * buffer, size_t len);
    ssize_t read(uint32_t lid, vespalib::DataBuffer & buffer) const;
    std::pair<size_t, vespalib::alloc::Alloc> read(uint32_t lid) const;
    size_t count() const { return _lids.size(); }
    bool empty() const { return count() == 0; }
    size_t size() const;
    const LidList & getLids() const { return _lids; }
    LidList getUniqueLids() const;
    size_t getMaxPackSize(CompressionConfig compression) const;
    void pack(uint64_t lastSerial, vespalib::DataBuffer & buffer, CompressionConfig compression);
    uint64_t getLastSerial() const { return _lastSerial; }
    uint32_t getId() const { return _id; }
    vespalib::ConstBufferRef getLid(uint32_t lid) const;
    const vespalib::nbostream & getData() const;
    bool hasRoom(size_t len) const;
    vespalib::MemoryUsage getMemoryUsage() const;
private:
    vespalib::nbostream & getData();

    uint32_t                      _id;
    uint64_t                      _lastSerial;
    std::unique_ptr<ChunkFormat>  _format;
    LidList                       _lids;
    mutable std::mutex            _lock;
};

using ChunkMetaV = std::vector<ChunkMeta>;

} // namespace search

