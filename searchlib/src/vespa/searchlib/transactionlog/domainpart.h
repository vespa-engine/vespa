// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "common.h"
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/util/memory.h>
#include <map>
#include <vector>
#include <atomic>

class FastOS_FileInterface;

namespace search::common { class FileHeaderContext; }
namespace search::transactionlog {

class DomainPart {
private:
    DomainPart(const DomainPart &);
    DomainPart& operator=(const DomainPart &);

public:
    enum Crc {
        ccitt_crc32=1,
        xxh64=2
    };
    typedef std::shared_ptr<DomainPart> SP;
    DomainPart(const vespalib::string &name, const vespalib::string &baseDir, SerialNum s, Crc defaultCrc,
               const common::FileHeaderContext &FileHeaderContext, bool allowTruncate);

    ~DomainPart();

    const vespalib::string &fileName() const { return _fileName; }
    void commit(SerialNum firstSerial, const Packet &packet);
    bool erase(SerialNum to);
    bool visit(SerialNumRange &r, Packet &packet);
    bool visit(FastOS_FileInterface &file, SerialNumRange &r, Packet &packet);
    bool close();
    void sync();
    SerialNumRange range() const { return _range; }

    SerialNum getSynced() const {
        vespalib::LockGuard guard(_writeLock);
        return _syncedSerial; 
    }
    
    size_t          size() const { return _sz; }
    size_t      byteSize() const {
        return _byteSize.load(std::memory_order_acquire);
    }
    bool        isClosed() const;
private:
    bool openAndFind(FastOS_FileInterface &file, const SerialNum &from);
    int64_t buildPacketMapping(bool allowTruncate);

    static bool read(FastOS_FileInterface &file, Packet::Entry &entry, vespalib::alloc::Alloc &buf, bool allowTruncate);

    void write(FastOS_FileInterface &file, const Packet::Entry &entry);
    static int32_t calcCrc(Crc crc, const void * buf, size_t len);
    void writeHeader(const common::FileHeaderContext &fileHeaderContext);

    class SkipInfo
    {
    public:
        SkipInfo(SerialNum s, uint64_t p) : _id(s), _pos(p) {}

        bool operator ==(const SkipInfo &b) const { return cmp(b) == 0; }
        bool operator  <(const SkipInfo &b) const { return cmp(b) < 0; }
        bool operator  >(const SkipInfo &b) const { return cmp(b) > 0; }
        bool operator <=(const SkipInfo &b) const { return cmp(b) <= 0; }
        bool operator >=(const SkipInfo &b) const { return cmp(b) >= 0; }
        int64_t   filePos() const { return _pos; }
        SerialNum      id() const { return _id; }
    private:
        int64_t cmp(const SkipInfo & b) const { return _id - b._id; }
        SerialNum _id;
        uint64_t  _pos;
    };
    typedef std::vector<SkipInfo> SkipList;
    typedef std::map<SerialNum, Packet> PacketList;
    const Crc      _defaultCrc;
    vespalib::Lock _lock;
    vespalib::Lock _fileLock;
    SerialNumRange _range;
    size_t         _sz;
    std::atomic<uint64_t> _byteSize;
    PacketList     _packets;
    vespalib::string _fileName;
    std::unique_ptr<FastOS_FileInterface> _transLog;
    SkipList       _skipList;
    uint32_t       _headerLen;
    vespalib::Lock _writeLock;
    // Protected by _writeLock
    SerialNum      _writtenSerial;
    SerialNum      _syncedSerial;
};

}
