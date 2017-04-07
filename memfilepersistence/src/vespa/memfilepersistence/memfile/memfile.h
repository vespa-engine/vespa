// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::MemFile
 * \ingroup memfile
 *
 * \brief Class representing a file storing documents in slots.
 *
 * This is a memory representation of the file, that isn't constricted by the
 * actual file format.
 *
 * A memfile must contains:
 *   - A header with generic information about the file, including version of
 *     file format.
 *
 * A memfile may also contain:
 *   - Cached meta data describing contents.
 *   - Cached document header content.
 *   - Cached document body content.
 *
 * The representation knows what parts of it that are persisted in a file, and
 * what parts exist only in memory.
 *
 * For ease of use, information is loaded into the cache automatically by the
 * MemFile implementation. Thus, the memfile needs a pointer to the file mapper
 * implementation.
 */

#pragma once

#include "memslot.h"
#include "slotiterator.h"
#include "memfileiointerface.h"
#include <vespa/memfilepersistence/common/filespecification.h>
#include <vespa/memfilepersistence/common/types.h>
#include <vespa/vespalib/io/fileutil.h>

namespace storage {
namespace memfile {

class Environment;
//class SlotFileV1SerializerTest;

class MemFile : private Types,
                public vespalib::Printable
{
public:
    struct FormatSpecificData {
        typedef std::unique_ptr<FormatSpecificData> UP;

        virtual ~FormatSpecificData() {}

        virtual std::string toString() const = 0;
    };

    typedef IteratorWrapper const_iterator;

    struct LocationContent {
        std::vector<const MemSlot*> slots;
    };
    typedef std::map<DataLocation, LocationContent> LocationMap;
    typedef std::vector<MemSlot> MemSlotVector;

private:
    void verifyDiskNotFull();

    mutable uint32_t _flags;
    mutable BucketInfo _info;
    MemFileIOInterface::UP _buffer;
    MemSlotVector _entries;
    FileSpecification _file;
    mutable FileVersion _currentVersion;
    Environment& _env;
    mutable FormatSpecificData::UP _formatData;
    MemSlot::MemoryUsage _cacheSizeOverride;

    friend class MemFilePtr;
    friend class MemCacheTest;
    class MemFileBufferCacheCopier;

    /**
     * Verify tests need to be able to create memfiles that hasn't called
     * loadfile, and possibly call loadFile without autorepair set. Such
     * memfiles are invalid as many functions require header+metadata to be
     * cached. Should only be used for unit tests.
     */
    friend class MemFileV1VerifierTest;
    MemFile(const FileSpecification&, Environment&, bool callLoadFile);

    // Ensures that all entries are cached.
    // If includeBody is true, caches the body as well.
    void ensureEntriesCached(bool includeBody) const;

    // Put the given location in the result map if the
    // location is persisted according to the given flags.
    void matchLocationWithFlags(LocationMap& result,
                                DocumentPart part,
                                const MemSlot* slot,
                                uint32_t flags) const;

public:
    struct LoadOptions {
        bool autoRepair;
        LoadOptions() : autoRepair(true)
        {}
    };

    MemFile(const FileSpecification& spec,
            Environment& env,
            const LoadOptions& opts = LoadOptions());

    const FileSpecification& getFile() const { return _file; }
    const document::BucketId& getBucketId() const noexcept {
        return _file.getBucketId();
    }
    FileVersion getCurrentVersion() const { return _currentVersion; }

    bool empty() const { return _entries.empty(); }
    bool fileExists() const { return (_flags & FILE_EXIST); }
    bool headerBlockCached() const { return (_flags & HEADER_BLOCK_READ); }
    bool bodyBlockCached() const { return (_flags & BODY_BLOCK_READ); }
    bool slotsAltered() const { return _flags & SLOTS_ALTERED; }

    /**
     * Called by the mapper when it has to call loadFile a second
     * time due to corruption repairs. Must NOT be called by anyone
     * else!
     */
    void resetMetaState();

    void verifyConsistent() const;

    /** Moves the physical file on disk (if any) to the new file name. */
    void move(const FileSpecification& newFileName);

    uint16_t getDisk() const;

    FormatSpecificData* getFormatSpecificData() const
        { return _formatData.get(); }
    void setFormatSpecificData(FormatSpecificData::UP d) const
        { _formatData = std::move(d); }
    void setCurrentVersion(FileVersion ver) const { _currentVersion = ver; }

    uint32_t getSlotCount() const;
    const MemSlot& operator[](uint32_t index) const { return _entries[index]; }
    const MemSlot* getSlotWithId(const document::DocumentId&,
                                 Timestamp maxTimestamp = MAX_TIMESTAMP) const;
    const MemSlot* getSlotAtTime(Timestamp) const;

    void getSlotsByTimestamp(const std::vector<Timestamp>&,
                             std::vector<const MemSlot*>& returned) const;

    // Get flags are defined in types.h (GetFlag)
    Document::UP getDocument(const MemSlot& slot, GetFlag getFlag) const;

    document::DocumentId getDocumentId(const MemSlot& slot) const;

    /**
     * Returns the number of bytes required by this memfile while
     * in cache.
     *
     * @return Returns the cache size.
     */
    MemSlot::MemoryUsage getCacheSize() const;

    void addPutSlot(const Document& doc, Timestamp time);

    void addUpdateSlot(const Document& header,
                       const MemSlot& body,
                       Timestamp time);

    void addRemoveSlot(const MemSlot& header, Timestamp time);

    enum RemoveType
    {
        REGULAR_REMOVE,
        UNREVERTABLE_REMOVE
    };

    void addRemoveSlotForNonExistingEntry(const DocumentId& docId,
                                          Timestamp time,
                                          RemoveType removeType);

    void addSlot(const MemSlot&);
    void removeSlot(const MemSlot&);

    void setMemFileIO(MemFileIOInterface::UP buffer) {
        _buffer = std::move(buffer);
    }
    MemFileIOInterface& getMemFileIO() { return *_buffer; }
    const MemFileIOInterface& getMemFileIO() const { return *_buffer; }

    void getLocations(LocationMap& headers,
                      LocationMap& bodies,
                      uint32_t flags) const;

    /**
     * Copies a slot from another memfile.
     */
    void copySlot(const MemFile& source, const MemSlot&);

    void copySlotsFrom(const MemFile& source,
                       const std::vector<const MemSlot*>& sourceSlots);

    /** Remove given slots. Slots must exist and be in rising timestamp order */
    void removeSlots(const std::vector<const MemSlot*>&);
    void modifySlot(const MemSlot&);

    void setFlag(uint32_t flags) {
        verifyLegalFlags(flags, LEGAL_MEMFILE_FLAGS, "MemFile::setFlag");
        _flags |= flags;
    }

    void clearFlag(uint32_t flags) {
        verifyLegalFlags(flags, LEGAL_MEMFILE_FLAGS, "MemFile::clearFlags");
        _flags &= ~flags;
    }

    /**
     * Removes entries overwritten after revert time period and remove
     * entries older than keep remove period.
     *
     * @return True if anything was compacted
     */
    bool compact();

    const_iterator begin(uint32_t iteratorFlags = 0,
                         Timestamp fromTimestamp = UNSET_TIMESTAMP,
                         Timestamp toTimestamp = UNSET_TIMESTAMP) const;

    const_iterator end() const { return const_iterator(); }

    void ensureDocumentIdCached(const MemSlot&) const;
    void ensureDocumentCached(const MemSlot&, bool headerOnly) const;
    void ensureHeaderBlockCached() const;
    void ensureBodyBlockCached() const;
    void ensureHeaderAndBodyBlocksCached() const;
    void ensureDocumentCached(const std::vector<Timestamp>&,
                              bool headerOnly) const;

    /**
     * Assert that a given slot is contained in the bucket this MemFile has
     * been created for (i.e. output of getBucketId()). In the common case,
     * only the slot GID will be consulted, but in the case of orderdoc docs
     * the document ID may have to be fetched.
     *
     * Precondition: `slot` must have its data blocks already added to the
     *   file's buffer cache. This means any fetches of the document ID should
     *   not require disk access, but will incur cache lookup and heap
     *   allocation overhead.
     * Postcondition: no side effects if `slot` is contained in bucket. Logs
     *   error and dumps core otherwise.
     */
    void assertSlotContainedInThisBucket(const MemSlot& slot) const;

    bool documentIdAvailable(const MemSlot&) const;
    bool partAvailable(const MemSlot&, DocumentPart part) const;
    bool partPersisted(const MemSlot&, DocumentPart) const;

    uint32_t getSerializedSize(const MemSlot&, DocumentPart part) const;

    /**
     * Fetches the bucket info. If metadata is altered, info will be
     * recalculated, and bucket database updated.
     */
    const BucketInfo& getBucketInfo() const;

    void flushToDisk(FlushFlag flags = NONE);

    void clearCache(DocumentPart part);

    /**
     * Repair any errors found in this slotfile.
     * If given, stuff error report into given ostream.
     *
     * @return True if file was fine. False if any errors were repaired.
     */
    bool repair(std::ostream& errorReport, uint32_t fileVerifyFlags = 0);

    /**
     * Tests for equality of memfiles. Equality requires MemFile to look equal
     * for clients. It will not read data from file, so the same parts of the
     * file must be cached for objects to be equal. Non-persistent flags need
     * not be equal (The same parts need not be persisted to backend files)
     *
     * Used in unit testing only.
     */
    bool operator==(const MemFile& other) const;

    /** Stat wants control of printing of slots. */
    void printHeader(std::ostream& out, bool verbose,
                     const std::string& indent) const;
    void printEntries(std::ostream& out, bool verbose,
                      const std::string& indent) const;
    void printEntriesState(std::ostream& out, bool verbose,
                           const std::string& indent) const;
    void print(std::ostream& out, bool verbose,
               const std::string& indent) const override;

    /** Stat wants control of printing of slots. */
    void printUserFriendly(const MemSlot& slot,
                           std::ostream& out,
                           const std::string& indent) const;
    void print(const MemSlot& slot,
               std::ostream& out, bool verbose,
               const std::string& indent) const;

    /** Debug function to print state. */
    void printState(std::ostream& out, bool userFriendlyOutput = false,
                    bool printBody = true, bool printHeader = true,
                    //MetaDataOrder order = DEFAULT,
                    const std::string& indent = "") const;
};

} // memfile
} // storage

