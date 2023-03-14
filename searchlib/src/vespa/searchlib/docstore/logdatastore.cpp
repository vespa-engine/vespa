// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "logdatastore.h"
#include "storebybucket.h"
#include "compacter.h"
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/size_literals.h>
#include <thread>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docstore.logdatastore");

namespace search {

namespace {
    constexpr size_t DEFAULT_MAX_FILESIZE = 1000000000ul;
    constexpr uint32_t DEFAULT_MAX_LIDS_PER_FILE = 32_Mi;
}

using common::FileHeaderContext;
using docstore::BucketCompacter;
using docstore::StoreByBucket;
using document::BucketId;
using namespace std::literals;
using std::runtime_error;
using vespalib::CpuUsage;
using vespalib::GenerationHandler;
using vespalib::IllegalStateException;
using vespalib::getErrorString;
using vespalib::getLastErrorString;
using vespalib::make_string;
using vespalib::to_string;

using CpuCategory = CpuUsage::Category;

LogDataStore::Config::Config()
    : _maxFileSize(DEFAULT_MAX_FILESIZE),
      _maxBucketSpread(2.5),
      _minFileSizeFactor(0.2),
      _maxNumLids(DEFAULT_MAX_LIDS_PER_FILE),
      _compactCompression(CompressionConfig::LZ4),
      _fileConfig()
{ }

bool
LogDataStore::Config::operator == (const Config & rhs) const {
    return (_maxBucketSpread == rhs._maxBucketSpread) &&
            (_maxFileSize == rhs._maxFileSize) &&
            (_minFileSizeFactor == rhs._minFileSizeFactor) &&
            (_compactCompression == rhs._compactCompression) &&
            (_fileConfig == rhs._fileConfig);
}

LogDataStore::LogDataStore(vespalib::Executor &executor, const vespalib::string &dirName, const Config &config,
                           const GrowStrategy &growStrategy, const TuneFileSummary &tune,
                           const FileHeaderContext &fileHeaderContext, transactionlog::SyncProxy &tlSyncer,
                           IBucketizer::SP bucketizer, bool readOnly)
    : IDataStore(dirName),
      _config(config),
      _tune(tune),
      _fileHeaderContext(fileHeaderContext),
      _genHandler(),
      _lidInfo(growStrategy),
      _fileChunks(),
      _holdFileChunks(),
      _active(0),
      _prevActive(FileId::active()),
      _readOnly(readOnly),
      _executor(executor),
      _initFlushSyncToken(0),
      _tlSyncer(tlSyncer),
      _bucketizer(std::move(bucketizer)),
      _currentlyCompacting(),
      _compactLidSpaceGeneration()
{
    // Reserve space for 1TB summary in order to avoid locking.
    // Even if we have reserved 16 bits for file id there is no chance that we will even get close to that.
    // Size of files grows with disk size, so 8k files should be more than sufficient.
    // File ids are reused so there should be no chance of running empty.
    static_assert(LidInfo::getFileIdLimit() == 65536u);
    _fileChunks.reserve(8_Ki);

    preload();
    updateLidMap(getLastFileChunkDocIdLimit());
    updateSerialNum();
}

void LogDataStore::reconfigure(const Config & config) {
    _config = config;
}

void
LogDataStore::updateSerialNum()
{
    std::unique_lock guard(_updateLock);
    if (getPrevActive(guard) != nullptr) {
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
    _genHandler.update_oldest_used_generation();
    _lidInfo.reclaim_memory(_genHandler.get_oldest_used_generation());
}

void
LogDataStore::updateLidMap(uint32_t lastFileChunkDocIdLimit)
{
    uint64_t lastSerialNum(0);
    std::unique_lock guard(_updateLock);
    for (size_t i = 0; i < _fileChunks.size(); ++i) {
        FileChunk::UP &chunk = _fileChunks[i];
        bool lastChunk = ((i + 1) == _fileChunks.size());
        uint32_t docIdLimit = lastChunk ? std::numeric_limits<uint32_t>::max() : lastFileChunkDocIdLimit;
        chunk->updateLidMap(guard, *this, lastSerialNum, docIdLimit);
        lastSerialNum = chunk->getLastPersistedSerialNum();
    }
}

void
LogDataStore::read(const LidVector & lids, IBufferVisitor & visitor) const
{
    LidInfoWithLidV orderedLids;
    GenerationHandler::Guard guard(_genHandler.takeGuard());
    for (uint32_t lid : lids) {
        if (lid < getDocIdLimit()) {
            LidInfo li = vespalib::atomic::load_ref_acquire(_lidInfo.acquire_elem_ref(lid));
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
    if (lid < getDocIdLimit()) {
        LidInfo li(0);
        {
            GenerationHandler::Guard guard(_genHandler.takeGuard());
            li = vespalib::atomic::load_ref_acquire(_lidInfo.acquire_elem_ref(lid));
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
    std::unique_lock guard(_updateLock);
    WriteableFileChunk & active = getActive(guard);
    write(std::move(guard), active, serialNum,  lid, buffer, len, CpuCategory::WRITE);
}

void
LogDataStore::write(MonitorGuard guard, FileId destinationFileId, uint32_t lid, const void * buffer, size_t len)
{
    auto & destination = static_cast<WriteableFileChunk &>(*_fileChunks[destinationFileId.getId()]);
    write(std::move(guard), destination, destination.getSerialNum(), lid, buffer, len, CpuCategory::COMPACT);
}

void
LogDataStore::write(MonitorGuard guard, WriteableFileChunk & destination,
                    uint64_t serialNum, uint32_t lid, const void * buffer, size_t len,
                    CpuUsage::Category cpu_category)
{
    LidInfo lm = destination.append(serialNum, lid, buffer, len, cpu_category);
    setLid(guard, lid, lm);
    if (destination.getFileId() == getActiveFileId(guard)) {
        requireSpace(std::move(guard), destination, cpu_category);
    }
}

void
LogDataStore::requireSpace(MonitorGuard guard, WriteableFileChunk & active, CpuUsage::Category cpu_category)
{
    assert(active.getFileId() == getActiveFileId(guard));
    size_t oldSz(active.getDiskFootprint());
    LOG(spam, "Checking file %s size %ld < %ld AND #lids %u < %u",
              active.getName().c_str(), oldSz, _config.getMaxFileSize(), active.getNumLids(), _config.getMaxNumLids());
    if ((oldSz > _config.getMaxFileSize()) || (active.getNumLids() >= _config.getMaxNumLids())) {
        FileId fileId = allocateFileId(guard);
        setNewFileChunk(guard, createWritableFile(fileId, active.getSerialNum()));
        setActive(guard, fileId);
        std::unique_ptr<FileChunkHolder> activeHolder = holdFileChunk(guard, active.getFileId());
        guard.unlock();
        // Write chunks to old .dat file 
        // Note: Feed latency spike
        active.flush(true, active.getSerialNum(), cpu_category);
        // Sync transaction log
        _tlSyncer.sync(active.getSerialNum());
        // sync old active .dat file, write pending chunks to old .idx file
        // and sync old .idx file to disk.
        active.flushPendingChunks(active.getSerialNum());
        active.freeze(cpu_category);
        // TODO: Delay create of new file
        LOG(debug, "Closed file %s of size %ld and %u lids due to maxsize of %ld or maxlids %u reached. Bloat is %ld",
                   active.getName().c_str(), active.getDiskFootprint(), active.getNumLids(),
                   _config.getMaxFileSize(), _config.getMaxNumLids(), active.getDiskBloat());
    }
}

uint64_t
LogDataStore::lastSyncToken() const
{
    MonitorGuard guard(_updateLock);
    uint64_t lastSerial(getActive(guard).getLastPersistedSerialNum());
    if (lastSerial == 0) {
        const FileChunk * prev = getPrevActive(guard);
        if (prev != nullptr) {
            lastSerial = prev->getLastPersistedSerialNum();
        }
    }
    return lastSerial;
}

uint64_t
LogDataStore::tentativeLastSyncToken() const
{
    MonitorGuard guard(_updateLock);
    return getActive(guard).getSerialNum();
}

vespalib::system_time
LogDataStore::getLastFlushTime() const
{
    if (lastSyncToken() == 0) {
        return vespalib::system_time();
    }
    MonitorGuard guard(_updateLock);
    vespalib::system_time timeStamp(getActive(guard).getModificationTime());
    if (timeStamp == vespalib::system_time()) {
        const FileChunk * prev = getPrevActive(guard);
        if (prev != nullptr) {
            timeStamp = prev->getModificationTime();
        }
    }
    // TODO Needs to change when we decide on Flush time reference
    return timeStamp;
}

void
LogDataStore::remove(uint64_t serialNum, uint32_t lid)
{
    MonitorGuard guard(_updateLock);
    if (lid < getDocIdLimit()) {
        LidInfo lm = vespalib::atomic::load_ref_relaxed(_lidInfo[lid]);
        if (lm.valid()) {
            _fileChunks[lm.getFileId()]->remove(lid, lm.size());
        }
        lm = getActive(guard).append(serialNum, lid, nullptr, 0, CpuCategory::WRITE);
        assert( lm.empty() );
        vespalib::atomic::store_ref_release(_lidInfo[lid], lm);
    }
}

namespace {

vespalib::string bloatMsg(size_t bloat, size_t usage) {
    return make_string("Disk bloat is now at %ld of %ld at %2.2f percent", bloat, usage, (bloat*100.0)/usage);
}

}

size_t
LogDataStore::getMaxSpreadAsBloat() const
{
    const size_t diskFootPrint = getDiskFootprint();
    const double maxSpread = getMaxBucketSpread();
    return (maxSpread > _config.getMaxBucketSpread())
        ? diskFootPrint * (1.0 - 1.0/maxSpread)
        : 0;
}

void
LogDataStore::flush(uint64_t syncToken)
{
    WriteableFileChunk * active = nullptr;
    std::unique_ptr<FileChunkHolder> activeHolder;
    assert(syncToken == _initFlushSyncToken);
    {
        MonitorGuard guard(_updateLock);
        // Note: Feed latency spike
        // This is executed by an IFlushTarget,
        // but is a fundamental part of the WRITE pipeline of the data store.
        getActive(guard).flush(true, syncToken, CpuCategory::WRITE);
        active = &getActive(guard);
        activeHolder = holdFileChunk(guard, active->getFileId());
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

std::pair<bool, LogDataStore::FileId>
LogDataStore::findNextToCompact(bool dueToBloat)
{
    using CostMap = std::multimap<double, FileId, std::greater<double>>;
    CostMap worst;
    MonitorGuard guard(_updateLock);
    for (size_t i(0); i < _fileChunks.size(); i++) {
        const auto & fc(_fileChunks[i]);
        if (fc && fc->frozen() && (_currentlyCompacting.find(fc->getNameId()) == _currentlyCompacting.end())) {
            uint64_t usage = fc->getDiskFootprint();
            if ( ! dueToBloat && _bucketizer) {
                worst.emplace(fc->getBucketSpread(), FileId(i));
            } else if (dueToBloat && usage > 0) {
                double tmp(double(fc->getDiskBloat())/usage);
                worst.emplace(tmp, FileId(i));
            }
        }
    }
    if (LOG_WOULD_LOG(debug)) {
        for (const auto & it : worst) {
            const FileChunk & fc = *_fileChunks[it.second.getId()];
            LOG(debug, "File '%s' has bloat '%2.2f' and bucket-spread '%1.4f numChunks=%d , numBuckets=%ld, numUniqueBuckets=%ld",
                       fc.getName().c_str(), it.first * 100, fc.getBucketSpread(), fc.getNumChunks(), fc.getNumBuckets(), fc.getNumUniqueBuckets());
        }
    }
    std::pair<bool, FileId> retval(false, FileId(-1));
    if ( ! worst.empty()) {
        retval.first = true;
        retval.second = worst.begin()->second;
    }
    if (retval.first) {
        _currentlyCompacting.insert(_fileChunks[retval.second.getId()]->getNameId());
    }
    return retval;
}

void
LogDataStore::compactWorst(uint64_t syncToken, bool compactDiskBloat) {
    uint64_t usage = getDiskFootprint();
    uint64_t bloat = getDiskBloat();
    const char * reason = compactDiskBloat ? "bloat" : "spread";
    LOG(debug, "%s", bloatMsg(bloat, usage).c_str());
    const bool doCompact = (_fileChunks.size() > 1);
    if (doCompact) {
        LOG(debug, "Will compact due to %s: %s", reason, bloatMsg(bloat, usage).c_str());
        auto worst = findNextToCompact(compactDiskBloat);
        if (worst.first) {
            compactFile(worst.second);
        }
        flushActiveAndWait(syncToken);
        usage = getDiskFootprint();
        bloat = getDiskBloat();
        LOG(info, "Done compacting due to %s: %s", reason, bloatMsg(bloat, usage).c_str());
    } else {
        flushActiveAndWait(syncToken);
    }
}

SerialNum LogDataStore::flushFile(MonitorGuard guard, WriteableFileChunk & file, SerialNum syncToken,
                                  CpuUsage::Category cpu_category)
{
    (void) guard;
    uint64_t lastSerial(file.getSerialNum());
    if (lastSerial > syncToken) {
        syncToken = lastSerial;
    }
    file.flush(false, syncToken, cpu_category);
    return syncToken;
}

void LogDataStore::flushFileAndWait(MonitorGuard guard, WriteableFileChunk & file, SerialNum syncToken) {
    // This function is always called in the context of compaction.
    syncToken = flushFile(std::move(guard), file, syncToken, CpuCategory::COMPACT);
    file.waitForDiskToCatchUpToNow();
    _tlSyncer.sync(syncToken);
    file.flushPendingChunks(syncToken);
}

SerialNum LogDataStore::flushActive(SerialNum syncToken) {
    MonitorGuard guard(_updateLock);
    WriteableFileChunk &active = getActive(guard);
    // This is executed by an IFlushTarget (via initFlush),
    // but is a fundamental part of the WRITE pipeline of the data store.
    return flushFile(std::move(guard), active, syncToken, CpuCategory::WRITE);
}

void LogDataStore::flushActiveAndWait(SerialNum syncToken) {
    MonitorGuard guard(_updateLock);
    WriteableFileChunk &active = getActive(guard);
    return flushFileAndWait(std::move(guard), active, syncToken);
}

bool LogDataStore::shouldCompactToActiveFile(size_t compactedSize) const {
    return (_config.getMinFileSizeFactor() * _config.getMaxFileSize() > compactedSize);
}

void LogDataStore::setNewFileChunk(const MonitorGuard & guard, FileChunk::UP file)
{
    assert(hasUpdateLock(guard));
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
        size_t disk_footprint = fc->getDiskFootprint();
        size_t disk_bloat = fc->getDiskBloat();
        size_t compacted_size = (disk_footprint <= disk_bloat) ? 0u : (disk_footprint - disk_bloat);
        if ( ! shouldCompactToActiveFile(compacted_size)) {
            MonitorGuard guard(_updateLock);
            destinationFileId = allocateFileId(guard);
            setNewFileChunk(guard, createWritableFile(destinationFileId, fc->getLastPersistedSerialNum(), fc->getNameId().next()));
        }
        size_t numSignificantBucketBits = computeNumberOfSignificantBucketIdBits(*_bucketizer, fc->getFileId());
        compacter = std::make_unique<BucketCompacter>(numSignificantBucketBits, _config.compactCompression(), *this, _executor,
                                            *_bucketizer, fc->getFileId(), destinationFileId);
    } else {
        compacter = std::make_unique<docstore::Compacter>(*this);
    }

    fc->appendTo(_executor, *this, *compacter, fc->getNumChunks(), nullptr, CpuCategory::COMPACT);

    flushActiveAndWait(0);
    if (!destinationFileId.isActive()) {
        MonitorGuard guard(_updateLock);
        auto & compactTo = dynamic_cast<WriteableFileChunk &>(*_fileChunks[destinationFileId.getId()]);
        flushFileAndWait(std::move(guard), compactTo, 0);
        compactTo.freeze(CpuCategory::COMPACT);
    }
    compacter.reset();

    std::this_thread::sleep_for(1s);
    uint64_t currentGeneration;
    {
        MonitorGuard guard(_updateLock);
        currentGeneration = _genHandler.getCurrentGeneration();
        _genHandler.incGeneration();
    }
    
    FileChunk::UP toDie;
    for (;;) {
        MonitorGuard guard(_updateLock);
        _genHandler.update_oldest_used_generation();
        if (currentGeneration < _genHandler.get_oldest_used_generation()) {
            if (canFileChunkBeDropped(guard, fc->getFileId())) {
                toDie = std::move(fc);
                break;
            }
        }
        guard.unlock();
        /*
         * Wait for requireSpace() and flush() methods to leave chunk
         * alone.
         */
        std::this_thread::sleep_for(1s);
    }
    toDie->erase();
    MonitorGuard guard(_updateLock);
    _currentlyCompacting.erase(compactedNameId);
}

size_t
LogDataStore::memoryUsed() const
{
    size_t sz(memoryMeta());
    {
        MonitorGuard guard(_updateLock);
        for (const auto & fc : _fileChunks) {
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
    MonitorGuard guard(_updateLock);
    size_t sz(_lidInfo.getMemoryUsage().allocatedBytes());
    for (const auto & fc : _fileChunks) {
        if (fc) {
            sz += fc->getMemoryMetaFootprint();
        }
    }
    return sz;
}

FileChunk::FileId
LogDataStore::allocateFileId(const MonitorGuard & guard)
{
    assert(guard.owns_lock());
    for (size_t i(0); i < _fileChunks.size(); i++) {
        if ( ! _fileChunks[i] ) {
            return FileId(i);
        }
    }
    // This assert is to verify that we have not gotten ourselves into a mess
    // that would require the use of locks to prevent. Just assure that the 
    // below resize is 'safe'.
    assert(_fileChunks.capacity() > _fileChunks.size());
    _fileChunks.resize(_fileChunks.size()+1);
    return FileId(_fileChunks.size() - 1);
}

size_t
LogDataStore::getDiskFootprint() const
{
    MonitorGuard guard(_updateLock);
    size_t sz(0);
    for (const auto & fc : _fileChunks) {
        if (fc) {
            sz += fc->getDiskFootprint();
        }
    }
    return sz;
}


size_t
LogDataStore::getDiskHeaderFootprint() const
{
    MonitorGuard guard(_updateLock);
    size_t sz(0);
    for (const auto & fc : _fileChunks) {
        if (fc) {
            sz += fc->getDiskHeaderFootprint();
        }
    }
    return sz;
}

double
LogDataStore::getMaxBucketSpread() const
{
    double maxSpread(1.0);
    MonitorGuard guard(_updateLock);
    for (FileId i(0); i < FileId(_fileChunks.size()); i = i.next()) {
        /// Ignore the the active file as it is never considered for reordering until completed and frozen.
        if (i != _active) {
            const auto & fc = _fileChunks[i.getId()];
            if (fc && _bucketizer && fc->frozen()) {
                maxSpread = std::max(maxSpread, fc->getBucketSpread());
            }
        }
    }
    return maxSpread;
}

size_t
LogDataStore::getDiskBloat() const
{
    MonitorGuard guard(_updateLock);
    size_t sz(0);
    for (FileId i(0); i < FileId(_fileChunks.size()); i = i.next()) {
        /// Do not count the holes in the last file as bloat as it is
        /// never considered for compaction until completed and frozen.
        if (i != _active) {
            const auto & chunk = _fileChunks[i.getId()];
            if (chunk) {
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
    auto file = std::make_unique<FileChunk>(fileId, nameId, getBaseDir(), _tune,
                                            _bucketizer.get());
    file->enableRead();
    return file;
}

FileChunk::UP
LogDataStore::createWritableFile(FileId fileId, SerialNum serialNum, NameId nameId)
{
    for (const auto & fc : _fileChunks) {
        if (fc && (fc->getNameId() == nameId)) {
            LOG(error, "We already have a file registered with internal fileId=%u, and external nameId=%" PRIu64,
                       fileId.getId(), nameId.getId());
            return FileChunk::UP();
        }
    }
    uint32_t docIdLimit = (getDocIdLimit() != 0) ? getDocIdLimit() : std::numeric_limits<uint32_t>::max();
    auto file = std::make_unique< WriteableFileChunk>(_executor, fileId, nameId, getBaseDir(), serialNum,docIdLimit,
                                                      _config.getFileConfig(), _tune, _fileHeaderContext,
                                                      _bucketizer.get());
    file->enableRead();
    return file;
}

FileChunk::UP
LogDataStore::createWritableFile(FileId fileId, SerialNum serialNum)
{
    return createWritableFile(fileId, serialNum, NameId(vespalib::system_clock::now().time_since_epoch().count()));
}

namespace {

vespalib::string
lsSingleFile(const vespalib::string & fileName)
{
    vespalib::string s;
    FastOS_StatInfo stat;
    if ( FastOS_File::Stat(fileName.c_str(), &stat)) {
        s += make_string("%s  %20" PRIu64 "  %12" PRId64, fileName.c_str(), vespalib::count_ns(stat._modifiedTime.time_since_epoch()), stat._size);
    } else {
        s = make_string("%s 'stat' FAILED !!", fileName.c_str());
    }
    return s;
}

}

vespalib::string
LogDataStore::ls(const NameIdSet & partList)
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
        ns_log::Logger::LogLevel logLevel = ns_log::Logger::debug;
        if ((datStat._modifiedTime < prevDatStat._modifiedTime) && hasNonHeaderData(datName)) {
            VLOG(logLevel, "Older file '%s' is newer (%s) than file '%s' (%s)\nDirectory =\n%s",
                         prevDatNam.c_str(), to_string(prevDatStat._modifiedTime).c_str(),
                         datName.c_str(), to_string(datStat._modifiedTime).c_str(),
                         ls(partList).c_str());
        }
        if ((idxStat._modifiedTime < prevIdxStat._modifiedTime) && hasNonHeaderData(idxName)) {
            VLOG(logLevel, "Older file '%s' is newer (%s) than file '%s' (%s)\nDirectory =\n%s",
                         prevIdxNam.c_str(), to_string(prevIdxStat._modifiedTime).c_str(),
                         idxName.c_str(), to_string(idxStat._modifiedTime).c_str(),
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

    partList = eraseEmptyIdxFiles(std::move(partList));
    eraseDanglingDatFiles(partList, datPartList);
    partList = eraseIncompleteCompactedFiles(std::move(partList));

    if (!partList.empty()) {
        verifyModificationTime(partList);
        partList = scanDir(getBaseDir(), ".idx");
        using It = NameIdSet::const_iterator;
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

uint32_t
LogDataStore::getLastFileChunkDocIdLimit()
{
    if (!_fileChunks.empty()) {
        return _fileChunks.back()->getDocIdLimit();
    }
    return std::numeric_limits<uint32_t>::max();
}

LogDataStore::NameIdSet
LogDataStore::eraseEmptyIdxFiles(NameIdSet partList)
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

LogDataStore::NameIdSet
LogDataStore::findIncompleteCompactedFiles(const NameIdSet & partList) {
    NameIdSet incomplete;
    if ( !partList.empty()) {
        NameIdSet::const_iterator it = partList.begin();
        for (FileChunk::NameId prev = *it++; it != partList.end(); it++) {
            if (prev.next() == *it) {
                if (!incomplete.empty() && (*incomplete.rbegin() == prev)) {
                    throw IllegalStateException(make_string("3 consecutive files {%" PRIu64 ", %" PRIu64 ", %" PRIu64 "}. Impossible",
                                                            prev.getId()-1, prev.getId(), it->getId()));
                }
                incomplete.insert(*it);
            }
            prev = *it;
        }
    }
    return incomplete;
}

LogDataStore::NameIdSet
LogDataStore::getAllActiveFiles() const {
    NameIdSet files;
    MonitorGuard guard(_updateLock);
    for (const auto & fc : _fileChunks) {
        if (fc) {
            files.insert(fc->getNameId());
        }
    }
    return files;
}

LogDataStore::NameIdSet
LogDataStore::eraseIncompleteCompactedFiles(NameIdSet partList)
{
    NameIdSet toRemove = findIncompleteCompactedFiles(partList);
    for (NameId toBeRemoved : toRemove) {
        partList.erase(toBeRemoved);
        vespalib::string name(createFileName(toBeRemoved));
        LOG(warning, "'%s' has been detected as an incompletely compacted file. Erasing it.", name.c_str());
        FileChunk::eraseIdxFile(name);
        FileChunk::eraseDatFile(name);
    }

    return partList;
}

void
LogDataStore::eraseDanglingDatFiles(const NameIdSet &partList, const NameIdSet &datPartList)
{
    using IT = NameIdSet::const_iterator;
    
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
            vespalib::string fileName = createFileName(dbase);
            LOG(warning, "Removing dangling file '%s'", FileChunk::createDatFileName(fileName).c_str());
            FileChunk::eraseDatFile(fileName);
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
                char *err(nullptr);
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
                LOG(debug, "Skipping '%s' since it does not end with '%s'", file.data(), suffix.c_str());
            }
        }
    }
    return baseFiles;
}

void
LogDataStore::setLid(const MonitorGuard &guard, uint32_t lid, const LidInfo &meta)
{
    (void) guard;
    if (lid < _lidInfo.size()) {
        _genHandler.update_oldest_used_generation();
        _lidInfo.reclaim_memory(_genHandler.get_oldest_used_generation());
        const LidInfo prev = vespalib::atomic::load_ref_relaxed(_lidInfo[lid]);
        if (prev.valid()) {
            _fileChunks[prev.getFileId()]->remove(lid, prev.size());
        }
    } else {
        _lidInfo.ensure_size(lid+1, LidInfo());
        incGeneration();
    }
    updateDocIdLimit(lid + 1);
    vespalib::atomic::store_ref_release(_lidInfo[lid], meta);
}

void
LogDataStore::incGeneration()
{
    _lidInfo.setGeneration(_genHandler.getNextGeneration());
    _genHandler.incGeneration();
    _lidInfo.reclaim_memory(_genHandler.get_oldest_used_generation());
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
    for (size_t i(0), m(getDocIdLimit()); i < m; i++) {
        LidInfo lid(vespalib::atomic::load_ref_acquire(_lidInfo.acquire_elem_ref(i)));
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
    LOG(debug, "computeNumberOfSignificantBucketIdBits(file=%d) = %ld = %ld took %1.3f", fileId.getId(), msb, msbHistogram[msb-1], timer.min_time());
    return msb;
}

void
LogDataStore::verify(bool reportOnly) const
{
    MonitorGuard guard(_updateLock);
    for (const auto & fc : _fileChunks) {
        if (fc) {
            fc->verify(reportOnly);
        }
    }
}

class LogDataStore::WrapVisitor : public IWriteData
{
    IDataStoreVisitor &_visitor;
    
public:
    void write(MonitorGuard guard, uint32_t chunkId, uint32_t lid, const void *buffer, size_t sz) override {
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
        // accept() is used when reprocessing all documents stored (e.g. when adding attribute to a field).
        // We tag this work as WRITE, as the alternative to reprocessing would be to re-feed the data.
        fc.appendTo(_executor, *this, wrap, fc.getNumChunks(), &wrapProgress, CpuCategory::WRITE);
        if (prune) {
            internalFlushAll();
            FileChunk::UP toDie;
            {
                MonitorGuard guard(_updateLock);
                toDie = std::move(_fileChunks[fcId.getId()]);
            }
            toDie->erase();
        }
    }
    lfc.appendTo(_executor, *this, wrap, lastChunks, &wrapProgress, CpuCategory::WRITE);
    if (prune) {
        internalFlushAll();
    }
}

double
LogDataStore::getVisitCost() const
{
    uint32_t totalChunks = 0;
    MonitorGuard guard(_updateLock);
    for (const auto &fc : _fileChunks) {
        if (fc) {
            totalChunks += fc->getNumChunks();
        }
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
LogDataStore::holdFileChunk(const MonitorGuard & guard, FileId fileId)
{
    assert(guard.owns_lock());
    auto found = _holdFileChunks.find(fileId.getId());
    if (found == _holdFileChunks.end()) {
        _holdFileChunks[fileId.getId()] = 1;
    } else {
        assert(found->second < 2000u);
        found->second++;
    }
    return std::make_unique<FileChunkHolder>(*this, fileId);
}

void
LogDataStore::unholdFileChunk(FileId fileId)
{
    MonitorGuard guard(_updateLock);
    auto found = _holdFileChunks.find(fileId.getId());
    assert(found != _holdFileChunks.end());
    assert(found->second > 0u);
    if (--found->second == 0u) {
        _holdFileChunks.erase(found);
    }
    // No signalling, compactWorst() sleeps and retries
}

bool LogDataStore::canFileChunkBeDropped(const MonitorGuard & guard, FileId fileId) const {
    assert(guard.owns_lock());
    return ! _holdFileChunks.contains(fileId.getId());
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
    uint32_t docIdLimit = getDocIdLimit();
    return DataStoreStorageStats(diskFootprint, diskBloat, maxBucketSpread,
                                 lastSerialNum, lastFlushedSerialNum, docIdLimit);
}

vespalib::MemoryUsage
LogDataStore::getMemoryUsage() const
{
    MonitorGuard guard(_updateLock);
    vespalib::MemoryUsage result;
    result.merge(_lidInfo.getMemoryUsage());
    for (const auto &fileChunk : _fileChunks) {
        if (fileChunk) {
            result.merge(fileChunk->getMemoryUsage());
        }
    }
    size_t extra_allocated = 0;
    extra_allocated += _fileChunks.capacity() * sizeof(FileChunkVector::value_type);
    extra_allocated += _holdFileChunks.capacity() * sizeof(uint32_t);
    size_t extra_used = 0;
    extra_used += _fileChunks.size() * sizeof(FileChunkVector::value_type);
    extra_used += _holdFileChunks.size() * sizeof(uint32_t);
    result.incAllocatedBytes(extra_allocated);
    result.incUsedBytes(extra_used);
    return result;
}

std::vector<DataStoreFileChunkStats>
LogDataStore::getFileChunkStats() const
{
    std::vector<DataStoreFileChunkStats> result;
    {
        MonitorGuard guard(_updateLock);
        for (const auto & fc : _fileChunks) {
            if (fc) {
                result.push_back(fc->getStats());
            }
        }
    }
    std::sort(result.begin(), result.end());
    return result;
}

void
LogDataStore::compactLidSpace(uint32_t wantedDocLidLimit)
{
    MonitorGuard guard(_updateLock);
    assert(wantedDocLidLimit <= getDocIdLimit());
    for (size_t i = wantedDocLidLimit; i < _lidInfo.size(); ++i) {
        vespalib::atomic::store_ref_release(_lidInfo[i], LidInfo());
    }
    setDocIdLimit(wantedDocLidLimit);
    _compactLidSpaceGeneration = _genHandler.getCurrentGeneration();
    incGeneration();
}

bool
LogDataStore::canShrinkLidSpace() const
{
    MonitorGuard guard(_updateLock);
    return canShrinkLidSpace(guard);
}

bool
LogDataStore::canShrinkLidSpace(const MonitorGuard &) const
{
    // Update lock is held, allowing call to _lidInfo.get_size()
    return getDocIdLimit() < _lidInfo.get_size() &&
           _compactLidSpaceGeneration < _genHandler.get_oldest_used_generation();
}

size_t
LogDataStore::getEstimatedShrinkLidSpaceGain() const
{
    MonitorGuard guard(_updateLock);
    if (!canShrinkLidSpace(guard)) {
        return 0;
    }
    // Update lock is held, allowing call to _lidInfo.get_size()
    return (_lidInfo.get_size() - getDocIdLimit()) * sizeof(uint64_t);
}

void
LogDataStore::shrinkLidSpace()
{
    MonitorGuard guard(_updateLock);
    if (!canShrinkLidSpace(guard)) {
        return;
    }
    _lidInfo.shrink(getDocIdLimit());
    incGeneration();
}

FileChunk::FileId
LogDataStore::getActiveFileId(const MonitorGuard & guard) const {
    assert(hasUpdateLock(guard));
    (void) guard;
    return _active;
}

WriteableFileChunk &
LogDataStore::getActive(const MonitorGuard & guard) {
    assert(hasUpdateLock(guard));
    return static_cast<WriteableFileChunk &>(*_fileChunks[_active.getId()]);
}

const WriteableFileChunk &
LogDataStore::getActive(const MonitorGuard & guard) const {
    assert(hasUpdateLock(guard));
    return static_cast<const WriteableFileChunk &>(*_fileChunks[_active.getId()]);
}

const FileChunk *
LogDataStore::getPrevActive(const MonitorGuard & guard) const {
    assert(hasUpdateLock(guard));
    return ( !_prevActive.isActive() ) ? _fileChunks[_prevActive.getId()].get() : nullptr;
}
void
LogDataStore::setActive(const MonitorGuard & guard, FileId fileId) {
    assert(hasUpdateLock(guard));
    _prevActive = _active;
    _active = fileId;
}

} // namespace search
