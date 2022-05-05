// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "common.h"
#include "ichunk.h"
#include <vespa/vespalib/util/memory.h>
#include <map>
#include <vector>
#include <atomic>
#include <mutex>

class FastOS_FileInterface;

namespace search::common { class FileHeaderContext; }
namespace search::transactionlog {

class DomainPart {
public:
    using SP = std::shared_ptr<DomainPart>;
    DomainPart(const DomainPart &) = delete;
    DomainPart& operator=(const DomainPart &) = delete;
    DomainPart(const vespalib::string &name, const vespalib::string &baseDir, SerialNum s,
               const common::FileHeaderContext &FileHeaderContext, bool allowTruncate);

    ~DomainPart();

    const vespalib::string &fileName() const { return _fileName; }
    void commit(const SerializedChunk & serialized);
    bool erase(SerialNum to);
    bool visit(FastOS_FileInterface &file, SerialNumRange &r, Packet &packet);
    bool close();
    void sync();
    SerialNumRange range() const { return SerialNumRange(get_range_from(), get_range_to()); }

    SerialNum getSynced() const {
        std::lock_guard guard(_writeLock);
        return _syncedSerial; 
    }
    
    size_t          size() const noexcept { return _sz.load(std::memory_order_relaxed); }
    size_t      byteSize() const {
        return _byteSize.load(std::memory_order_acquire);
    }
    bool        isClosed() const;
private:
    using Alloc = vespalib::alloc::Alloc;
    bool openAndFind(FastOS_FileInterface &file, const SerialNum &from);
    int64_t buildPacketMapping(bool allowTruncate);
    static Packet readPacket(FastOS_FileInterface & file, SerialNumRange wanted, size_t targetSize, bool allowTruncate);
    static bool read(FastOS_FileInterface &file, IChunk::UP & chunk, Alloc &buf, bool allowTruncate);

    void write(FastOS_FileInterface &file, SerialNumRange range, vespalib::ConstBufferRef buf);
    void writeHeader(const common::FileHeaderContext &fileHeaderContext);
    void set_size(size_t sz) noexcept { _sz.store(sz, std::memory_order_relaxed); }
    SerialNum get_range_from() const noexcept { return _range_from.load(std::memory_order_relaxed); }
    SerialNum get_range_to() const noexcept { return _range_to.load(std::memory_order_relaxed); }
    void set_range_from(SerialNum range_from) noexcept { _range_from.store(range_from, std::memory_order_relaxed); }
    void set_range_to(SerialNum range_to) noexcept { _range_to.store(range_to, std::memory_order_relaxed); }

    class SkipInfo
    {
    public:
        SkipInfo(SerialNum s, uint64_t p) noexcept : _id(s), _pos(p) {}

        bool operator ==(const SkipInfo &b) const noexcept { return cmp(b) == 0; }
        bool operator  <(const SkipInfo &b) const noexcept { return cmp(b) < 0; }
        bool operator  >(const SkipInfo &b) const noexcept { return cmp(b) > 0; }
        bool operator <=(const SkipInfo &b) const noexcept { return cmp(b) <= 0; }
        bool operator >=(const SkipInfo &b) const noexcept { return cmp(b) >= 0; }
        int64_t   filePos() const noexcept { return _pos; }
        SerialNum      id() const noexcept { return _id; }
    private:
        int64_t cmp(const SkipInfo & b) const noexcept { return _id - b._id; }
        SerialNum _id;
        uint64_t  _pos;
    };
    std::mutex            _lock;
    std::mutex            _fileLock;
    std::atomic<SerialNum> _range_from;
    std::atomic<SerialNum> _range_to;
    std::atomic<size_t>   _sz;
    std::atomic<uint64_t> _byteSize;
    vespalib::string      _fileName;
    std::unique_ptr<FastOS_FileInterface> _transLog;
    std::vector<SkipInfo> _skipList;
    uint32_t              _headerLen;
    mutable std::mutex    _writeLock;
    // Protected by _writeLock
    SerialNum             _writtenSerial;
    SerialNum             _syncedSerial;
};

}
