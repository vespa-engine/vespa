// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/memfilepersistence/common/types.h>
#include <vespa/vespalib/util/crc.h>

namespace storage {

namespace memfile {

struct MetaSlot : private Types {
    Timestamp _timestamp;
    GlobalId _gid;
    uint32_t _headerPos;
    uint32_t _headerSize;
    uint32_t _bodyPos;
    uint32_t _bodySize;
    uint16_t _flags;
    uint16_t _checksum;

    MetaSlot() : _timestamp(0), _headerPos(0), _headerSize(0),
                 _bodyPos(0), _bodySize(0), _flags(0), _checksum(39859)
    {
        //_checksum = calcSlotChecksum();
        //std::cerr << "Empty checksum " << _checksum << "\n";
    }

    uint16_t calcSlotChecksum() const {
        static uint32_t size(sizeof(MetaSlot) - sizeof(_checksum));
        vespalib::crc_32_type calculator;
        calculator.process_bytes(this, size);
            return calculator.checksum() & 0xffff;

    }

    bool inUse() const {
        return (_flags & IN_USE);
    }

    void print(std::ostream & out) const;
    void print(vespalib::asciistream & out) const;

    // Functions used by unit tests (avoid renaming all old func usage)
    void updateChecksum() { _checksum = calcSlotChecksum(); }
    void setTimestamp(Timestamp ts) { _timestamp = ts; }
    void setHeaderPos(uint32_t p) { _headerPos = p; }
    void setHeaderSize(uint32_t sz) { _headerSize = sz; }
    void setBodyPos(uint32_t p) { _bodyPos = p; }
    void setBodySize(uint32_t sz) { _bodySize = sz; }
    void setUseFlag(bool isInUse)
    { _flags = (isInUse ? _flags | IN_USE : _flags & ~IN_USE); }
};

std::ostream& operator<<(std::ostream& out, const MetaSlot& slot);
vespalib::asciistream& operator<<(vespalib::asciistream & out, const MetaSlot& slot);

/**
 * Represents a slotfile header.
 */
struct Header {
    uint32_t _version;
    uint32_t _metaDataListSize;
    uint32_t _headerBlockSize;
    uint32_t _checksum;
    uint32_t _fileChecksum;
    uint32_t _notInUse0; // Some reserved bits, which we can use later if
    uint64_t _notInUse1; // needed without altering the file format.
    uint64_t _notInUse2;
    uint64_t _notInUse3;
    uint64_t _notInUse4;
    uint64_t _notInUse5;

    Header()
        : _version(Types::TRADITIONAL_SLOTFILE),
          _metaDataListSize(0),
          _headerBlockSize(0),
          _checksum(0),
          _fileChecksum(0),
          _notInUse0(0), _notInUse1(0), _notInUse2(0),
          _notInUse3(0), _notInUse4(0), _notInUse5(0)
    {
    }

    uint32_t calcHeaderChecksum() const {
        vespalib::crc_32_type calculator;
        calculator.process_bytes(this, 12);
        return calculator.checksum();
    }
    bool verify() const {
        return (_version == Types::TRADITIONAL_SLOTFILE
                && _checksum == calcHeaderChecksum());
    }
    // Functions used by unit tests (avoid renaming all old func usage)
    void updateChecksum() { _checksum = calcHeaderChecksum(); }
    void setVersion(uint32_t version) { _version = version; }
    void setMetaDataListSize(uint32_t sz) { _metaDataListSize = sz; }
    void setHeaderBlockSize(uint32_t sz) { _headerBlockSize = sz; }

    void print(std::ostream& out, const std::string& indent = "") const;
 };

struct FileInfo {
    typedef std::unique_ptr<FileInfo> UP;

    uint32_t _metaDataListSize;
    uint32_t _headerBlockSize;
    uint32_t _bodyBlockSize;

    // Cached header bytes to write in addition to metadata when
    // needing to write back metadata 512 byte aligned
    std::vector<char> _firstHeaderBytes;

    FileInfo();
    FileInfo(uint32_t metaDataListSize, uint32_t headerBlockSize, uint32_t bodyBlockSize);
    FileInfo(const Header& header, size_t fileSize);
    ~FileInfo();

    uint32_t getBlockSize(Types::DocumentPart part) const {
        return (part == Types::BODY ? _bodyBlockSize : _headerBlockSize);
    }
    uint32_t getBlockIndex(Types::DocumentPart part) const {
        return (part == Types::BODY ? getBodyBlockStartIndex()
                : getHeaderBlockStartIndex());
    }
    uint32_t getHeaderBlockStartIndex() const;
    uint32_t getBodyBlockStartIndex() const;
    uint32_t getFileSize() const;
    std::string toString() const;
};

}

}

