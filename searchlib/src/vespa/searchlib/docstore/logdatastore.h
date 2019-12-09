// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "idatastore.h"
#include "lid_info.h"
#include "writeablefilechunk.h"
#include <vespa/vespalib/util/compressionconfig.h>
#include <vespa/searchcommon/common/growstrategy.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/transactionlog/syncproxy.h>
#include <vespa/vespalib/util/rcuvector.h>
#include <vespa/vespalib/util/threadexecutor.h>

#include <set>

namespace search {

namespace common { class FileHeaderContext; }


/**
 * Simple data storage for byte arrays.
 * A small integer key is associated with each byte array;
 * a zero-sized array is equivalent to a removed key.
 * Changes are held in memory until flush() is called.
 * A sync token is associated with each flush().
 **/
class LogDataStore : public IDataStore, public ISetLid, public IGetLid
{
private:
    using NameId = FileChunk::NameId;
    using FileId = FileChunk::FileId;
public:
    using NameIdSet = std::set<NameId>;
    using LockGuard = vespalib::LockGuard;
    using CompressionConfig = vespalib::compression::CompressionConfig;
    class Config {
    public:
        Config();

        Config & setMaxFileSize(size_t v) { _maxFileSize = v; return *this; }
        Config & setMaxDiskBloatFactor(double v) { _maxDiskBloatFactor = v; return *this; }
        Config & setMaxBucketSpread(double v) { _maxBucketSpread = v; return *this; }
        Config & setMinFileSizeFactor(double v) { _minFileSizeFactor = v; return *this; }

        Config & compactCompression(CompressionConfig v) { _compactCompression = v; return *this; }
        Config & setFileConfig(WriteableFileChunk::Config v) { _fileConfig = v; return *this; }

        size_t getMaxFileSize() const { return _maxFileSize; }
        double getMaxDiskBloatFactor() const { return _maxDiskBloatFactor; }
        double getMaxBucketSpread() const { return _maxBucketSpread; }
        double getMinFileSizeFactor() const { return _minFileSizeFactor; }

        bool crcOnReadDisabled() const { return _skipCrcOnRead; }
        const CompressionConfig & compactCompression() const { return _compactCompression; }

        const WriteableFileChunk::Config & getFileConfig() const { return _fileConfig; }
        Config & disableCrcOnRead(bool v) { _skipCrcOnRead = v; return *this;}

        bool operator == (const Config &) const;
    private:
        size_t                      _maxFileSize;
        double                      _maxDiskBloatFactor;
        double                      _maxBucketSpread;
        double                      _minFileSizeFactor;
        bool                        _skipCrcOnRead;
        CompressionConfig           _compactCompression;
        WriteableFileChunk::Config  _fileConfig;
    };
public:
    /**
     * Construct a log based data store.
     * All files are stored in base directory.
     *
     * @param dirName  The directory that will contain the data file.
     * @param fileHeaderContext The file header context used to populate
     *                          the generic file header with extra tags.
     *                          The caller must keep it alive for the semantic
     *                          lifetime of the log data store.
     * @param tlSyncer          Helper to sync transaction log to avoid
     *                          it being behind the document store after a 
     *                          crash.
     *                          The caller must keep it alive for the semantic
     *                          lifetime of the log data store.
     */
    LogDataStore(vespalib::ThreadExecutor &executor, const vespalib::string &dirName, const Config & config,
                 const GrowStrategy &growStrategy, const TuneFileSummary &tune,
                 const search::common::FileHeaderContext &fileHeaderContext,
                 transactionlog::SyncProxy &tlSyncer, const IBucketizer::SP & bucketizer, bool readOnly = false);

    ~LogDataStore() override;

