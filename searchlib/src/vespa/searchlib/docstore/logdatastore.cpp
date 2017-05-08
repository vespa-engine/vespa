// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storebybucket.h"
#include "compacter.h"
#include "logdatastore.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/searchlib/common/rcuvector.hpp>
#include <vespa/vespalib/util/exceptions.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docstore.logdatastore");

namespace search {

using vespalib::LockGuard;
using vespalib::getLastErrorString;
using vespalib::getErrorString;
using vespalib::GenerationHandler;
using vespalib::make_string;
using common::FileHeaderContext;
using std::runtime_error;
using document::BucketId;
using docstore::StoreByBucket;
using docstore::BucketCompacter;
using namespace std::literals;

LogDataStore::LogDataStore(vespalib::ThreadExecutor &executor,
                           const vespalib::string &dirName,
                           const Config &config,
                           const GrowStrategy &growStrategy,
                           const TuneFileSummary &tune,
                           const FileHeaderContext &fileHeaderContext,
                           transactionlog::SyncProxy &tlSyncer,
                           const IBucketizer::SP & bucketizer,
                           bool readOnly)
    : IDataStore(dirName),
      _config(config),
      _tune(tune),
      _fileHeaderContext(fileHeaderContext),
      _genHandler(),
      _lidInfo(growStrategy.getDocsInitialCapacity(),
               growStrategy.getDocsGrowPercent(),
               growStrategy.getDocsGrowDelta()),
      _fileChunks(),
      _holdFileChunks(),
      _active(0),
      _prevActive(FileId::active()),
      _readOnly(readOnly),
      _executor(executor),
      _initFlushSyncToken(0),
      _tlSyncer(tlSyncer),
      _bucketizer(bucketizer)
{
    // Reserve space for 1TB summary in order to avoid locking.
    _fileChunks.reserve(LidInfo::getFileIdLimit());
    _holdFileChunks.resize(LidInfo::getFileIdLimit());

    preload();
    updateLidMap();
    updateSerialNum();
}

void
LogDataStore::updateSerialNum()
{
    LockGuard guard(_updateLock);
    if (getPrevActive(guard) != NULL) {
        if (getActive(guard).getSerialNum() <
            getPrevActive(guard)->getLastPersistedSerialNum()) {
            getActive(guard).setSerialNum(getPrevActive(guard)->getLastPersistedSerialNum());
        }
    }
}

LogDataStore::~LogDataStore()
{
    // Must be called before ending threads as there are sanity checks.
    _fileChunks.clear();
    _executor.sync();
    _genHandler.updateFirstUsedGeneration();
    _lidInfo.removeOldGenerations(_genHandler.getFirstUsedGeneration());
}

void
LogDataStore::updateLidMap()
{
    uint64_t lastSerialNum(0);
    LockGuard guard(_updateLock);
    for (FileChunk::UP & fc : _fileChunks) {
        fc->updateLidMap(guard, *this, lastSerialNum);
        lastSerialNum = fc->getLastPersistedSerialNum();
    }
}

void
LogDataStore::read(const LidVector & lids, IBufferVisitor & visitor) const
{
    LidInfoWithLidV orderedLids;
    GenerationHandler::Guard guard(_genHandler.takeGuard());
    for (uint32_t lid : lids) {
        if (lid < _lidInfo.size()) {
            LidInfo li = _lidInfo[lid];
            if (!li.empty() && li.valid()) {
                orderedLids.emplace_back(li, lid);
            }
        }
    }
    if (orderedLids.empty()) { return; }

    std::sort(orderedLids.begin(), orderedLids.end());
    uint32_t prevFile = orderedLids[0].getFileId();
    uint32_t start = 0;
    for (size_t curr(1); curr < orderedLids.size(); curr++) {
        const LidInfoWithLid & li = orderedLids[curr];
        if (prevFile != li.getFileId()) {
            const FileChunk & fc(*_fileChunks[prevFile]);
            fc.read(orderedLids.begin() + start, curr - start, visitor);
            start = curr;
            prevFile = li.getFileId();
        }
    }
    const FileChunk & fc(*_fileChunks[prevFile]);
    fc.read(orderedLids.begin() + start, orderedLids.size() - start, visitor);
}

ssize_t
LogDataStore::read(uint32_t lid, vespalib::DataBuffer& buffer) const
{
    ssize_t sz(0);
    if (lid < _lidInfo.size()) {
        LidInfo li(0);
        {
            GenerationHandler::Guard guard(_genHandler.takeGuard());
            li = _lidInfo[lid];
        }
        if (!li.empty() && li.valid()) {
            const FileChunk & fc(*_fileChunks[li.getFileId()]);
            sz = fc.read(lid, li.getChunkId(), buffer);
        }
    }
    return sz;
}


void
LogDataStore::write(uint64_t serialNum, uint32_t lid, const void * buffer, size_t len)
{
    LockGuard guard(_updateLock);
    WriteableFileChunk & active = getActive(guard);
    write(guard, active, serialNum,  lid, buffer, len);
}

void
LogDataStore::write(LockGuard guard, FileId destinationFileId, uint32_t lid, const void * buffer, size_t len)
{
    WriteableFileChunk & destination = static_cast<WriteableFileChunk &>(*_fileChunks[destinationFileId.getId()]);
    write(guard, destination, destination.getSerialNum(), lid, buffer, len);
}

void
LogDataStore::write(LockGuard guard, WriteableFileChunk & destination,
                    uint64_t serialNum, uint32_t lid, const void * buffer, size_t len)
{
    LidInfo lm = destination.append(serialNum, lid, buffer, len);
    setLid(guard, lid, lm);
    if (destination.getFileId() == getActiveFileId(guard)) {
        requireSpace(guard, destination);
    }
}

void
LogDataStore::requireSpace(LockGuard guard, WriteableFileChunk & active)
{
    assert(active.getFileId() == getActiveFileId(guard));
    size_t oldSz(active.getDiskFootprint());
    LOG(spam, "Checking file %s size %ld < %ld",
              active.getName().c_str(), oldSz, _config.getMaxFileSize());
    if (oldSz > _config.getMaxFileSize()) {
        FileId fileId = allocateFileId(guard);
        setNewFileChunk(guard, createWritableFile(fileId, active.getSerialNum()));
        setActive(guard, fileId);
        std::unique_ptr<FileChunkHolder> activeHolder = holdFileChunk(active.getFileId());
        guard.unlock();
        // Write chunks to old .dat file 
        // Note: Feed latency spike
        active.flush(true, active.getSerialNum());
        // Sync transaction log
        _tlSyncer.sync(active.getSerialNum());
        // sync old active .dat file, write pending chunks to old .idx file
        // and sync old .idx file to disk.
        active.flushPendingChunks(active.getSerialNum());
        active.freeze();
        // TODO: Delay create of new file
        LOG(debug, "Closed file %s of size %ld due to maxsize of %ld reached. Bloat is %ld",
                   active.getName().c_str(), active.getDiskFootprint(),
                   _config.getMaxFileSize(), active.getDiskBloat());
    }
}

uint64_t
LogDataStore::lastSyncToken() const
{
    LockGuard guard(_updateLock);
    uint64_t lastSerial(getActive(guard).getLastPersistedSerialNum());
    if (lastSerial == 0) {
        const FileChunk * prev = getPrevActive(guard);
        if (prev != NULL) {
            lastSerial = prev->getLastPersistedSerialNum();
        }
    }
    return lastSerial;
}

uint64_t
LogDataStore::tentativeLastSyncToken() const
{
    LockGuard guard(_updateLock);
    return getActive(guard).getSerialNum();
}

fastos::TimeStamp
LogDataStore::getLastFlushTime() const
{
    if (lastSyncToken() == 0) {
        return fastos::TimeStamp();
    }
    LockGuard guard(_updateLock);
    fastos::TimeStamp timeStamp(getActive(guard).getModificationTime());
    if (timeStamp == 0) {
        const FileChunk * prev = getPrevActive(guard);
        if (prev != nullptr) {
            timeStamp = prev->getModificationTime();
        }
    }
    return timeStamp;
}

void
LogDataStore::remove(uint64_t serialNum, uint32_t lid)
{
    LockGuard guard(_updateLock);
    if (lid < _lidInfo.size()) {
        LidInfo lm = _lidInfo[lid];
        if (lm.valid()) {
            _fileChunks[lm.getFileId()]->remove(lid, lm.size());
        }
        lm = getActive(guard).append(serialNum, lid, NULL, 0);
        assert( lm.empty() );
        _lidInfo[lid] = lm;
    }
}

namespace {

vespalib::string bloatMsg(size_t bloat, size_t usage) {
    return make_string("Disk bloat is now at %ld of %ld at %2.2f percent", bloat, usage, (bloat*100.0)/usage);
}

}

void
LogDataStore::compact(uint64_t syncToken)
{
    uint64_t usage = getDiskFootprint();
    uint64_t bloat = getDiskBloat();
    LOG(debug, "%s", bloatMsg(bloat, usage).c_str());
    if ((_fileChunks.size() > 1) &&
           ( isBucketSpreadTooLarge(getMaxBucketSpread()) ||
             isBloatOverLimit(bloat, usage)))
    {
        LOG(info, "%s. Will compact", bloatMsg(bloat, usage).c_str());
        compactWorst();
        usage = getDiskFootprint();
        bloat = getDiskBloat();
        LOG(info, "Done compacting. %s", bloatMsg(bloat, usage).c_str());
    }

    flushActiveAndWait(syncToken);
}

size_t
LogDataStore::getMaxCompactGain() const
{
    const size_t diskFootPrint = getDiskFootprint();
    const size_t maxConfiguredDiskBloat = diskFootPrint * _config.getMaxDiskBloatFactor();
    double maxSpread = getMaxBucketSpread();
    size_t bloat = getDiskBloat();
    if (bloat < maxConfiguredDiskBloat) {
        bloat = 0;
    }
    size_t spreadAsBloat = diskFootPrint * (1.0 - 1.0/maxSpread);
    if ( ! isBucketSpreadTooLarge(maxSpread)) {
        spreadAsBloat = 0;
    }
    return (bloat + spreadAsBloat);
}

void
LogDataStore::flush(uint64_t syncToken)
{
    WriteableFileChunk * active = NULL;
    std::unique_ptr<FileChunkHolder> activeHolder;
    assert(syncToken == _initFlushSyncToken);
    {
        LockGuard guard(_updateLock);
        // Note: Feed latency spike
        getActive(guard).flush(true, syncToken);
        active = &getActive(guard);
        activeHolder = holdFileChunk(active->getFileId());
    }
    active->flushPendingChunks(syncToken);
    activeHolder.reset();
    LOG(info, "Flushing. %s",bloatMsg(getDiskBloat(), getDiskFootprint()).c_str());
}


uint64_t
LogDataStore::initFlush(uint64_t syncToken)
{
    assert(syncToken >= _initFlushSyncToken);
    syncToken = flushActive(syncToken);
    _initFlushSyncToken = syncToken;
    return syncToken;
}

double
LogDataStore::getMaxBucketSpread() const
{
    double maxSpread(1.0);
    for (const FileChunk::UP & fc : _fileChunks) {
        if (fc) {
            if (_bucketizer && fc->frozen()) {
                maxSpread = std::max(maxSpread, fc->getBucketSpread());
            }
        }
    }
    return maxSpread;
}

std::pair<bool, LogDataStore::FileId>
LogDataStore::findNextToCompact()
{
    typedef std::multimap<double, FileId, std::greater<double>> CostMap;
    CostMap worstBloat;
    CostMap worstSpread;
    LockGuard guard(_updateLock);
    for (size_t i(0); i < _fileChunks.size(); i++) {
        const FileChunk::UP & fc(_fileChunks[i]);
        if (fc && fc->frozen() && (_currentlyCompacting.find(fc->getNameId()) == _currentlyCompacting.end())) {
            uint64_t usage = fc->getDiskFootprint();
            uint64_t bloat = fc->getDiskBloat();
            if (_bucketizer) {
                worstSpread.emplace(fc->getBucketSpread(), FileId(i));
            }
            if (usage > 0) {
                double tmp(double(bloat)/usage);
                worstBloat.emplace(tmp, FileId(i));
            }
        }
    }
    if (LOG_WOULD_LOG(debug)) {
        for (const auto & it : worstBloat) {
            const FileChunk & fc = *_fileChunks[it.second.getId()];
            LOG(debug, "File '%s' has bloat '%2.2f' and bucket-spread '%1.4f numChunks=%d , numBuckets=%ld, numUniqueBuckets=%ld",
                       fc.getName().c_str(), it.first * 100, fc.getBucketSpread(), fc.getNumChunks(), fc.getNumBuckets(), fc.getNumUniqueBuckets());
        }
    }
    std::pair<bool, FileId> retval(false, FileId(-1));
    if ( ! worstBloat.empty() && (worstBloat.begin()->first > _config.getMaxDiskBloatFactor())) {
        retval.first = true;
        retval.second = worstBloat.begin()->second;
    } else if ( ! worstSpread.empty() && (worstSpread.begin()->first > _config.getMaxBucketSpread())) {
        retval.first = true;
        retval.second = worstSpread.begin()->second;
    }
    if (retval.first) {
        _currentlyCompacting.insert(_fileChunks[retval.second.getId()]->getNameId());
    }
    return retval;
}

void
LogDataStore::compactWorst() {
    auto worst = findNextToCompact();
    if (worst.first) {
        compactFile(worst.second);
    }
}

SerialNum LogDataStore::flushFile(LockGuard guard, WriteableFileChunk & file, SerialNum syncToken) {
    (void) guard;
    uint64_t lastSerial(file.getSerialNum());
    if (lastSerial > syncToken) {
        syncToken = lastSerial;
    }
    file.flush(false, syncToken);
    return syncToken;
}

void LogDataStore::flushFileAndWait(LockGuard guard, WriteableFileChunk & file, SerialNum syncToken) {
    syncToken = flushFile(guard, file, syncToken);
    file.waitForDiskToCatchUpToNow();
    _tlSyncer.sync(syncToken);
    file.flushPendingChunks(syncToken);
}

SerialNum LogDataStore::flushActive(SerialNum syncToken) {
    LockGuard guard(_updateLock);
    WriteableFileChunk &active = getActive(guard);
    return flushFile(guard, active, syncToken);
}

void LogDataStore::flushActiveAndWait(SerialNum syncToken) {
    LockGuard guard(_updateLock);
    WriteableFileChunk &active = getActive(guard);
    return flushFileAndWait(guard, active, syncToken);
}

bool LogDataStore::shouldCompactToActiveFile(size_t compactedSize) const {
    return _config.compact2ActiveFile()
           || (_config.getMinFileSizeFactor() * _config.getMaxFileSize() > compactedSize);
}

void LogDataStore::setNewFileChunk(const LockGuard & guard, FileChunk::UP file)
{
    (void) guard;
    assert(guard.locks(_updateLock));
    size_t fileId = file->getFileId().getId();
    assert( ! _fileChunks[fileId]);
    _fileChunks[fileId] = std::move(file);
}

void LogDataStore::compactFile(FileId fileId)
{
    FileChunk::UP & fc(_fileChunks[fileId.getId()]);
    NameId compactedNameId = fc->getNameId();
    LOG(info, "Compacting file '%s' which has bloat '%2.2f' and bucket-spread '%1.4f",
              fc->getName().c_str(), 100*fc->getDiskBloat()/double(fc->getDiskFootprint()), fc->getBucketSpread());
    IWriteData::UP compacter;
    FileId destinationFileId = FileId::active();
    if (_bucketizer) {
        if ( ! shouldCompactToActiveFile(fc->getDiskFootprint() - fc->getDiskBloat())) {
            LockGuard guard(_updateLock);
            destinationFileId = allocateFileId(guard);
            setNewFileChunk(guard, createWritableFile(destinationFileId, fc->getLastPersistedSerialNum(), fc->getNameId().next()));
        }
        size_t numSignificantBucketBits = computeNumberOfSignificantBucketIdBits(*_bucketizer, fc->getFileId());
        compacter.reset(new BucketCompacter(numSignificantBucketBits, _config.compactCompression(), *this, _executor,
                                            *_bucketizer, fc->getFileId(), destinationFileId));
    } else {
        compacter.reset(new docstore::Compacter(*this));
    }

    fc->appendTo(*this, *compacter, fc->getNumChunks(), nullptr);

    if (destinationFileId.isActive()) {
        flushActiveAndWait(0);
    } else {
        LockGuard guard(_updateLock);
        WriteableFileChunk & compactTo = dynamic_cast<WriteableFileChunk &>(*_fileChunks[destinationFileId.getId()]);
        flushFileAndWait(guard, compactTo, 0);
        compactTo.freeze();
    }
    compacter.reset();

    std::this_thread::sleep_for(1s);
    uint64_t currentGeneration;
    {
        LockGuard guard(_updateLock);
        currentGeneration = _genHandler.getCurrentGeneration();
        _genHandler.incGeneration();
    }
    
    FileChunk::UP toDie;
    for (;;) {
        LockGuard guard(_updateLock);
        _genHandler.updateFirstUsedGeneration();
        if (currentGeneration < _genHandler.getFirstUsedGeneration()) {
            if (_holdFileChunks[fc->getFileId().getId()] == 0u) {
                toDie = std::move(fc);
                break;
            }
        }
        guard.unlock();
        /*
         * Wait for requireSpace() and flush() methods to leave chunk
         * alone.
         */
        std::this_thread::sleep_for(1s);;
    }
    toDie->erase();
    LockGuard guard(_updateLock);
    _currentlyCompacting.erase(compactedNameId);
}

size_t
LogDataStore::memoryUsed() const
{
    size_t sz(memoryMeta());
    {
        LockGuard guard(_updateLock);
        for (const FileChunk::UP & fc : _fileChunks) {
            if (fc) {
                sz += fc->getMemoryFootprint();
            }
        }
    }
    return sz;
}

size_t
LogDataStore::memoryMeta() const
{
    LockGuard guard(_updateLock);
    size_t sz(_lidInfo.getMemoryUsage().allocatedBytes());
    for (const FileChunk::UP & fc : _fileChunks) {
        if (fc) {
            sz += fc->getMemoryMetaFootprint();
        }
    }
    return sz;
}

FileChunk::FileId
LogDataStore::allocateFileId(const LockGuard & guard)
{
    (void) guard;
    for (size_t i(0); i < _fileChunks.size(); i++) {
        if (_fileChunks[i].get() == nullptr) {
            return FileId(i);
        }
    }
    // This assert is verify that we have not gotten ourselves into a mess
    // that would require the use of locks to prevent. Just assure that the 
    // below resize is 'safe'.
    assert(_fileChunks.capacity() > _fileChunks.size());
    _fileChunks.resize(_fileChunks.size()+1);
    return FileId(_fileChunks.size() - 1);
}

size_t
LogDataStore::getDiskFootprint() const
{
    LockGuard guard(_updateLock);
    size_t sz(0);
    for (const FileChunk::UP & fc : _fileChunks) {
        if (fc) {
            sz += fc->getDiskFootprint();
        }
    }
    return sz;
}


size_t
LogDataStore::getDiskHeaderFootprint(void) const
{
    LockGuard guard(_updateLock);
    size_t sz(0);
    for (const FileChunk::UP & fc : _fileChunks) {
        if (fc) {
            sz += fc->getDiskHeaderFootprint();
        }
    }
    return sz;
}


size_t
LogDataStore::getDiskBloat() const
{
    LockGuard guard(_updateLock);
    size_t sz(0);
    for (FileId i(0); i < FileId(_fileChunks.size()); i = i.next()) {
        /// Do not count the holes in the last file as bloat
        if (i != _active) {
            const FileChunk * chunk = _fileChunks[i.getId()].get();
            if (chunk != NULL) {
                sz += chunk->getDiskBloat();
            }
        }
    }
    return sz;
}

vespalib::string
LogDataStore::createFileName(NameId id) const {
    return id.createName(getBaseDir());
}
vespalib::string
LogDataStore::createDatFileName(NameId id) const {
    return FileChunk::createDatFileName(id.createName(getBaseDir()));
}

vespalib::string
LogDataStore::createIdxFileName(NameId id) const {
    return FileChunk::createIdxFileName(id.createName(getBaseDir()));
}

FileChunk::UP
LogDataStore::createReadOnlyFile(FileId fileId, NameId nameId) {
    FileChunk::UP file(new FileChunk(fileId, nameId, getBaseDir(), _tune,
                                     _bucketizer.get(), _config.crcOnReadDisabled()));
    file->enableRead();
    return file;
}

FileChunk::UP
LogDataStore::createWritableFile(FileId fileId, SerialNum serialNum, NameId nameId)
{
    for (const auto & fc : _fileChunks) {
        if (fc && (fc->getNameId() == nameId)) {
            LOG(error, "We already have a file registered with internal fileId=%u, and external nameId=%ld",
                       fileId.getId(), nameId.getId());
            return FileChunk::UP();
        }
    }
    FileChunk::UP file(new WriteableFileChunk(_executor, fileId, nameId, getBaseDir(),
                                              serialNum, std::numeric_limits<uint32_t>::max(),
                                              _config.getFileConfig(), _tune, _fileHeaderContext,
                                              _bucketizer.get(), _config.crcOnReadDisabled()));
    file->enableRead();
    return file;
}

FileChunk::UP
LogDataStore::createWritableFile(FileId fileId, SerialNum serialNum)
{
    return createWritableFile(fileId, serialNum, NameId(fastos::ClockSystem::now()));
}

namespace {

vespalib::string
lsSingleFile(const vespalib::string & fileName)
{
    vespalib::string s;
    FastOS_StatInfo stat;
    if ( FastOS_File::Stat(fileName.c_str(), &stat)) {
        s += make_string("%s  %20ld  %12ld", fileName.c_str(), stat._modifiedTimeNS, stat._size);
    } else {
        s = make_string("%s 'stat' FAILED !!", fileName.c_str());
    }
    return s;
}

}

vespalib::string LogDataStore::ls(const NameIdSet & partList)
{
    vespalib::string s;
    for (auto it(++partList.begin()), mt(partList.end()); it != mt; ++it) {
        s += lsSingleFile(createDatFileName(*it));
        s += "\n";
        s += lsSingleFile(createIdxFileName(*it));
    }
    return s;
}

static bool
hasNonHeaderData(const vespalib::string &name)
{
    FastOS_File file(name.c_str());
    if (!file.OpenReadOnly())
        return false;
    int64_t fSize(file.GetSize());
    uint32_t headerLen = 0;
    uint32_t minHeaderLen = vespalib::GenericHeader::getMinSize();
    if (fSize < minHeaderLen)
        return false;
    try {
        vespalib::FileHeader h;
        headerLen = h.readFile(file);
    } catch (vespalib::IllegalHeaderException &e) {
        file.SetPosition(0);
        try {
            vespalib::FileHeader::FileReader fr(file);
            uint32_t header2Len = vespalib::FileHeader::readSize(fr);
            if (header2Len <= fSize) {
                e.throwSelf(); // header not truncated
            }
        } catch (vespalib::IllegalHeaderException &e2) {
        }
        return false;
    }
    return fSize > headerLen;
}

void
LogDataStore::verifyModificationTime(const NameIdSet & partList)
{
    FastOS_StatInfo prevDatStat;
    FastOS_StatInfo prevIdxStat;
    NameId nameId(*partList.begin());
    vespalib::string datName(createDatFileName(nameId));
    vespalib::string idxName(createIdxFileName(nameId));
    if ( ! FastOS_File::Stat(datName.c_str(), &prevDatStat)) {
        throw runtime_error(make_string("Failed to Stat '%s'\nDirectory =\n%s", datName.c_str(), ls(partList).c_str()));
    }
    if ( ! FastOS_File::Stat(idxName.c_str(), &prevIdxStat)) {
        throw runtime_error(make_string("Failed to Stat '%s'\nDirectory =\n%s", idxName.c_str(), ls(partList).c_str()));
    }
    for (auto it(++partList.begin()), mt(partList.end()); it != mt; ++it) {
        vespalib::string prevDatNam(datName);
        vespalib::string prevIdxNam(idxName);
        FastOS_StatInfo datStat;
        FastOS_StatInfo idxStat;
        nameId = *it;
        datName = createDatFileName(nameId);
        idxName = createIdxFileName(nameId);
        if ( ! FastOS_File::Stat(datName.c_str(), &datStat)) {
            throw runtime_error(make_string("Failed to Stat '%s'\nDirectory =\n%s", datName.c_str(), ls(partList).c_str()));
        }
        if ( ! FastOS_File::Stat(idxName.c_str(), &idxStat)) {
            throw runtime_error(make_string("Failed to Stat '%s'\nDirectory =\n%s", idxName.c_str(), ls(partList).c_str()));
        }
        ns_log::Logger::LogLevel logLevel = _config.compact2ActiveFile()
                                            ? ns_log::Logger::warning
                                            : ns_log::Logger::debug;
        if ((datStat._modifiedTimeNS < prevDatStat._modifiedTimeNS) && hasNonHeaderData(datName)) {
            VLOG(logLevel, "Older file '%s' is newer (%ld) than file '%s' (%ld)\nDirectory =\n%s",
                         prevDatNam.c_str(), prevDatStat._modifiedTimeNS,
                         datName.c_str(), datStat._modifiedTimeNS,
                         ls(partList).c_str());
        }
        if ((idxStat._modifiedTimeNS < prevIdxStat._modifiedTimeNS) && hasNonHeaderData(idxName)) {
            VLOG(logLevel, "Older file '%s' is newer (%ld) than file '%s' (%ld)\nDirectory =\n%s",
                         prevIdxNam.c_str(), prevIdxStat._modifiedTimeNS,
                         idxName.c_str(), idxStat._modifiedTimeNS,
                         ls(partList).c_str());
        }
        prevDatStat = datStat;
        prevIdxStat = idxStat;
    }
}

void
LogDataStore::preload()
{
    // scan directory
    NameIdSet partList = scanDir(getBaseDir(), ".idx");
    NameIdSet datPartList = scanDir(getBaseDir(), ".dat");

    partList = eraseEmptyIdxFiles(partList);
    eraseDanglingDatFiles(partList, datPartList);

    if (!partList.empty()) {
        verifyModificationTime(partList);
        partList = scanDir(getBaseDir(), ".idx");
        typedef NameIdSet::const_iterator It;
        for (It it(partList.begin()), mt(--partList.end()); it != mt; it++) {
            _fileChunks.push_back(createReadOnlyFile(FileId(_fileChunks.size()), *it));
        }
        _fileChunks.push_back(isReadOnly()
            ? createReadOnlyFile(FileId(_fileChunks.size()), *partList.rbegin())
            : createWritableFile(FileId(_fileChunks.size()), getMinLastPersistedSerialNum(), *partList.rbegin()));
    } else {
        if ( ! isReadOnly() ) {
            _fileChunks.push_back(createWritableFile(FileId::first(), 0));
        } else {
            throw vespalib::IllegalArgumentException(getBaseDir() + " does not have any summary data... And that is no good in readonly case.");
        }
    }
    _active = FileId(_fileChunks.size() - 1);
    _prevActive = _active.prev();
}

LogDataStore::NameIdSet
LogDataStore::eraseEmptyIdxFiles(const NameIdSet &partList)
{
    NameIdSet nonEmptyIdxPartList;
    for (const auto & part : partList) {
        vespalib::string name(createFileName(part));
        if (FileChunk::isIdxFileEmpty(name)) {
            LOG(warning, "We detected an empty idx file for part '%s'. Erasing it.", name.c_str());
            FileChunk::eraseIdxFile(name);
        } else {
            nonEmptyIdxPartList.insert(part);
        }
    }
    return nonEmptyIdxPartList;
}

void
LogDataStore::eraseDanglingDatFiles(const NameIdSet &partList, const NameIdSet &datPartList)
{
    typedef NameIdSet::const_iterator IT;
    
    IT iib(partList.begin());
    IT ii(iib);
    IT iie(partList.end());
    IT dib(datPartList.begin());
    IT di(dib);
    IT die(datPartList.end());
    IT dirb(die);
    NameId endMarker(NameId::last());
    
    if (dirb != dib) {
        --dirb;
    }
    for (;;) {
        if (ii == iie && di == die) {
            break;
        }
        NameId ibase(ii == iie ? endMarker : *ii);
        NameId dbase(di == die ? endMarker : *di);
        if (ibase < dbase) {
            vespalib::string name(createFileName(ibase));
            const char *s = name.c_str();
            throw runtime_error(make_string( "Missing file '%s.dat', found '%s.idx'", s, s));
        } else if (dbase < ibase) {
            vespalib::string name(createDatFileName(dbase));
            const char *s = name.c_str();
            LOG(warning, "Removing dangling file '%s'", s);
            if (!FastOS_File::Delete(s)) {
                vespalib::string e = getErrorString(errno);
                throw runtime_error(make_string("Error erasing dangling file '%s'. Error is '%s'", s, e.c_str()));
            }
            ++di;
        } else {
            ++ii;
            ++di;
        }
    }
}

LogDataStore::NameIdSet
LogDataStore::scanDir(const vespalib::string &dir, const vespalib::string &suffix)
{
    NameIdSet baseFiles;
    FastOS_DirectoryScan dirScan(dir.c_str());
    while (dirScan.ReadNext()) {
        if (dirScan.IsRegular()) {
            vespalib::stringref file(dirScan.GetName());
            if (file.size() > suffix.size() &&
                file.find(suffix.c_str()) == file.size() - suffix.size()) {
                vespalib::string base(file.substr(0, file.find(suffix.c_str())));
                char *err(NULL);
                errno = 0;
                NameId baseId(strtoul(base.c_str(), &err, 10));
                if ((errno == 0) && (err[0] == '\0')) {
                    vespalib::string tmpFull = createFileName(baseId);
                    vespalib::string tmp = tmpFull.substr(tmpFull.rfind('/') + 1);
                    assert(tmp == base);
                    baseFiles.insert(baseId);
                } else {
                    throw runtime_error(make_string("Error converting '%s' to a unsigned integer number. Error occurred at '%s'. Error is '%s'",
                                                    base.c_str(), err, getLastErrorString().c_str()));
                }
            } else {
                LOG(debug, "Skipping '%s' since it does not end with '%s'", file.c_str(), suffix.c_str());
            }
        }
    }
    return baseFiles;
}

void
LogDataStore::setLid(const LockGuard & guard, uint32_t lid, const LidInfo & meta)
{
    (void) guard;
    if (lid < _lidInfo.size()) {
        _genHandler.updateFirstUsedGeneration();
        _lidInfo.removeOldGenerations(_genHandler.getFirstUsedGeneration());
        const LidInfo & prev = _lidInfo[lid];
        if (prev.valid()) {
            _fileChunks[prev.getFileId()]->remove(lid, prev.size());
        }
    } else {
        _lidInfo.ensure_size(lid+1, LidInfo());
        _lidInfo.setGeneration(_genHandler.getNextGeneration());
        _genHandler.incGeneration();
        _genHandler.updateFirstUsedGeneration();
        _lidInfo.removeOldGenerations(_genHandler.getFirstUsedGeneration());
        setNextId(_lidInfo.size());
    }
    _lidInfo[lid] = meta;
}

size_t
LogDataStore::computeNumberOfSignificantBucketIdBits(const IBucketizer & bucketizer, FileId fileId) const
{
    vespalib::BenchmarkTimer timer(1.0);
    size_t msbHistogram[64];
    memset(msbHistogram, 0, sizeof(msbHistogram));
    timer.before();
    auto bucketizerGuard = bucketizer.getGuard();
    GenerationHandler::Guard lidGuard(_genHandler.takeGuard());
    for (size_t i(0), m(_lidInfo.size()); i < m; i++) {
        LidInfo lid(_lidInfo[i]);
        if (lid.valid() && (lid.getFileId() == fileId.getId())) {
            BucketId bucketId = bucketizer.getBucketOf(bucketizerGuard, i);
            size_t msbCount = vespalib::Optimized::msbIdx(bucketId.toKey());
            msbHistogram[msbCount]++;
        }
    }
    timer.after();
    if (LOG_WOULD_LOG(debug)) {
        for (size_t i(0); i < 64; i++) {
            LOG(info, "msbCount[%ld] = %ld", i, msbHistogram[i]);
        }
    }
    size_t msb(64);
    while ((msb > 0) && (msbHistogram[msb - 1] == 0)) {
        msb--;
    }
    LOG(info, "computeNumberOfSignificantBucketIdBits(file=%d) = %ld = %ld took %1.3f", fileId.getId(), msb, msbHistogram[msb-1], timer.min_time());
    return msb;
}

void
LogDataStore::verify(bool reportOnly) const
{
    for (const FileChunk::UP & fc : _fileChunks) {
        if (fc) {
            fc->verify(reportOnly);
        }
    }
}

class LogDataStore::WrapVisitor : public IWriteData
{
    IDataStoreVisitor &_visitor;
    
public:
    void write(LockGuard guard, uint32_t chunkId, uint32_t lid, const void *buffer, size_t sz) override {
        (void) chunkId;
        guard.unlock();
        _visitor.visit(lid, buffer, sz);
    }

