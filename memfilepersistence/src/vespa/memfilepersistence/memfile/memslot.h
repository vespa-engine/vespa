// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::MemSlot
 * \ingroup memfile
 *
 * \brief Class representing a slot in a MemFile.
 *
 * The MemSlot class keeps all the data we need for a single entry in the
 * slotfile.
 *
 * Note that a lot of these instances will be kept in the memory cache. It is
 * important that the memory footprint of this class is really small, such that
 * we can fit many entries in the cache. The layout of the class is thus a bit
 * specialized to keep a low footprint.
 *
 * Currently, 40 bytes are used for metadata.
 *
 * A note about constness. The cached part are considered mutable, such that
 * all read access can be const. Only operations causing the slot to change on
 * disk (given a flush) is non-const.
 */
#pragma once

#include <vespa/memfilepersistence/common/types.h>

namespace storage {
namespace memfile {

class MemFile;

class MemSlot : private Types
{
    // Metadata for slot we need to keep.
    Timestamp     _timestamp; //   64 bit -  8 bytes timestamp
    DataLocation  _header;    //   2x32 bit - 8 bytes header location
    DataLocation  _body;      //   2x32 bit - 8 bytes body location
    GlobalId      _gid;       //   96 bit - 12 bytes
    uint16_t      _flags;     //   16 bit -  2 bytes flag
    uint16_t      _checksum;  //   16 bit -  2 bytes checksum

    friend class MemFileTest;

    // used by tests to simulate gid collision.
    void setGlobalId(const GlobalId& gid) {
        _gid = gid;
    }

public:
    struct MemoryUsage {
        MemoryUsage() :
            headerSize(0),
            bodySize(0),
            metaSize(0) {}

        MemoryUsage(uint64_t metaSz, uint64_t headerSz, uint64_t bodySz)
            : headerSize(headerSz),
              bodySize(bodySz),
              metaSize(metaSz)
        {}

        uint64_t headerSize;
        uint64_t bodySize;
        uint64_t metaSize;

        uint64_t sum() const {
            return headerSize + bodySize + metaSize;
        }

        void add(const MemoryUsage& usage) {
            headerSize += usage.headerSize;
            bodySize += usage.bodySize;
            metaSize += usage.metaSize;
        }

        void sub(const MemoryUsage& usage) {
            headerSize -= usage.headerSize;
            bodySize -= usage.bodySize;
            metaSize -= usage.metaSize;
        }

        std::string toString() const;
    };

    using UP = std::unique_ptr<MemSlot>;

    MemSlot(const MemSlot&);
    /** Constructor used by mappers reading from file. */
    MemSlot(const GlobalId& gid, Timestamp time,
            DataLocation header, DataLocation body,
            uint16_t flags, uint16_t checksum);
    ~MemSlot();

    MemSlot& operator=(const MemSlot&);
    void swap(MemSlot&);

    Timestamp getTimestamp() const { return _timestamp; }
    const GlobalId& getGlobalId() const { return _gid; }

    DataLocation getLocation(DocumentPart part) const
        { return (part == HEADER ? _header : _body); }

    bool inUse() const            { return (_flags & IN_USE); }
    bool deleted() const          { return (_flags & DELETED); }
    bool deletedInPlace() const   { return (_flags & DELETED_IN_PLACE); }

    bool checksumOutdated() const { return (_flags & CHECKSUM_OUTDATED); }

    bool alteredInMemory() const { return (_flags & SLOTS_ALTERED); }

    bool usingUnusedFlags() const { return (_flags & UNUSED); }

    uint16_t getFlags() const { return _flags; }

    bool hasBodyContent() const;

    uint16_t getPersistedFlags() const
        { return (_flags & LEGAL_PERSISTED_SLOT_FLAGS); }

    /**
     * Returns the number of bytes required to keep this slot
     * in memory.
     */
    MemoryUsage getCacheSize() const;

    void setFlag(uint32_t flags)
        { _flags |= flags | (flags & 0xff ? CHECKSUM_OUTDATED : 0); }

    void clearFlag(uint32_t flags) { _flags &= ~flags; }

    void setLocation(DocumentPart part, DataLocation location) {
        if (part == HEADER) {
            _header = location;
        } else {
            _body = location;
        }
        _flags |= CHECKSUM_OUTDATED;
    }

    void setChecksum(uint16_t checksum)
    { _checksum = checksum; _flags &= ~CHECKSUM_OUTDATED; }

    uint16_t getChecksum() const { return _checksum; }

    void clearPersistence() {
        _header = DataLocation();
        if (_body._size > 0) {
            _body = DataLocation();
        }
        _flags |= CHECKSUM_OUTDATED;
    }

    void turnToUnrevertableRemove() {
        if (_flags & DELETED_IN_PLACE) return;
        _body = DataLocation(0, 0);
        _flags |= DELETED | DELETED_IN_PLACE;
        _flags |= ALTERED_IN_MEMORY | CHECKSUM_OUTDATED;
    }

    /**
     * Tests for equality of memfiles. Equality requires MemFile to look equal
     * for clients. It will not read data from file, so the same parts of the
     * file must be cached for objects to be equal. Non-persistent flags need
     * not be equal (The same parts need not be persisted to backend files)
     *
     * Used in unit testing only.
     */
    bool operator==(const MemSlot& other) const;
    bool operator!=(const MemSlot& other) const {
        return ! (*this == other);
    }

    // Implement print functions so we can be used similar to as we were
    // a document::Printable (Don't want inheritance in this class)
    void print(std::ostream& out, bool verbose,
               const std::string& indent) const;

    std::string toString(bool verbose = false) const;
};

std::ostream& operator<<(std::ostream& out, const MemSlot& slot);

} // memfile
} // storage

