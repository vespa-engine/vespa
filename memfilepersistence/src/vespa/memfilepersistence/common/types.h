// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::slotfile::Types
 * \ingroup memfile
 *
 * \brief This class defines and includes some types used in the slotfile layer.
 *
 * As many of the types are used many places in the layer, we define them here
 * rather than in one random class using them. This also makes it easy to switch
 * implementation by switching out which class to use here.
 *
 * This class should not have any members, virtual classes or anything. We don't
 * want it to add to the memory footprint of classes, as it will be used also
 * by classes kept many times in memory cache.
 */
#pragma once


#include <vespa/storageframework/storageframework.h>
#include <vespa/persistence/spi/bucketinfo.h>
#include <vespa/document/fieldvalue/document.h>

namespace storage {
namespace memfile {

/**
 * \class storage::slotfile::DataLocation
 * \ingroup memfile
 *
 * \brief Points to data in a file storing documents.
 *
 * This file stores info on where header and body parts of document are stored.
 * It is really format specific data, but for now it is implemented globally.
 *
 * All unused locations should be size zero pointing to address zero. A size
 * of zero with a non-zero position is invalid, and used to indicate that this
 * value is not set yet. (Typically when data isn't persisted to disk yet)
 */
struct DataLocation {
    uint32_t _pos;
    uint32_t _size;

    DataLocation() : _pos(1), _size(0) {} // pos 1 size 0 is invalid value.
    DataLocation(uint32_t pos, uint32_t sz) : _pos(pos), _size(sz) {}

    uint32_t size() const { return _size; }

    uint32_t endPos() const { return _pos + _size; }

    bool valid() const { return (_size > 0 || _pos == 0); }

    bool operator==(const DataLocation& other) const {
        return (_pos == other._pos && _size == other._size);
    }
    bool operator!=(const DataLocation& other) const {
        return ! (*this == other);
    }

    bool operator<(const DataLocation& other) const {
        if (_pos == other._pos) {
            return _size < other._size;
        }

        return _pos < other._pos;
    }

    bool contains(const DataLocation& other) const {
        return (_pos <= other._pos && _pos + _size >= other._pos + other._size);
    }
};

std::ostream& operator<<(std::ostream&, const DataLocation&);

struct Types {
    typedef document::BucketId BucketId;
    typedef document::Document Document;
    typedef document::DocumentId DocumentId;
    typedef document::GlobalId GlobalId;
    typedef framework::MicroSecTime Timestamp;
    typedef Timestamp RevertToken;
    typedef vespalib::string String;
    typedef spi::BucketInfo BucketInfo;

    static const framework::MicroSecTime MAX_TIMESTAMP;
    static const framework::MicroSecTime UNSET_TIMESTAMP;

    enum FileVersion {
        UNKNOWN              = 0,
        TRADITIONAL_SLOTFILE = 0xABCD0001
    };

    enum SlotFlag {
        IN_USE                     = 0x01,
        DELETED                    = 0x02,
        DELETED_IN_PLACE           = 0x04,
        LEGAL_PERSISTED_SLOT_FLAGS = 0x07,

        // States not stored in file. As we have set aside 16 bits for the
        // flags in the fileformat, but use so few, we use some of the
        // unused bits in the memory representation to store memory state.
        ALTERED_IN_MEMORY          = 0x02 << 8,
        CHECKSUM_OUTDATED          = 0x04 << 8,

        // Masks to check for multiple bits
        UNUSED                     = 0xf8f8
    };

    enum GetFlag {
        ALL             = 0,
        HEADER_ONLY     = 0x1,
        LEGAL_GET_FLAGS = 0x1
    };

    enum IteratorFlag {
        ITERATE_GID_UNIQUE   = 0x1,
        ITERATE_REMOVED      = 0x2,
        LEGAL_ITERATOR_FLAGS = 0x3
    };

    enum DocContentFlag {
        HAS_HEADER_ONLY,
        HAS_BODY
    };

    enum DocumentPart {
        HEADER,
        BODY
    };

    enum MemFileFlag {
        FILE_EXIST           = 0x0001,
        HEADER_BLOCK_READ    = 0x0002,
        BODY_BLOCK_READ      = 0x0004,
        BUCKET_INFO_OUTDATED = 0x0008,
        SLOTS_ALTERED        = 0x0010,
        LEGAL_MEMFILE_FLAGS  = 0x001f
    };

    enum FileVerifyFlags {
        DONT_VERIFY_HEADER = 0x0001,
        DONT_VERIFY_BODY   = 0x0002,
        LEGAL_VERIFY_FLAGS = 0x0003
    };

    enum FlushFlag {
        NONE = 0,
        CHECK_NON_DIRTY_FILE_FOR_SPACE = 1
    };

    enum GetLocationsFlag {
        NON_PERSISTED_LOCATIONS = 0x0001,
        PERSISTED_LOCATIONS = 0x0002,
        NO_SLOT_LIST = 0x0004
    };

    enum DocumentCopyType {
        DEEP_COPY,
        SHALLOW_COPY
    };

    static const char* getDocumentPartName(DocumentPart part) {
        switch (part) {
            case HEADER: return "Header";
            case BODY: return "Body";
            default: return "Invalid";
        }
    }

    static const char* getFileVersionName(FileVersion version) {
        switch (version) {
            case UNKNOWN: return "UNKNOWN";
            case TRADITIONAL_SLOTFILE: return "TRADITIONAL_SLOTFILE";
            default: return "INVALID";
        }
    }

    static const char* getMemFileFlagName(MemFileFlag flag) {
        switch (flag) {
            case FILE_EXIST: return "FILE_EXIST";
            case HEADER_BLOCK_READ: return "HEADER_BLOCK_READ";
            case BODY_BLOCK_READ: return "BODY_BLOCK_READ";
            case BUCKET_INFO_OUTDATED: return "BUCKET_INFO_OUTDATED";
            case SLOTS_ALTERED: return "SLOTS_ALTERED";
            case LEGAL_MEMFILE_FLAGS: assert(false); // Not a single flag
            default: return "INVALID";
        }
    }

    static void verifyLegalFlags(uint32_t flags, uint32_t legal,
                                 const char* operation);

protected:
    ~Types() {} // Noone should refer to objects as Types objects
};

} // memfile
} // storage

