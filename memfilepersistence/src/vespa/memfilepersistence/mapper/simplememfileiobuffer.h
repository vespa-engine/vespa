// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "buffer.h"
#include "fileinfo.h"
#include "versionserializer.h"
#include <vespa/memfilepersistence/memfile/memfileiointerface.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/objects/nbostream.h>

namespace storage {
namespace memfile {

namespace util {

/**
 * @param Alignment (template) must be a power of two.
 * @return val aligned up so that retval >= val && retval % Alignment == 0
 */
template <size_t Alignment>
size_t
alignUpPow2(const size_t val)
{
    const size_t mask = Alignment - 1;
    return (val + mask) & ~mask;
}

/**
 * Round any non-power of two value up to the nearest power of two. E.g:
 *   nextPow2(3)  -> 4
 *   nextPow2(15) -> 16
 *   nextPow2(40) -> 64
 *   nextPow2(64) -> 64
 *
 * From http://graphics.stanford.edu/~seander/bithacks.html#RoundUpPowerOf2
 */
inline uint32_t
nextPow2(uint32_t v)
{
    --v;
    v |= v >> 1;
    v |= v >> 2;
    v |= v >> 4;
    v |= v >> 8;
    v |= v >> 16;
    ++v;
    return v;
}

}

/**
 * Implements a simple buffered cache for a memfile.
 */
class SimpleMemFileIOBuffer : public MemFileIOInterface
{
public:
    /**
     * Any buffer requests >= than this size will get their own, separately
     * allocated buffer. For all other cases, we'll attempt to squeeze as many
     * documents as possible into the same (shared) buffer.
     */
    static const size_t WORKING_BUFFER_SIZE = 16*1024; // TODO(vekterli): make configurable

    class SharedBuffer
    {
    public:
        static const size_t ALLOC_ALIGNMENT = 8;
        enum Alignment {
            NO_ALIGN,
            ALIGN_512_BYTES
        };

        typedef vespalib::LinkedPtr<SharedBuffer> LP;
        explicit SharedBuffer(size_t totalSize)
            : _buf(vespalib::alloc::Alloc::allocMMap(totalSize)),
              _totalSize(totalSize),
              _usedSize(0)
        { }

        size_t getSize() const { return _totalSize; }
        size_t getUsedSize() const { return _usedSize; }
        size_t getFreeSize() const { return getSize() - getUsedSize(); }
        bool hasRoomFor(size_t sz, Alignment align = NO_ALIGN) const {
            return (align == ALIGN_512_BYTES
                    ? util::alignUpPow2<512>(_usedSize)
                    : _usedSize) + sz <= getSize();
        }

        /**
         * Returns an offset into the shared buffer which is valid to use for
         * sz bytes. If align is ALIGN_512_BYTES, the returned offset will be
         * aligned on a 512-byte boundary. It is the responsibility of the
         * caller to ensure buffers used for Direct I/O are allocated with a
         * size that is also evenly 512-byte divisible.
         */
        size_t allocate(size_t sz, Alignment align = NO_ALIGN) {
            if (align == ALIGN_512_BYTES) {
                _usedSize = util::alignUpPow2<512>(_usedSize);
            }
            assert(hasRoomFor(sz));
            size_t ret = _usedSize;
            _usedSize += util::alignUpPow2<ALLOC_ALIGNMENT>(sz);
            return ret;
        }

        char* getBuffer() {
            return static_cast<char*>(_buf.get());
        }
        const char* getBuffer() const {
            return static_cast<const char*>(_buf.get());
        }
    private:
        vespalib::alloc::Alloc _buf;
        size_t _totalSize;
        size_t _usedSize;
    };

    struct BufferAllocation
    {
        BufferAllocation() : pos(0), size(0) {}

        BufferAllocation(const SharedBuffer::LP& b, uint32_t p, uint32_t sz)
            : buf(b), pos(p), size(sz) { }

        /**
         * Get buffer area available to this specific allocation
         */
        char* getBuffer() { return buf->getBuffer() + pos; }
        const char* getBuffer() const { return buf->getBuffer() + pos; }

        /**
         * Get buffer that is (potentially) shared between many individual
         * allocations.
         */
        SharedBuffer::LP& getSharedBuffer() { return buf; }
        uint32_t getBufferPosition() const { return pos; }
        uint32_t getSize() const { return size; }

        SharedBuffer::LP buf;
        uint32_t pos;
        uint32_t size;
    };

    /**
     * Utility class for fully encoding a chunk of file data for a document
     * header in a slotfile. Supports writing header chunks with and without
     * a document payload.
     */
    class HeaderChunkEncoder
    {
        vespalib::nbostream _serializedDoc;
        vespalib::string _docId;
    public:
        static const size_t DEFAULT_STREAM_ALLOC_SIZE = 5 * 2014;

        HeaderChunkEncoder(const document::DocumentId& docId);
        ~HeaderChunkEncoder();

        /**
         * Serializes header chunk to buf, which must have at least a size
         * of encodedSize() bytes available.
         */
        void writeTo(BufferAllocation& buf) const;

        /**
         * Assign (and buffer) document that should be written to the chunk.
         * If this method is not called on an encoder prior to writeTo(), the
         * chunk will contain only a document ID but no payload. This is
         * perfectly fine for 5.1+, but is not supported by 5.0 readers.
         * It is safe for the provided document to go out of scope after having
         * called this method.
         * Since this method buffers it may only be called once per encoder.
         */
        void bufferDocument(const document::Document&);