    // Implements IDataStore API
    ssize_t read(uint32_t lid, vespalib::DataBuffer & buffer) const override;
    void read(const LidVector & lids, IBufferVisitor & visitor) const override;
    void write(uint64_t serialNum, uint32_t lid, const void * buffer, size_t len) override;
    void remove(uint64_t serialNum, uint32_t lid) override;
    void flush(uint64_t syncToken) override;
    uint64_t initFlush(uint64_t syncToken) override;
    size_t memoryUsed() const override;
    size_t memoryMeta() const override;
    uint64_t lastSyncToken() const override;
    uint64_t tentativeLastSyncToken() const override;
    vespalib::system_time getLastFlushTime() const override;
    size_t getDiskFootprint() const override;
    size_t getDiskHeaderFootprint() const override;
    size_t getDiskBloat() const override;
    size_t getMaxCompactGain() const override;

    /**
     * Will compact the docsummary up to a lower limit of 5% bloat.
     */
    void compact(uint64_t syncToken);

    const Config & getConfig() const { return _config; }
    Config & getConfig() { return _config; }

    void write(LockGuard guard, WriteableFileChunk & destination, uint64_t serialNum, uint32_t lid, const void * buffer, size_t len);
    void write(LockGuard guard, FileId destinationFileId, uint32_t lid, const void * buffer, size_t len);

    /**
     * This will spinn through the data and verify the content of both
     * the '.dat' and the '.idx' files.
     *
     * @param reportOnly If set inconsitencies will be written to 'stderr'.
     */
    void verify(bool reportOnly) const;

    /**
     * Visit all data found in data store.
     */
    void accept(IDataStoreVisitor &visitor, IDataStoreVisitorProgress &visitorProgress, bool prune) override;

    /**
     * Return cost of visiting all data found in data store.
     */
    double getVisitCost() const override;

    // Implements IGetLid API
    Guard getLidReadGuard() const override {
        return _genHandler.takeGuard();
    }

    // Implements IGetLid API
    LockGuard getLidGuard(uint32_t lid) const override {
        (void) lid;
        return LockGuard(_updateLock);
    }

    // Implements IGetLid API
    LidInfo getLid(const Guard & guard, uint32_t lid) const override {
        (void) guard;
        if (lid < getDocIdLimit()) {
            return _lidInfo[lid];
        } else {
            return LidInfo();
        }
    }
    FileId getActiveFileId(const vespalib::LockGuard & guard) const {
        assert(guard.locks(_updateLock));
        (void) guard;
        return _active;
    }

    DataStoreStorageStats getStorageStats() const override;
    vespalib::MemoryUsage getMemoryUsage() const override;
    std::vector<DataStoreFileChunkStats> getFileChunkStats() const override;

    void compactLidSpace(uint32_t wantedDocLidLimit) override;
    bool canShrinkLidSpace() const override;
    size_t getEstimatedShrinkLidSpaceGain() const override;
    void shrinkLidSpace() override;
    static NameIdSet findIncompleteCompactedFiles(const NameIdSet & partList);

    NameIdSet getAllActiveFiles() const;
    void reconfigure(const Config & config);

private:
    class WrapVisitor;
    class WrapVisitorProgress;
    class FileChunkHolder;

    // Implements ISetLid API
    void setLid(const LockGuard & guard, uint32_t lid, const LidInfo & lm) override;

    void compactWorst(double bloatLimit, double spreadLimit);
    void compactFile(FileId chunkId);

    typedef vespalib::RcuVector<uint64_t> LidInfoVector;
    typedef std::vector<FileChunk::UP> FileChunkVector;

    void updateLidMap(uint32_t lastFileChunkDocIdLimit);
    void preload();
    uint32_t getLastFileChunkDocIdLimit();
    void verifyModificationTime(const NameIdSet & partList);

    void eraseDanglingDatFiles(const NameIdSet &partList, const NameIdSet &datPartList);
    NameIdSet eraseEmptyIdxFiles(NameIdSet partList);
    NameIdSet eraseIncompleteCompactedFiles(NameIdSet partList);
    void internalFlushAll();

