// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "writeablefilechunk.h"
#include "data_store_file_chunk_stats.h"
#include "summaryexceptions.h"
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/searchlib/util/file_settings.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>
LOG_SETUP(".search.writeablefilechunk");

using search::common::FileHeaderContext;
using vespalib::CpuUsage;
using vespalib::FileHeader;
using vespalib::GenerationHandler;
using vespalib::IllegalHeaderException;
using vespalib::makeLambdaTask;
using vespalib::make_string;
using vespalib::nbostream;

namespace search {

namespace {

const size_t Alignment = FileSettings::DIRECTIO_ALIGNMENT;

}

/*
 * Information about serialized chunk written to .dat file but not yet
 * synced.
 */
class PendingChunk
{
    vespalib::nbostream _idx; // Serialized chunk for .idx file
    uint64_t _lastSerial;
    uint64_t _dataOffset;
    uint32_t _dataLen;
public:
    PendingChunk(uint64_t lastSerial, uint64_t dataOffset, uint32_t dataLen);
    ~PendingChunk();
    vespalib::nbostream & getSerializedIdx() { return _idx; }
    const vespalib::nbostream & getSerializedIdx() const { return _idx; }
    uint64_t getDataOffset() const { return _dataOffset; }
    uint32_t getDataLen() const { return _dataLen; }
    uint32_t getIdxLen() const { return _idx.size(); }
    uint64_t getLastSerial() const { return _lastSerial; }
};

class ProcessedChunk
{
public:
    ProcessedChunk(uint32_t chunkId, uint32_t alignment)
            : _chunkId(chunkId),
              _payLoad(0),
              _buf(0ul, alignment)
    { }
    void setPayLoad() { _payLoad = _buf.getDataLen(); }
    uint32_t getPayLoad() const { return _payLoad; }
    uint32_t getChunkId() const { return _chunkId; }
    const vespalib::DataBuffer & getBuf() const { return _buf; }
    vespalib::DataBuffer & getBuf() { return _buf; }
private:
    uint32_t             _chunkId;
    uint32_t             _payLoad;
    vespalib::DataBuffer _buf;
};

WriteableFileChunk::
WriteableFileChunk(vespalib::Executor &executor,
                   FileId fileId, NameId nameId,
                   const vespalib::string &baseName,
                   SerialNum initialSerialNum,
                   uint32_t docIdLimit,
                   const Config &config,
                   const TuneFileSummary &tune,
                   const FileHeaderContext &fileHeaderContext,
                   const IBucketizer * bucketizer,
                   bool skipCrcOnRead)
    : FileChunk(fileId, nameId, baseName, tune, bucketizer, skipCrcOnRead),
      _config(config),
      _serialNum(initialSerialNum),
      _frozen(false),
      _lock(),
      _cond(),
      _writeLock(),
      _flushLock(),
      _dataFile(_dataFileName.c_str()),
      _chunkMap(),
      _pendingChunks(),
      _pendingIdx(0),
      _pendingDat(0),
      _idxFileSize(0),
      _currentDiskFootprint(0),
      _nextChunkId(1),
      _active(new Chunk(0, Chunk::Config(config.getMaxChunkBytes()))),
      _alignment(1),
      _granularity(1),
      _maxChunkSize(0x100000),
      _firstChunkIdToBeWritten(0),
      _writeTaskIsRunning(false),
      _writeMonitor(),
      _writeCond(),
      _executor(executor),
      _bucketMap(bucketizer)
{
    _docIdLimit = docIdLimit;
    if (tune._write.getWantDirectIO()) {
        _dataFile.EnableDirectIO();
    }
    if (tune._write.getWantSyncWrites()) {
        _dataFile.EnableSyncWrites();
    }
    if (_dataFile.OpenReadWrite()) {
        readDataHeader();
        if (_dataHeaderLen == 0) {
            writeDataHeader(fileHeaderContext);
        }
        _dataFile.SetPosition(_dataFile.GetSize());
        if (tune._write.getWantDirectIO()) {
            if (!_dataFile.GetDirectIORestrictions(_alignment, _granularity, _maxChunkSize)) {
                LOG(debug, "Direct IO setup failed for file %s due to %s",
                           _dataFile.GetFileName(), _dataFile.getLastErrorString().c_str());
            }
        }
        auto idxFile = openIdx();
        readIdxHeader(*idxFile);
        if (_idxHeaderLen == 0) {
            _idxHeaderLen = writeIdxHeader(fileHeaderContext, _docIdLimit, *idxFile);
        }
        _idxFileSize.store(idxFile->GetSize(), std::memory_order_relaxed);
        if ( ! idxFile->Sync()) {
            throw SummaryException("Failed syncing idx file", *idxFile, VESPA_STRLOC);
        }
    } else {
        throw SummaryException("Failed opening data file", _dataFile, VESPA_STRLOC);
    }
    _firstChunkIdToBeWritten = _active->getId();
    updateCurrentDiskFootprint();
}

std::unique_ptr<FastOS_FileInterface>
WriteableFileChunk::openIdx() {
    auto file = std::make_unique<FastOS_File>(_idxFileName.c_str());
    if (_dataFile.useSyncWrites()) {
        file->EnableSyncWrites();
    }
    if ( ! file->OpenReadWrite() ) {
        throw SummaryException("Failed opening idx file", *file, VESPA_STRLOC);
    }
    return file;
}

WriteableFileChunk::~WriteableFileChunk()
{
    if (!frozen()) {
        if (_active->size() || _active->count()) {
            flush(true, _serialNum, CpuUsage::Category::WRITE);
        }
        freeze(CpuUsage::Category::WRITE);
    }
    // This is a wild stab at fixing bug 6348143.
    // If it works it indicates something bad with the filesystem.
    if (_dataFile.IsOpened()) {
        if (! _dataFile.Sync()) {
            assert(false);
        }
    }
}

size_t
WriteableFileChunk::updateLidMap(const unique_lock &guard, ISetLid &ds, uint64_t serialNum, uint32_t docIdLimit)
{
    size_t sz = FileChunk::updateLidMap(guard, ds, serialNum, docIdLimit);
    _nextChunkId = _chunkInfo.size();
    _active = std::make_unique<Chunk>(_nextChunkId++, Chunk::Config(_config.getMaxChunkBytes()));
    _serialNum = getLastPersistedSerialNum();
    _firstChunkIdToBeWritten = _active->getId();
    setDiskFootprint(0);
    _chunkInfo.reserve(0x10000);
    return sz;
}

void
WriteableFileChunk::restart(uint32_t nextChunkId, CpuUsage::Category cpu_category)
{
    auto task = makeLambdaTask([this, nextChunkId] {fileWriter(nextChunkId);});
    _executor.execute(CpuUsage::wrap(std::move(task), cpu_category));
}

namespace {

LidInfoWithLidV::const_iterator
find_first(LidInfoWithLidV::const_iterator begin, uint32_t chunkId) {
   for ( ; begin->getChunkId() != chunkId; ++begin);
   return begin;
}

LidInfoWithLidV::const_iterator
seek_past(LidInfoWithLidV::const_iterator begin, LidInfoWithLidV::const_iterator end, uint32_t chunkId) {
   for ( ; (begin < end) && (begin->getChunkId() == chunkId); begin++);
   return begin;
}

struct LidAndBuffer {
    LidAndBuffer(uint32_t lid, uint32_t sz, vespalib::alloc::Alloc buf) noexcept : _lid(lid), _size(sz), _buf(std::move(buf)) {}
    uint32_t _lid;
    uint32_t _size;
    vespalib::alloc::Alloc _buf;
};

}

const Chunk&
WriteableFileChunk::get_chunk(uint32_t chunk) const
{
    auto found = _chunkMap.find(chunk);
    if (found != _chunkMap.end()) {
        return *found->second;
    } else {
        assert(chunk == _active->getId());
        return *_active;
    }
}

void
WriteableFileChunk::read(LidInfoWithLidV::const_iterator begin, size_t count, IBufferVisitor & visitor) const
{
    if (count == 0) { return; }
    if (!frozen()) {
        vespalib::hash_map<uint32_t, ChunkInfo> chunksOnFile;
        std::vector<LidAndBuffer> buffers;
        {
            std::lock_guard guard(_lock);
            for (size_t i(0); i < count; i++) {
                const LidInfoWithLid & li = *(begin + i);
                uint32_t chunk = li.getChunkId();
                if ((chunk >= _chunkInfo.size()) || !_chunkInfo[chunk].valid()) {
                    auto copy = get_chunk(chunk).read(li.getLid());
                    buffers.emplace_back(li.getLid(), copy.first, std::move(copy.second));
                } else {
                    chunksOnFile[chunk] = _chunkInfo[chunk];
                }
            }
        }
        for (auto & entry : buffers) {
            visitor.visit(entry._lid, vespalib::ConstBufferRef(entry._buf.get(), entry._size));
            entry._buf = vespalib::alloc::Alloc();
        }
        for (auto & it : chunksOnFile) {
            auto first = find_first(begin, it.first);
            auto last = seek_past(first, begin + count, it.first);
            FileChunk::read(first, last - first, it.second, visitor);
        }
    } else {
        FileChunk::read(begin, count, visitor);
    }
}

ssize_t
WriteableFileChunk::read(uint32_t lid, SubChunkId chunkId, vespalib::DataBuffer & buffer) const
{
    ChunkInfo chunkInfo;
    if (!frozen()) {
        std::lock_guard guard(_lock);
        if ((chunkId >= _chunkInfo.size()) || !_chunkInfo[chunkId].valid()) {
            auto found = _chunkMap.find(chunkId);
            if (found != _chunkMap.end()) {
                return found->second->read(lid, buffer);
            } else {
                assert(chunkId == _active->getId());
                return _active->read(lid, buffer);
            }
        }
        chunkInfo = _chunkInfo[chunkId];
    } else {
        chunkInfo = _chunkInfo[chunkId];
    }
    return FileChunk::read(lid, chunkId, chunkInfo, buffer);
}

void
WriteableFileChunk::internalFlush(uint32_t chunkId, uint64_t serialNum, CpuUsage::Category cpu_category)
{
    Chunk * active(nullptr);
    {
        std::lock_guard guard(_lock);
        active = _chunkMap[chunkId].get();
    }

    auto tmp = std::make_unique<ProcessedChunk>(chunkId, _alignment);
    if (_alignment > 1) {
        tmp->getBuf().ensureFree(active->getMaxPackSize(_config.getCompression()) + _alignment - 1);
    }
    active->pack(serialNum, tmp->getBuf(), _config.getCompression());
    tmp->setPayLoad();
    if (_alignment > 1) {
        const size_t padAfter((_alignment - tmp->getPayLoad() % _alignment) % _alignment);
        memset(tmp->getBuf().getFree(), 0, padAfter);
        tmp->getBuf().moveFreeToData(padAfter);
    }
    {
        std::lock_guard innerGuard(_lock);
        setDiskFootprint(FileChunk::getDiskFootprint() + tmp->getBuf().getDataLen());
    }
    enque(std::move(tmp), cpu_category);
}

void
WriteableFileChunk::enque(ProcessedChunkUP tmp, CpuUsage::Category cpu_category)
{
    LOG(debug, "enqueing %p", tmp.get());
    std::unique_lock guard(_writeMonitor);
    _writeQ.push_back(std::move(tmp));
    if ( ! _writeTaskIsRunning) {
        _writeTaskIsRunning = true;
        uint32_t nextChunkId = _firstChunkIdToBeWritten;
        guard.unlock();
        _writeCond.notify_one();
        restart(nextChunkId, cpu_category);
    } else {
        _writeCond.notify_one();
    }
}

namespace {

const std::vector<char> Padding(Alignment, '\0');

size_t
getAlignedStartPos(FastOS_File & file)
{
    ssize_t startPos(file.GetPosition());
    assert(startPos == file.GetSize());
    if (startPos & (Alignment-1)) {
        FastOS_File align(file.GetFileName());
        if (align.OpenWriteOnly()) {
            align.SetPosition(startPos);
            ssize_t toWrite(Alignment - (startPos & (Alignment-1)));
            ssize_t written = align.Write2(&Padding[0], toWrite);
            if (written == toWrite) {
                if ( align.Sync() ) {
                    file.SetPosition(align.GetSize());
                    startPos = file.GetPosition();
                } else {
                    throw SummaryException(
                            make_string("Failed syncing dat file."),
                            align, VESPA_STRLOC);
                }
             } else {
                throw SummaryException(
                    make_string("Failed writing %ld bytes to dat file. Only %ld written", toWrite, written),
                    align, VESPA_STRLOC);
             }
        } else {
            throw SummaryException("Failed opening dat file for padding for direct io.", align, VESPA_STRLOC);
        }
    }
    assert((startPos & (Alignment-1)) == 0);
    return startPos;
}

}

WriteableFileChunk::ProcessedChunkQ
WriteableFileChunk::drainQ(unique_lock & guard)
{
    assert(guard.mutex() == &_writeMonitor && guard.owns_lock());
    ProcessedChunkQ newChunks;
    newChunks.swap(_writeQ);
    if ( ! newChunks.empty() ) {
        _writeCond.notify_one();
    }
    return newChunks;
}

void
WriteableFileChunk::insertChunks(ProcessedChunkMap & orderedChunks, ProcessedChunkQ & newChunks, const uint32_t nextChunkId)
{
    (void) nextChunkId;
    for (auto &chunk : newChunks) {
        if (chunk) {
            assert(chunk->getChunkId() >= nextChunkId);
            assert(orderedChunks.find(chunk->getChunkId()) == orderedChunks.end());
            orderedChunks[chunk->getChunkId()] = std::move(chunk);
        } else {
            orderedChunks[std::numeric_limits<uint32_t>::max()] = ProcessedChunkUP();
        }
    }
}

WriteableFileChunk::ProcessedChunkQ
WriteableFileChunk::fetchNextChain(ProcessedChunkMap & orderedChunks, const uint32_t firstChunkId)
{
    ProcessedChunkQ chunks;
    while (!orderedChunks.empty() &&
           ((orderedChunks.begin()->first == (firstChunkId+chunks.size())) ||
            !orderedChunks.begin()->second))
    {
        chunks.push_back(std::move(orderedChunks.begin()->second));
        orderedChunks.erase(orderedChunks.begin());
    }
    return chunks;
}

ChunkMeta
WriteableFileChunk::computeChunkMeta(const unique_lock & guard,
                                     const GenerationHandler::Guard & bucketizerGuard,
                                     size_t offset, const ProcessedChunk & tmp, const Chunk & active)
{
    assert((guard.mutex() == &_lock) && guard.owns_lock());
    size_t dataLen = tmp.getBuf().getDataLen();
    const ChunkMeta cmeta(offset, tmp.getPayLoad(), active.getLastSerial(), active.count());
    assert((size_t(tmp.getBuf().getData())%_alignment) == 0);
    assert((dataLen%_alignment) == 0);
    auto pcsp = std::make_shared<PendingChunk>(active.getLastSerial(), offset, dataLen);
    PendingChunk &pc(*pcsp.get());
    nbostream &os(pc.getSerializedIdx());
    cmeta.serialize(os);
    BucketDensityComputer bucketMap(_bucketizer);
    for (const Chunk::Entry & e : active.getLids()) {
        bucketMap.recordLid(bucketizerGuard, e.getLid(), e.netSize());
        _bucketMap.recordLid(bucketizerGuard, e.getLid(), e.netSize());
        LidMeta lm(e.getLid(), e.netSize());
        lm.serialize(os);
    }
    addNumBuckets(bucketMap.getNumBuckets());
    setNumUniqueBuckets(_bucketMap.getNumBuckets());

    _pendingDat += pc.getDataLen();
    _pendingIdx += pc.getIdxLen();
    _pendingChunks.push_back(pcsp);
    return cmeta;
}

ChunkMetaV
WriteableFileChunk::computeChunkMeta(ProcessedChunkQ & chunks, size_t startPos, size_t & sz, bool & done)
{
    ChunkMetaV cmetaV;
    cmetaV.reserve(chunks.size());
    uint64_t lastSerial(_lastPersistedSerialNum.load(std::memory_order_relaxed));
    std::unique_lock guard(_lock);

    if (!_pendingChunks.empty()) {
        const PendingChunk & pc = *_pendingChunks.back();
        assert(pc.getLastSerial() >= lastSerial);
        lastSerial = pc.getLastSerial();
    }

    GenerationHandler::Guard bucketizerGuard = _bucketMap.getGuard();
    for (size_t i(0), m(chunks.size()); i < m; i++) {
        if (chunks[i]) {
            const ProcessedChunk & chunk = *chunks[i];
            const ChunkMeta cmeta(computeChunkMeta(guard, bucketizerGuard, startPos + sz, chunk, *_chunkMap[chunk.getChunkId()]));
            sz += chunk.getBuf().getDataLen();
            cmetaV.push_back(cmeta);
            assert(cmeta.getLastSerial() >= lastSerial);
            lastSerial = cmeta.getLastSerial();
        } else {
            done = true;
            assert((i+1) == chunks.size());
            chunks.resize(i);
            assert(i == chunks.size());
        }
    }
    return cmetaV;
}

void
WriteableFileChunk::writeData(const ProcessedChunkQ & chunks, size_t sz)
{
    vespalib::DataBuffer buf(0ul, _alignment);
    buf.ensureFree(sz);
    for (const auto & chunk : chunks) {
        buf.writeBytes(chunk->getBuf().getData(), chunk->getBuf().getDataLen());
    }

    std::lock_guard guard(_writeLock);
    ssize_t wlen = _dataFile.Write2(buf.getData(), buf.getDataLen());
    if (wlen != static_cast<ssize_t>(buf.getDataLen())) {
        throw SummaryException(make_string("Failed writing %ld bytes to dat file. Only %ld written",
                                           buf.getDataLen(), wlen),
                               _dataFile, VESPA_STRLOC);
    }
    updateCurrentDiskFootprint();
}

void
WriteableFileChunk::updateChunkInfo(const ProcessedChunkQ & chunks, const ChunkMetaV & cmetaV, size_t sz)
{
    uint32_t maxChunkId(0);
    for (const auto & chunk : chunks) {
        maxChunkId = std::max(chunk->getChunkId(), maxChunkId);
    }
    std::lock_guard guard(_lock);
    if (maxChunkId >= _chunkInfo.size()) {
        _chunkInfo.reserve(vespalib::roundUp2inN(maxChunkId+1));
    }
    size_t nettoSz(sz);
    for (size_t i(0); i < chunks.size(); i++) {
        const ProcessedChunk & chunk = *chunks[i];
        assert(_chunkMap.find(chunk.getChunkId()) == _chunkMap.begin());
        const Chunk & active = *_chunkMap.begin()->second;
        assert(active.getId() == chunk.getChunkId());
        if (active.getId() >= _chunkInfo.size()) {
            _chunkInfo.resize(active.getId()+1);
        }
        const ChunkMeta & cmeta(cmetaV[i]);
        _chunkInfo[active.getId()] = ChunkInfo(cmeta.getOffset(), chunk.getPayLoad(), cmeta.getLastSerial());
        nettoSz += active.size();
        _chunkMap.erase(_chunkMap.begin());
    }
    setDiskFootprint(FileChunk::getDiskFootprint() - nettoSz);
    _cond.notify_all();
}

void
WriteableFileChunk::fileWriter(const uint32_t firstChunkId)
{
    LOG(debug, "Starting the filewriter with chunkid = %d", firstChunkId);
    uint32_t nextChunkId(firstChunkId);
    bool done(false);
    std::unique_lock guard(_writeMonitor);
    {
        for (ProcessedChunkQ newChunks(drainQ(guard)); !newChunks.empty(); newChunks = drainQ(guard)) {
            guard.unlock();
            insertChunks(_orderedChunks, newChunks, nextChunkId);
            ProcessedChunkQ chunks(fetchNextChain(_orderedChunks, nextChunkId));
            nextChunkId += chunks.size();
            
            size_t sz(0);
            ChunkMetaV cmetaV(computeChunkMeta(chunks, getAlignedStartPos(_dataFile), sz, done));
            writeData(chunks, sz);
            updateChunkInfo(chunks, cmetaV, sz);
            LOG(spam, "bucket spread = '%3.2f'", getBucketSpread());
            guard = std::unique_lock(_writeMonitor);
            if (done) break;
        }
    }
    LOG(debug, "Stopping the filewriter with startchunkid = %d and ending chunkid = %d done=%d",
               firstChunkId, nextChunkId, done);
    assert(_writeQ.empty());
    _writeTaskIsRunning = false;
    if (done) {
        assert(_chunkMap.empty());
        for (const ChunkInfo & cm : _chunkInfo) {
            (void) cm;
            assert(cm.valid() && cm.getSize() != 0);
        }
        _writeCond.notify_all();
    } else {
        _firstChunkIdToBeWritten = nextChunkId;
    }
}

vespalib::system_time
WriteableFileChunk::getModificationTime() const
{
    std::lock_guard guard(_lock);
    return _modificationTime;
}

void
WriteableFileChunk::freeze(CpuUsage::Category cpu_category)
{
    if (!frozen()) {
        waitForAllChunksFlushedToDisk();
        enque(ProcessedChunkUP(), cpu_category);
        {
            std::unique_lock guard(_writeMonitor);
            while (_writeTaskIsRunning) {
                _writeCond.wait_for(guard, 10ms);
            }
        }
        assert(_writeQ.empty());
        assert(_chunkMap.empty());
        {
            std::unique_lock guard(_lock);
            setDiskFootprint(getDiskFootprint(guard));
            _frozen.store(true, std::memory_order_release);
        }
        bool sync_and_close_ok = _dataFile.Sync() && _dataFile.Close();
        assert(sync_and_close_ok);
        _bucketMap = BucketDensityComputer(_bucketizer);
    }
}

size_t
WriteableFileChunk::getDiskFootprint() const
{
    if (frozen()) {
        return FileChunk::getDiskFootprint();
    } else {
        // Double checked locking.
        std::unique_lock guard(_lock);
        return getDiskFootprint(guard);
    }
}

size_t
WriteableFileChunk::getDiskFootprint(const unique_lock & guard) const
{
    assert(guard.mutex() == &_lock && guard.owns_lock());
    return frozen()
        ? FileChunk::getDiskFootprint()
        : _currentDiskFootprint.load(std::memory_order_relaxed) + FileChunk::getDiskFootprint();
}

size_t
WriteableFileChunk::getMemoryFootprint() const
{
    size_t sz(0);
    std::lock_guard guard(_lock);
    for (const auto & it : _chunkMap) {
        sz += it.second->size();
    }
    sz += _pendingIdx + _pendingDat;
    return sz + FileChunk::getMemoryFootprint();
}

size_t
WriteableFileChunk::getMemoryMetaFootprint() const
{
    std::lock_guard guard(_lock);
    constexpr size_t mySizeWithoutMyParent(sizeof(*this) - sizeof(FileChunk));
    return mySizeWithoutMyParent + FileChunk::getMemoryMetaFootprint();
}

vespalib::MemoryUsage
WriteableFileChunk::getMemoryUsage() const
{
    std::lock_guard guard(_lock);
    vespalib::MemoryUsage result;
    for (const auto &chunk : _chunkMap) {
        result.merge(chunk.second->getMemoryUsage());
    }
    size_t pendingBytes = _pendingIdx + _pendingDat;
    result.incAllocatedBytes(pendingBytes);
    result.incUsedBytes(pendingBytes);
    result.merge(FileChunk::getMemoryUsage());
    return result;
}

int32_t WriteableFileChunk::flushLastIfNonEmpty(bool force)
{
    int32_t chunkId(-1);
    std::unique_lock guard(_lock);
    for (bool ready(false); !ready;) {
        if (_chunkMap.size() > 1000) {
            LOG(debug, "Summary write overload at least 1000 outstanding chunks. Suspending.");
            _cond.wait(guard);
            LOG(debug, "Summary write overload eased off. Commencing.");
        } else {
            ready = true;
        }
    }
    if ( force || ! _active->empty()) {
        chunkId = _active->getId();
        _chunkMap[chunkId] = std::move(_active);
        assert(_nextChunkId < LidInfo::getChunkIdLimit());
        _active = std::make_unique<Chunk>(_nextChunkId++, Chunk::Config(_config.getMaxChunkBytes()));
    }
    return chunkId;
}

void
WriteableFileChunk::flush(bool block, uint64_t syncToken, CpuUsage::Category cpu_category)
{
    int32_t chunkId = flushLastIfNonEmpty(syncToken > _serialNum);
    if (chunkId >= 0) {
        setSerialNum(syncToken);
        auto task = makeLambdaTask([this, chunkId, serialNum=_serialNum, cpu_category] {
            internalFlush(chunkId, serialNum, cpu_category);
        });
        _executor.execute(CpuUsage::wrap(std::move(task), cpu_category));
    } else {
        if (block) {
            std::lock_guard guard(_lock);
            if (!_chunkMap.empty()) {
                chunkId = _chunkMap.rbegin()->first;
            }
        }
    }
    if (block) {
        waitForChunkFlushedToDisk(chunkId);
    }
}

void
WriteableFileChunk::waitForDiskToCatchUpToNow() const
{
    int32_t chunkId(-1);
    {
        std::lock_guard guard(_lock);
        if (!_chunkMap.empty()) {
            chunkId = _chunkMap.rbegin()->first;
        }
    }
    waitForChunkFlushedToDisk(chunkId);
}

void
WriteableFileChunk::waitForChunkFlushedToDisk(uint32_t chunkId) const
{
    std::unique_lock guard(_lock);
    while( _chunkMap.find(chunkId) != _chunkMap.end() ) {
        _cond.wait(guard);
    }
}

void
WriteableFileChunk::waitForAllChunksFlushedToDisk() const
{
    std::unique_lock guard(_lock);
    while( ! _chunkMap.empty() ) {
        _cond.wait(guard);
    }
}

LidInfo
WriteableFileChunk::append(uint64_t serialNum, uint32_t lid, const void * buffer, size_t len,
                           CpuUsage::Category cpu_category)
{
    assert( !frozen() );
    if ( ! _active->hasRoom(len)) {
        flush(false, _serialNum, cpu_category);
    }
    assert(serialNum >= _serialNum);
    _serialNum = serialNum;
    _addedBytes += adjustSize(len);
    _numLids++;
    size_t oldSz(_active->size());
    LidMeta lm = _active->append(lid, buffer, len);
    setDiskFootprint(FileChunk::getDiskFootprint() - oldSz + _active->size());
    return LidInfo(getFileId().getId(), _active->getId(), lm.size());
}


void
WriteableFileChunk::readDataHeader()
{
    int64_t fSize(_dataFile.GetSize());
    try {
        FileHeader h;
        _dataHeaderLen = h.readFile(_dataFile);
        _dataFile.SetPosition(_dataHeaderLen);
    } catch (IllegalHeaderException &e) {
        _dataFile.SetPosition(0);
        try {
            FileHeader::FileReader fr(_dataFile);
            uint32_t header2Len = FileHeader::readSize(fr);
            if (header2Len <= fSize)
                e.throwSelf(); // header not truncated
        } catch (IllegalHeaderException &e2) {
        }
        if (fSize > 0) {
            // Truncate file (dropping header) if cannot even read
            // header length, or if header has been truncated.
            _dataFile.SetPosition(0);
            _dataFile.SetSize(0);
            assert(_dataFile.GetSize() == 0);
            assert(_dataFile.GetPosition() == 0);
            LOG(warning,
                "Truncated file chunk data %s due to truncated file header",
                _dataFile.GetFileName());
        }
    }
}


void
WriteableFileChunk::readIdxHeader(FastOS_FileInterface & idxFile)
{
    int64_t fSize(idxFile.GetSize());
    try {
        FileHeader h;
        _idxHeaderLen = h.readFile(idxFile);
        idxFile.SetPosition(_idxHeaderLen);
        _docIdLimit = readDocIdLimit(h);
    } catch (IllegalHeaderException &e) {
        idxFile.SetPosition(0);
        try {
            FileHeader::FileReader fr(idxFile);
            uint32_t header2Len = FileHeader::readSize(fr);
            if (header2Len <= fSize) {
                e.throwSelf(); // header not truncated
            }
        } catch (IllegalHeaderException &e2) {
        }
        if (fSize > 0) {
            // Truncate file (dropping header) if cannot even read
            // header length, or if header has been truncated.
            idxFile.SetPosition(0);
            idxFile.SetSize(0);
            assert(idxFile.GetSize() == 0);
            assert(idxFile.GetPosition() == 0);
            LOG(warning, "Truncated file chunk index %s due to truncated file header", idxFile.GetFileName());
        }
    }
}


void
WriteableFileChunk::writeDataHeader(const FileHeaderContext &fileHeaderContext)
{
    typedef FileHeader::Tag Tag;
    FileHeader h(FileSettings::DIRECTIO_ALIGNMENT);
    assert(_dataFile.IsOpened());
    assert(_dataFile.IsWriteMode());
    assert(_dataFile.GetPosition() == 0);
    fileHeaderContext.addTags(h, _dataFile.GetFileName());
    h.putTag(Tag("desc", "Log data store chunk data"));
    _dataHeaderLen = h.writeFile(_dataFile);
}


uint64_t
WriteableFileChunk::writeIdxHeader(const FileHeaderContext &fileHeaderContext, uint32_t docIdLimit, FastOS_FileInterface &file)
{
    typedef FileHeader::Tag Tag;
    FileHeader h;
    assert(file.IsOpened());
    assert(file.IsWriteMode());
    assert(file.GetPosition() == 0);
    fileHeaderContext.addTags(h, file.GetFileName());
    h.putTag(Tag("desc", "Log data store chunk index"));
    writeDocIdLimit(h, docIdLimit);
    return h.writeFile(file);
}


bool
WriteableFileChunk::needFlushPendingChunks(uint64_t serialNum, uint64_t datFileLen) {
    std::unique_lock guard(_lock);
    return needFlushPendingChunks(guard, serialNum, datFileLen);
}

bool
WriteableFileChunk::needFlushPendingChunks(const unique_lock & guard, uint64_t serialNum, uint64_t datFileLen)
{
    (void) guard;
    assert(guard.mutex() == &_lock && guard.owns_lock());
    if (_pendingChunks.empty())
        return false;
    const PendingChunk & pc = *_pendingChunks.front();
    if (pc.getLastSerial() > serialNum)
        return false;
    bool datWritten = datFileLen >= pc.getDataOffset() + pc.getDataLen();
    if (pc.getLastSerial() < serialNum) {
        assert(datWritten);
        return true;
    }
    return datWritten;
}

void
WriteableFileChunk::updateCurrentDiskFootprint() {
    _currentDiskFootprint.store(_idxFileSize.load(std::memory_order_relaxed) + _dataFile.getSize(), std::memory_order_relaxed);
}

/*
 * Called by writeExecutor thread for now.
 */
void
WriteableFileChunk::flushPendingChunks(uint64_t serialNum) {
    std::unique_lock flushGuard(_flushLock);
    if (frozen())
        return;
    uint64_t datFileLen = _dataFile.getSize();
    vespalib::system_time timeStamp(vespalib::system_clock::now());
    if (needFlushPendingChunks(serialNum, datFileLen)) {
        timeStamp = unconditionallyFlushPendingChunks(flushGuard, serialNum, datFileLen);
    }
    std::lock_guard guard(_lock);
    _modificationTime = std::max(timeStamp, _modificationTime);
}

vespalib::system_time
WriteableFileChunk::unconditionallyFlushPendingChunks(const unique_lock &flushGuard, uint64_t serialNum, uint64_t datFileLen)
{
    assert((flushGuard.mutex() == &_flushLock) && flushGuard.owns_lock());
    if ( ! _dataFile.Sync()) {
        throw SummaryException("Failed fsync of dat file", _dataFile, VESPA_STRLOC);
    }
    nbostream os;
    uint64_t lastSerial = 0;
    {
        std::unique_lock guard(_lock);
        lastSerial = _lastPersistedSerialNum.load(std::memory_order_relaxed);
        for (;;) {
            if (!needFlushPendingChunks(guard, serialNum, datFileLen))
                break;
            std::shared_ptr<PendingChunk> pcsp = std::move(_pendingChunks.front());
            _pendingChunks.pop_front();
            const PendingChunk &pc(*pcsp.get());
            assert(_pendingIdx >= pc.getIdxLen());
            assert(_pendingDat >= pc.getDataLen());
            assert(datFileLen >= pc.getDataOffset() + pc.getDataLen());
            assert(lastSerial <= pc.getLastSerial());
            _pendingIdx -= pc.getIdxLen();
            _pendingDat -= pc.getDataLen();
            lastSerial = pc.getLastSerial();
            const nbostream &os2(pc.getSerializedIdx());
            os.write(os2.data(), os2.size());
        }
    }
    vespalib::system_time timeStamp(vespalib::system_clock::now());
    auto idxFile = openIdx();
    idxFile->SetPosition(idxFile->GetSize());
    ssize_t wlen = idxFile->Write2(os.data(), os.size());
    updateCurrentDiskFootprint();

    if (wlen != static_cast<ssize_t>(os.size())) {
        throw SummaryException(make_string("Failed writing %ld bytes to idx file. Only wrote %ld bytes ", os.size(), wlen), *idxFile, VESPA_STRLOC);
    }
    if ( ! idxFile->Sync()) {
        throw SummaryException("Failed fsync of idx file", *idxFile, VESPA_STRLOC);
    }
    _idxFileSize.store(idxFile->GetSize(), std::memory_order_relaxed);
    if (_lastPersistedSerialNum.load(std::memory_order_relaxed) < lastSerial) {
        _lastPersistedSerialNum.store(lastSerial, std::memory_order_relaxed);
    }
    return timeStamp;
}

DataStoreFileChunkStats
WriteableFileChunk::getStats() const
{
    DataStoreFileChunkStats stats = FileChunk::getStats();
    uint64_t serialNum = getSerialNum();
    return DataStoreFileChunkStats(stats.diskUsage(), stats.diskBloat(), stats.maxBucketSpread(),
                                   serialNum, stats.lastFlushedSerialNum(), stats.docIdLimit(), stats.nameId());
};

PendingChunk::PendingChunk(uint64_t lastSerial, uint64_t dataOffset, uint32_t dataLen)
    : _idx(),
      _lastSerial(lastSerial),
      _dataOffset(dataOffset),
      _dataLen(dataLen)
{ }

PendingChunk::~PendingChunk() = default;

} // namespace search