    WrapVisitor(IDataStoreVisitor &visitor) : _visitor(visitor) { }
    void close() override { }
};

class LogDataStore::WrapVisitorProgress : public IFileChunkVisitorProgress
{
    IDataStoreVisitorProgress &_progress;
    const uint32_t _totalChunks;
    uint32_t _processedChunks;

public:
    void
    updateProgress() override
    {
        ++_processedChunks;
        if (_totalChunks != 0) {
            double progress = std::min(static_cast<double>(_processedChunks) /
                                       static_cast<double>(_totalChunks),
                                       1.0);
            _progress.updateProgress(progress);
        }
    };

    WrapVisitorProgress(IDataStoreVisitorProgress &progress,
                        uint32_t totalChunks)
        : _progress(progress),
          _totalChunks(totalChunks),
          _processedChunks(0u)
    {
        if (totalChunks == 0) {
            progress.updateProgress(1.0);
        }
    }
};

void
LogDataStore::internalFlushAll()
{
    uint64_t flushToken(initFlush(tentativeLastSyncToken()));
    _tlSyncer.sync(flushToken);
    flush(flushToken);
}

void
LogDataStore::accept(IDataStoreVisitor &visitor,
                     IDataStoreVisitorProgress &visitorProgress,
                     bool prune)
{
    WrapVisitor wrap(visitor);
    internalFlushAll();
    FileIdxVector fileChunks;
    fileChunks.reserve(_fileChunks.size());
    for (auto &fc : _fileChunks) {
        if (fc && (fc->getFileId() != _active)) {
            fileChunks.push_back(fc->getFileId());
        }
    }
    FileChunk & lfc = *_fileChunks[_active.getId()];

    uint32_t totalChunks = 0;
    for (auto &fc : fileChunks) {
        totalChunks += _fileChunks[fc.getId()]->getNumChunks();
    }
    uint32_t lastChunks = lfc.getNumChunks();
    totalChunks += lastChunks;
    WrapVisitorProgress wrapProgress(visitorProgress, totalChunks);
    for (FileId fcId : fileChunks) {
        FileChunk & fc = *_fileChunks[fcId.getId()];
        fc.appendTo(*this, wrap, fc.getNumChunks(), &wrapProgress);
        if (prune) {
            internalFlushAll();
            FileChunk::UP toDie;
            {
                LockGuard guard(_updateLock);
                toDie = std::move(_fileChunks[fcId.getId()]);
            }
            toDie->erase();
        }
    }
    lfc.appendTo(*this, wrap, lastChunks, &wrapProgress);
    if (prune) {
        internalFlushAll();
    }
}

double
LogDataStore::getVisitCost() const
{
    uint32_t totalChunks = 0;
    for (auto &fc : _fileChunks) {
        totalChunks += fc->getNumChunks();
    }
    return totalChunks;
}

class LogDataStore::FileChunkHolder
{
private:
    LogDataStore &_store;
    FileId _fileId;
public:
    FileChunkHolder(LogDataStore &store, FileId fileId) : _store(store), _fileId(fileId) { }
    ~FileChunkHolder() { _store.unholdFileChunk(_fileId); }
};

std::unique_ptr<LogDataStore::FileChunkHolder>
LogDataStore::holdFileChunk(FileId fileId)
{
    assert(fileId.getId() < _holdFileChunks.size());
    assert(_holdFileChunks[fileId.getId()] < 2000u);
    ++_holdFileChunks[fileId.getId()];
    return std::make_unique<FileChunkHolder>(*this, fileId);
}

void
LogDataStore::unholdFileChunk(FileId fileId)
{
    LockGuard guard(_updateLock);
    assert(fileId.getId() < _holdFileChunks.size());
    assert(_holdFileChunks[fileId.getId()] > 0u);
    --_holdFileChunks[fileId.getId()];
    // No signalling, compactWorst() sleeps and retries
}

DataStoreStorageStats
LogDataStore::getStorageStats() const
{
    uint64_t diskFootprint = getDiskFootprint();
    uint64_t diskBloat = getDiskBloat();
    double maxBucketSpread = getMaxBucketSpread();
    // Note: Naming consistency issue
    SerialNum lastSerialNum = tentativeLastSyncToken();
    SerialNum lastFlushedSerialNum = lastSyncToken();
    return DataStoreStorageStats(diskFootprint, diskBloat, maxBucketSpread,
                                 lastSerialNum, lastFlushedSerialNum);
}

MemoryUsage
LogDataStore::getMemoryUsage() const
{
    LockGuard guard(_updateLock);
    MemoryUsage result;
    result.merge(_lidInfo.getMemoryUsage());
    for (const auto &fileChunk : _fileChunks) {
        if (fileChunk) {
            result.merge(fileChunk->getMemoryUsage());
        }
    }
    return result;
}

std::vector<DataStoreFileChunkStats>
LogDataStore::getFileChunkStats() const
{
    std::vector<DataStoreFileChunkStats> result;
    {
        LockGuard guard(_updateLock);
        for (const FileChunk::UP & fc : _fileChunks) {
            if (fc) {
                result.push_back(fc->getStats());
            }
        }
    }
    std::sort(result.begin(), result.end());
    return std::move(result);
}

void
LogDataStore::compactLidSpace(uint32_t wantedDocLidLimit)
{
    (void) wantedDocLidLimit;
}

bool
LogDataStore::canShrinkLidSpace() const
{
    return false;
}

void
LogDataStore::shrinkLidSpace()
{
}

} // namespace search
