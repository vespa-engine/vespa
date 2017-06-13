// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class vdslib::DocumentList
 * @ingroup messageapi
 *
 * @brief A utility class for a buffer containing a list of documents.
 *
 * During visiting and subscriptions, one or more documents need to be sent
 * to clients. Both documents added and removed. For performance reasons, we
 * might need to bundle multiple entries together in a buffer, and for some
 * extreme performance requirements, we might need to use shared memory to send
 * this data to a process running on the same computer.
 *
 * The format is as follows.  The first 4 bytes contain the number of meta
 * entries in the block. After this, the list of meta entries is. Each entry
 * is the memory representation of a MetaEntry object. After this list, a
 * generic block with header and body blocks exist. The meta entry points to
 * the data they use. Meta entry pointers are indexes starting from the start
 * of the docblock.
 *
 */

#pragma once

#include <vespa/vdslib/defs.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/repo/documenttyperepo.h>

namespace document {
    class DocumentUpdate;
}
namespace vdslib {

class DocumentList : public document::Printable {
public:

    struct MetaEntry {
        enum Flag {
            REMOVE_ENTRY   = 1,
            BODY_STRIPPED  = 2,
            BODY_IN_HEADER = 4,
            UPDATE_ENTRY   = 8,
            COMPRESSED     = 16
        };
        Timestamp timestamp;
        uint32_t headerPos;
        uint32_t headerLen;
        uint32_t bodyPos;
        uint32_t bodyLen;
        uint8_t  flags;
        uint8_t  padding[7]; // In order to align equally in 32bit and 64bit

        MetaEntry();
        void print(std::ostream& out, const std::string& indent = "") const;
    };

    class Entry : public document::Printable {
    private:
        MetaEntry* _metaEntry;
        char*      _start;
        uint32_t   _entry;
        const document::DocumentTypeRepo *_repo;

    public:
        Entry() : _metaEntry(0), _start(0), _entry(0), _repo(0) {}
        Entry(char* start, uint32_t entry,
              const document::DocumentTypeRepo &repo);

        Entry(const Entry &rhs)
            : document::Printable(rhs),
              _metaEntry(rhs._metaEntry),
              _start(rhs._start),
              _entry(rhs._entry),
              _repo(rhs._repo)
        { }

        Entry& operator=(const Entry &rhs)
        {
            document::Printable::operator=(rhs);
            _metaEntry = rhs._metaEntry;
            _start = rhs._start;
            _entry = rhs._entry;
            _repo = rhs._repo;
            return *this;
        }

        Entry next() const;

        /** Entries in iterators gotten from DocumentList::end() are invalid. */
        bool valid() const { return (_start != 0); }
        bool isRemoveEntry() const { return _metaEntry->flags & MetaEntry::REMOVE_ENTRY; }
        bool isBodyStripped() const { return _metaEntry->flags & MetaEntry::BODY_STRIPPED; }
        bool isUpdateEntry() const { return _metaEntry->flags & MetaEntry::UPDATE_ENTRY; }

        uint8_t getFlags() const { return _metaEntry->flags; }
        Timestamp getTimestamp() const { return _metaEntry->timestamp; }
        void setTimestamp(Timestamp t) const { _metaEntry->timestamp = t; }

        document::DocumentId getDocumentId() const;
        document::Document::UP getDocument(const document::DocumentType *anticipatedType = 0) const;
        std::unique_ptr<document::DocumentUpdate> getUpdate() const;
    public:
        bool getUpdate(document::DocumentUpdate&) const;

        typedef std::pair<char*, uint32_t> BufferPosition;
        /**
         * Get the raw header of the document; note that in case
         * BODY_IN_HEADER is set, this also includes the body.
         */
        const BufferPosition getRawHeader() const;
        /**
         * Get the raw body of the document; note that in case BODY_IN_HEADER
         * is set, this should not be used.
         */
        const BufferPosition getRawBody() const;
        uint32_t getSerializedSize() const {
            return _metaEntry->headerLen + _metaEntry->bodyLen
                    + sizeof(MetaEntry);
        }

        void print(std::ostream& out, bool verbose, const std::string& indent) const override;
        bool operator==(const Entry& e) const { return (_start == e._start && _entry == e._entry); }
    };

    class const_iterator {
        Entry _entry;

    public:
        typedef std::input_iterator_tag iterator_category;
        typedef Entry value_type;
        typedef uint32_t difference_type;
        typedef const Entry* pointer;
        typedef const Entry& reference;

        const_iterator(const Entry& e) : _entry(e) {}

        const Entry& operator*() { return _entry; }
        const Entry* operator->() { return &_entry; }
        const_iterator& operator++(); // Prefix
        const_iterator operator++(int); // Postfix
        bool operator==(const const_iterator& it) const;
        bool operator!=(const const_iterator& it) const;
    };

    /**
     * Create a new documentlist, using the given buffer.
     * @param keepexisting If set to true, assume buffer is already filled.
     */
    DocumentList(const document::DocumentTypeRepo::SP & repo, char* buffer,
                 uint32_t bufferSize, bool keepexisting = false);

    DocumentList(const DocumentList& source, char* buffer, uint32_t bufferSize);

    virtual ~DocumentList();
    DocumentList& operator=(const DocumentList &rhs);
    DocumentList(const DocumentList &rhs);

    /** return number of bytes free space (in the middle of the buffer) */
    uint32_t countFree() const {
        return (_freePtr - _buffer) - sizeof(uint32_t) - sizeof(MetaEntry) * size();
    }

    void clear() { docCount() = 0; _freePtr = _buffer + _bufferSize; }

    uint32_t getBufferSize() const { return _bufferSize; }
    char* getBuffer() { return _buffer; }
    const char* getBuffer() const { return _buffer; }

    const_iterator begin() const {
        return const_iterator(Entry((_freePtr < _buffer + _bufferSize ? _buffer : 0), 0, *_repo));
    }
    const_iterator end() const { return const_iterator(Entry()); }
    uint32_t size() const { return (_buffer == 0 ? 0 : docCount()); }

    /** compute minimum number of bytes needed to hold the current documentlist */
    uint64_t spaceNeeded() const {
        uint32_t n = docCount();
        uint64_t need = sizeof(uint32_t);
        for (uint32_t i = 0; i < n; ++i) {
            const MetaEntry& entry(getMeta(i));
            need += sizeof(MetaEntry) + entry.headerLen + entry.bodyLen;
        }
        return need;
    }

    void checkConsistency(bool do_memset = false);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    const document::DocumentTypeRepo::SP & getTypeRepo() const { return _repo; }

protected:
    char     *_buffer;
    uint32_t  _bufferSize;
    uint32_t  _wasted;
    char     *_freePtr;

    uint32_t docCount() const { return *reinterpret_cast<uint32_t*>(_buffer); }

    uint32_t& docCount() { return *reinterpret_cast<uint32_t*>(_buffer); }
    const MetaEntry& getMeta(uint32_t index) const {
        return *reinterpret_cast<MetaEntry*>(_buffer + sizeof(uint32_t) + index * sizeof(MetaEntry));
    }
    MetaEntry& getMeta(uint32_t index) {
        return *reinterpret_cast<MetaEntry*>(_buffer + sizeof(uint32_t) + index * sizeof(MetaEntry));
    }
private:
    void init(bool keepExisting);
    document::DocumentTypeRepo::SP _repo;
};

std::ostream& operator<<(std::ostream& out, const DocumentList::MetaEntry& e);

}