    NameIdSet scanDir(const vespalib::string &dir, const vespalib::string &suffix);
    FileId allocateFileId(const LockGuard & guard);
    void setNewFileChunk(const LockGuard & guard, FileChunk::UP fileChunk);
    vespalib::string ls(const NameIdSet & partList);

    WriteableFileChunk & getActive(const LockGuard & guard) {
        assert(guard.locks(_updateLock));
        (void) guard;
        return static_cast<WriteableFileChunk &>(*_fileChunks[_active.getId()]);
    }

    const WriteableFileChunk & getActive(const LockGuard & guard) const {
        assert(guard.locks(_updateLock));
        (void) guard;
        return static_cast<const WriteableFileChunk &>(*_fileChunks[_active.getId()]);
    }

    const FileChunk * getPrevActive(const LockGuard & guard) const {
        assert(guard.locks(_updateLock));
        (void) guard;
        return ( !_prevActive.isActive() ) ? _fileChunks[_prevActive.getId()].get() : nullptr;
    }
    void setActive(const LockGuard & guard, FileId fileId) {
        assert(guard.locks(_updateLock));
        (void) guard;
        _prevActive = _active;
        _active = fileId;
    }

    double getMaxBucketSpread() const;

    FileChunk::UP createReadOnlyFile(FileId fileId, NameId nameId);
    FileChunk::UP createWritableFile(FileId fileId, SerialNum serialNum);
    FileChunk::UP createWritableFile(FileId fileId, SerialNum serialNum, NameId nameId);
    vespalib::string createFileName(NameId id) const;
    vespalib::string createDatFileName(NameId id) const;
    vespalib::string createIdxFileName(NameId id) const;

    void requireSpace(LockGuard guard, WriteableFileChunk & active);
    bool isReadOnly() const { return _readOnly; }
    void updateSerialNum();

    size_t computeNumberOfSignificantBucketIdBits(const IBucketizer & bucketizer, FileId fileId) const;

    /*
     * Protect against compactWorst() dropping file chunk.  Caller must hold
     * _updateLock.
     */
    std::unique_ptr<FileChunkHolder> holdFileChunk(FileId fileId);

    /*
     * Drop protection against compactWorst() dropping file chunk.
     */
    void unholdFileChunk(FileId fileId);

    SerialNum flushFile(LockGuard guard, WriteableFileChunk & file, SerialNum syncToken);
    SerialNum flushActive(SerialNum syncToken);
    void flushActiveAndWait(SerialNum syncToken);
    void flushFileAndWait(LockGuard guard, WriteableFileChunk & file, SerialNum syncToken);
    SerialNum getMinLastPersistedSerialNum() const {
        return (_fileChunks.empty() ? 0 : _fileChunks.back()->getLastPersistedSerialNum());
    }
    bool shouldCompactToActiveFile(size_t compactedSize) const;
    std::pair<bool, FileId> findNextToCompact(double bloatLimit, double spreadLimit);
    void incGeneration();
    bool canShrinkLidSpace(const vespalib::LockGuard &guard) const;

    typedef std::vector<FileId> FileIdxVector;
    Config                                   _config;
    TuneFileSummary                          _tune;
    const search::common::FileHeaderContext &_fileHeaderContext;
    mutable vespalib::GenerationHandler      _genHandler;
    LidInfoVector                            _lidInfo;
    FileChunkVector                          _fileChunks;
    std::vector<uint32_t>                    _holdFileChunks;
    FileId                                   _active;
    FileId                                   _prevActive;
    vespalib::Lock                           _updateLock;
    bool                                     _readOnly;
    vespalib::ThreadExecutor                &_executor;
    SerialNum                                _initFlushSyncToken;
    transactionlog::SyncProxy               &_tlSyncer;
    IBucketizer::SP                          _bucketizer;
    NameIdSet                                _currentlyCompacting;
    uint64_t                                 _compactLidSpaceGeneration;
};

} // namespace search