        /**
         * Compute total size of chunk as it will reside on disk, including
         * document blob/id payload and metadata overhead.
         * Max doc size is <=64M so we cannot possibly exceed 32 bits.
         */
        uint32_t encodedSize() const {
            return (_serializedDoc.size() + trailerLength());
        }
     private:
        static constexpr uint32_t fixedTrailerLength() {
            // CRC32 of doc blob + u32 doc id length + CRC32 of doc id.
            return (sizeof(uint32_t) * 3);
        }
        uint32_t trailerLength() const {
            return (fixedTrailerLength() + _docId.size());
        }
    };

    typedef SharedBuffer BufferType;

    class PartNotCachedException : public vespalib::Exception {
    public:
        PartNotCachedException(const std::string& msg,
                               const std::string& location)
            : vespalib::Exception(msg, location) {};
    };

    SimpleMemFileIOBuffer(
            VersionSerializer& reader,
            vespalib::LazyFile::UP file,
            FileInfo::UP fileInfo,
            const FileSpecification& fileSpec,
            const Environment& env);

    virtual Document::UP getDocumentHeader(
            const document::DocumentTypeRepo& repo,
            DataLocation loc) const;

    virtual document::DocumentId getDocumentId(DataLocation loc) const;

    virtual void readBody(
            const document::DocumentTypeRepo& repo,
            DataLocation loc,
            Document& doc) const;

    virtual DataLocation addDocumentIdOnlyHeader(
            const DocumentId& id,
            const document::DocumentTypeRepo& repo);

    virtual DataLocation addHeader(const Document& doc);

    virtual DataLocation addBody(const Document& doc);

    virtual void clear(DocumentPart type);

    virtual bool verifyConsistent() const;

    /**
     * Moves the underlying file to another location.
     */
    virtual void move(const FileSpecification& target);

    virtual void close();

    virtual DataLocation copyCache(const MemFileIOInterface& source,
                                   DocumentPart part,
                                   DataLocation loc);

    /**
     * Add a location -> buffer mapping
     */
    void cacheLocation(DocumentPart part,
                       DataLocation loc,
                       BufferType::LP& buf,
                       uint32_t bufferPos);

    /**
     * @return Returns true if the given location is cached.
     */
    virtual bool isCached(DataLocation loc, DocumentPart type) const;

    /**
     * @return Returns true if the given location has been persisted to disk.
     */
    virtual bool isPersisted(DataLocation loc, DocumentPart type) const;

    virtual uint32_t getSerializedSize(DocumentPart part,
                                       DataLocation loc) const;

    virtual void ensureCached(Environment& env,
                              DocumentPart part,
                              const std::vector<DataLocation>& locations);

    /**
     * Moves the given location into the persisted data area.
     * oldLoc must be outside the persisted data area, and newLoc must be within.
     */
    void persist(DocumentPart part, DataLocation oldLoc, DataLocation newLoc);

    /**
     * Remaps every single location for the given part.
     * WARNING: All existing locations that are not remapped will be discarded!
     */
    void remapAndPersistAllLocations(DocumentPart part,
                                     const std::map<DataLocation, DataLocation>& locs);

    vespalib::LazyFile& getFileHandle() { return *_file; };
    const vespalib::LazyFile& getFileHandle() const { return *_file; };

    const FileInfo& getFileInfo() const { return *_fileInfo; }
    void setFileInfo(FileInfo::UP fileInfo) { _fileInfo = std::move(fileInfo); }

    const FileSpecification& getFileSpec() const { return _fileSpec; }

    const char* getBuffer(DataLocation loc, DocumentPart part) const;

    size_t getCachedSize(DocumentPart part) const;

    BufferAllocation allocateBuffer(DocumentPart part,
                                    uint32_t sz,
                                    SharedBuffer::Alignment align
                                    = SharedBuffer::NO_ALIGN);

    /**
     * Whether removes should be written with a document header payload in
     * order to be backwards-compatible with VDS 5.0. This is in order to
     * support a scenario where a cluster is downgraded from 5.1+ -> 5.0.
     */
    bool writeBackwardsCompatibleRemoves() const;

    /**
     * Generate a document with no content which stores the given document ID
     * and is of the type inferred by the ID. If the ID is of legacy format
     * (and thus without a type), the default configured type will be used.
     */
    Document::UP generateBlankDocument(const DocumentId&,
                                       const document::DocumentTypeRepo&) const;

private:
    struct Data {
        Data() : pos(0), persisted(false) {}

        Data(const BufferType::LP& b, uint32_t p, bool isPersisted)
            : buf(b), pos(p), persisted(isPersisted) {}

        BufferType::LP buf;
        uint32_t pos;
        bool persisted;
    };

    typedef std::map<DataLocation, Data> DataMap;

    VersionSerializer& _reader;
    std::vector<DataMap> _data;
    std::vector<SharedBuffer::LP> _workingBuffers;
    vespalib::LazyFile::UP _file;
    FileInfo::UP _fileInfo;
    FileSpecification _fileSpec;
    const Environment& _env;
    // Same memfile config is used during entire lifetime of buffer object.
    // This makes live reconfigs kick in for all files only when all buckets
    // have been evicted from the cache post-reconfig, but greatly simplifies
    // the reasoning about a given bucket in the face of such actions.
    std::shared_ptr<const Options> _options;

    DataLocation addLocation(DocumentPart part,
                             BufferAllocation newData);

    const Data& getData(DocumentPart part, DataLocation loc) const;

    BufferAllocation serializeDocumentIdOnlyHeader(
            const DocumentId& id,
            const document::DocumentTypeRepo&);
    BufferAllocation serializeHeader(const Document& doc);
    BufferAllocation serializeBody(const Document& doc);

    friend class SimpleMemFileIOBufferTest;
};

}
}


