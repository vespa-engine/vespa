// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memfile_v1_serializer.h"
#include "memfile_v1_verifier.h"

#include "locationreadplanner.h"
#include "uniqueslotgenerator.h"
#include <vespa/memfilepersistence/common/exceptions.h>
#include <vespa/memfilepersistence/spi/memfilepersistenceprovidermetrics.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/storageframework/generic/clock/timer.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.memfilev1");

namespace storage::memfile {

namespace {

void alignUp(uint32_t& value, uint32_t offset = 0, uint32_t block = 512) {
    uint32_t blocks = (value + offset + block - 1) / block;
    value = blocks * block - offset;
}

int32_t getBufferPos(
        const DataLocation& location,
        const std::vector<DataLocation>& locations)
{
    uint32_t posNow = 0;
    for (uint32_t i = 0; i < locations.size(); ++i) {
        if (locations[i].contains(location)) {
            return posNow + location._pos - locations[i]._pos;
        }

        posNow += locations[i]._size;
    }

    return -1;
}

}

MemFileV1Serializer::MemFileV1Serializer(ThreadMetricProvider& metricProvider)
    : _metricProvider(metricProvider)
{
}

namespace {

class SlotValidator
{
public:
    SlotValidator(uint32_t headerBlockOffset,
                  uint32_t bodyBlockOffset,
                  uint32_t fileSize)
        : _headerBlockOffset(headerBlockOffset),
          _bodyBlockOffset(bodyBlockOffset),
          _fileSize(fileSize)
    {
    }

    bool slotHasValidInformation(const MetaSlot& ms) const {
        const uint16_t slotCrc(ms.calcSlotChecksum());
        const bool checksumOk(slotCrc == ms._checksum);
        return (checksumOk && slotLocationsWithinFileBounds(ms));
    }

private:
    bool slotLocationsWithinFileBounds(const MetaSlot& ms) const {
        // The reason for checking header location bounds against file size
        // instead of body block offset is that the latter is computed from the
        // file meta header information and will thus be entirely unaware of
        // any file truncations.
        return (_headerBlockOffset + ms._headerPos + ms._headerSize <= _fileSize
                && _bodyBlockOffset + ms._bodyPos + ms._bodySize <= _fileSize);
    }

    const uint32_t _headerBlockOffset;
    const uint32_t _bodyBlockOffset;
    const uint32_t _fileSize;
};

}

void
MemFileV1Serializer::loadFile(MemFile& file, Environment& env,
                              Buffer& buffer, uint64_t bytesRead)
{
    SerializationMetrics& metrics(getMetrics().serialization);
    SimpleMemFileIOBuffer& ioBuf(
            static_cast<SimpleMemFileIOBuffer&>(file.getMemFileIO()));

    vespalib::LazyFile* lf = &ioBuf.getFileHandle();

    assert(file.getSlotCount() == 0);
    assert(bytesRead >= 64);

    const Header* header(reinterpret_cast<const Header*>(buffer.getBuffer()));
    if (header->_checksum != header->calcHeaderChecksum()) {
        std::ostringstream error;
        error << "Header checksum mismatch. Stored checksum " << std::hex
              << header->_checksum << " does not match calculated checksum "
              << header->calcHeaderChecksum();
        throw CorruptMemFileException(error.str(), file.getFile(), VESPA_STRLOC);
    }
    uint32_t headerBlockIndex = sizeof(Header)
                              + header->_metaDataListSize * sizeof(MetaSlot);

    // Read all we need including first header bytes until alignment
    uint32_t firstAlignedHeaderByte = headerBlockIndex;
    alignUp(firstAlignedHeaderByte);
    if (firstAlignedHeaderByte > bytesRead) {
        framework::MilliSecTimer timer(env._clock);
        LOG(spam,
            "Only read %zu of required %u header bytes. "
            "Resizing buffer and reading remaining data",
            bytesRead,
            firstAlignedHeaderByte);
        buffer.resize(firstAlignedHeaderByte);
        header = reinterpret_cast<const Header*>(buffer.getBuffer());
        off_t moreBytesRead = lf->read(
                buffer + bytesRead,
                firstAlignedHeaderByte - bytesRead,
                bytesRead);
        bytesRead += moreBytesRead;
        if (bytesRead != firstAlignedHeaderByte) {
            size_t fileSize = lf->getFileSize();
            if (firstAlignedHeaderByte > fileSize) {
                std::ostringstream error;
                error << "Header indicates file is bigger than it "
                      << "physically is. First aligned byte in header block "
                      << "starts at byte " << firstAlignedHeaderByte
                      << " while file is " << fileSize << " bytes long.";
                throw CorruptMemFileException(error.str(), file.getFile(), VESPA_STRLOC);

            }
            assert(bytesRead == firstAlignedHeaderByte);
        }
        metrics.tooLargeMetaReadLatency.addValue(timer.getElapsedTimeAsDouble());
    }

    FileInfo::UP data(new FileInfo);
    data->_metaDataListSize = header->_metaDataListSize;
    data->_headerBlockSize = header->_headerBlockSize;
    const uint32_t headerBlockOffset(
            sizeof(Header) + data->_metaDataListSize * sizeof(MetaSlot));
    const uint32_t bodyBlockOffset = headerBlockOffset + data->_headerBlockSize;
    const uint32_t fileSize = lf->getFileSize();

    // Avoid underflow in case of truncation.
    const uint32_t bodyBlockSize(
            fileSize > bodyBlockOffset ? fileSize - bodyBlockOffset : 0);

    data->_bodyBlockSize = bodyBlockSize;
    data->_firstHeaderBytes.resize(firstAlignedHeaderByte - headerBlockIndex);
    memcpy(&data->_firstHeaderBytes[0], buffer.getBuffer() + headerBlockIndex,
           data->_firstHeaderBytes.size());

    LOG(debug,
        "File %s header info: metaDataListSize=%u, "
        "headerBlockSize=%u, bodyBlockSize=%u",
        file.getFile().getPath().c_str(),
        data->_metaDataListSize,
        data->_headerBlockSize,
        data->_bodyBlockSize);

    ioBuf.setFileInfo(std::move(data));

    uint32_t metaEntriesRead(header->_metaDataListSize);
    bool foundBadSlot = false;
    uint32_t lastBadSlot = 0;
    SlotValidator validator(headerBlockOffset, bodyBlockOffset, fileSize);

    for (uint32_t i = 0; i < metaEntriesRead; ++i) {
        const MetaSlot* ms(reinterpret_cast<const MetaSlot*>(
                    buffer + sizeof(Header) + i * sizeof(MetaSlot)));

        if (!validator.slotHasValidInformation(*ms)) {
            foundBadSlot = true;
            lastBadSlot = i;
            continue; // Don't add bad slots.
        }

        if (!ms->inUse()) {
            break;
        }

        MemSlot slot(ms->_gid,
                     ms->_timestamp,
                     DataLocation(ms->_headerPos, ms->_headerSize),
                     DataLocation(ms->_bodyPos, ms->_bodySize),
                     ms->_flags,
                     ms->_checksum);

        file.addSlot(slot);
    }

    // We bail here instead of doing so inside the loop because this allows us
    // to add all healthy slots to the file prior to throwing the exception.
    // Any caller code that wants/need to inspect the good slots is then able
    // to do so. It is not a given that this is a strong requirement; the check
    // may be moved inside the loop if it can be established that no caller code
    // expects the good slots to be present after a loadFile exception.
    if (foundBadSlot) {
        std::ostringstream error;
        error << "Found bad slot in file '"
              << file.getFile().getPath()
              << "' at slot index " << lastBadSlot
              << ", forcing repair of file. Details of file "
                 "corruption to follow.";
        throw CorruptMemFileException(error.str(), file.getFile(),
                                      VESPA_STRLOC);
    }

    file.clearFlag(SLOTS_ALTERED);

    LOG(spam, "After loading file, its state is %s", file.toString(true).c_str());
}

void
MemFileV1Serializer::cacheLocationsForPart(SimpleMemFileIOBuffer& cache,
                                           DocumentPart part,
                                           uint32_t blockIndex,
                                           const std::vector<DataLocation>& locationsToCache,
                                           const std::vector<DataLocation>& locationsRead,
                                           SimpleMemFileIOBuffer::BufferAllocation& buf)
{
    vespalib::asciistream error;
    for (uint32_t i = 0; i < locationsToCache.size(); ++i) {
        DataLocation loc(locationsToCache[i]);
        assert(loc.valid());

        if (loc._size == 0) {
            LOG(spam, "Bailing since location size is 0");
            continue;
        }

        loc._pos += blockIndex;
        int32_t bufferPos = getBufferPos(loc, locationsRead);

        assert(bufferPos != -1);

        MemFileV1Verifier verifier;
        if (!verifier.verifyBlock(part, locationsToCache[i]._pos,
                                  error,
                                  buf.getBuffer() + bufferPos,
                                  loc._size))
        {
            throw CorruptMemFileException(
                    error.str(), cache.getFileSpec(), VESPA_STRLOC);
        }

        cache.cacheLocation(part,
                            locationsToCache[i],
                            buf.getSharedBuffer(),
                            buf.getBufferPosition() + bufferPos);
    }
}

void
MemFileV1Serializer::cacheLocations(MemFileIOInterface& io,
                                    Environment& env,
                                    const Options& options,
                                    DocumentPart part,
                                    const std::vector<DataLocation>& locations)
{
    SimpleMemFileIOBuffer& cache(static_cast<SimpleMemFileIOBuffer&>(io));

    const FileInfo& data(cache.getFileInfo());
    uint32_t blockStartIndex(part == HEADER
                             ? data.getHeaderBlockStartIndex()
                             : data.getBodyBlockStartIndex());

    LOG(spam, "%s: cacheLocations for %s with %zu locations. "
        "max read-through gap is %u",
        cache.getFileHandle().getFilename().c_str(),
        getDocumentPartName(part),
        locations.size(),
        options._maximumGapToReadThrough);

    LocationDiskIoPlanner planner(
            cache,
            part,
            locations,
            options._maximumGapToReadThrough,
            blockStartIndex);

    if (planner.getIoOperations().empty()) {
        LOG(spam, "%s: no disk read operations required for %zu %s locations",
            cache.getFileHandle().getFilename().c_str(),
            locations.size(),
            getDocumentPartName(part));
        return;
    }

    const std::vector<DataLocation>& readLocations(planner.getIoOperations());

    const size_t bufferSize = planner.getTotalBufferSize();
    assert(bufferSize % 512 == 0);
    const SimpleMemFileIOBuffer::SharedBuffer::Alignment align512(
            SimpleMemFileIOBuffer::SharedBuffer::ALIGN_512_BYTES);

    SimpleMemFileIOBuffer::BufferAllocation buf(
            cache.allocateBuffer(part, bufferSize, align512));
    assert(reinterpret_cast<size_t>(buf.getBuffer()) % 512 == 0);
    LOG(spam,
        "Allocated %u bytes with offset %u from shared buffer %p "
        "(of total %zu bytes, %zu bytes used, %zu bytes free)",
        buf.getSize(),
        buf.getBufferPosition(),
        buf.getSharedBuffer().get(),
        buf.getSharedBuffer()->getSize(),
        buf.getSharedBuffer()->getUsedSize(),
        buf.getSharedBuffer()->getFreeSize());

    framework::MilliSecTimer readTimer(env._clock);
    SerializationMetrics& metrics(getMetrics().serialization);

    uint64_t total(read(cache.getFileHandle(), buf.getBuffer(), readLocations));

    metrics::DoubleAverageMetric& latency(
            part == HEADER ? metrics.headerReadLatency
                           : metrics.bodyReadLatency);
    metrics::LongAverageMetric& sz(part == HEADER ? metrics.headerReadSize
                                                  : metrics.bodyReadSize);
    latency.addValue(readTimer.getElapsedTimeAsDouble());
    sz.addValue(total);

    framework::MilliSecTimer cacheUpdateTimer(env._clock);
    cacheLocationsForPart(cache, part, blockStartIndex, locations,
                          readLocations, buf);

    metrics.cacheUpdateAndImplicitVerifyLatency.addValue(
            cacheUpdateTimer.getElapsedTimeAsDouble());
}

uint64_t
MemFileV1Serializer::read(vespalib::LazyFile& file,
                          char* buf,
                          const std::vector<DataLocation>& readOps)
{
    uint32_t currPos = 0;
    uint64_t totalRead = 0;

    for (uint32_t i = 0; i < readOps.size(); i++) {
        file.read(buf + currPos, readOps[i]._size, readOps[i]._pos);
        currPos += readOps[i]._size;
        totalRead += readOps[i]._size;
    }
    return totalRead;
}

void
MemFileV1Serializer::ensureFormatSpecificDataSet(const MemFile& )
{
/*
    if (file.getFormatSpecificData() == 0) {
        assert(!file.fileExists());
        file.setFormatSpecificData(MemFile::FormatSpecificData::UP(new Data));
    }
*/
}

uint32_t
MemFileV1Serializer::writeMetaData(BufferedFileWriter& writer,
                                   const MemFile& file)
{
    const SimpleMemFileIOBuffer& ioBuf(
            static_cast<const SimpleMemFileIOBuffer&>(file.getMemFileIO()));
    uint32_t lastPos = writer.getFilePosition();
    const FileInfo& data(ioBuf.getFileInfo());

    // Create the header
    Header header;
    header._version = file.getCurrentVersion();
    header._metaDataListSize = data._metaDataListSize;
    header._headerBlockSize = data._headerBlockSize;
    header.updateChecksum();
    header._fileChecksum = file.getBucketInfo().getChecksum();
    writer.write(&header, sizeof(Header));
    for (uint32_t i=0, n=header._metaDataListSize; i<n; ++i) {
        MetaSlot meta;
        if (i < file.getSlotCount()) {
            const MemSlot& slot(file[i]);
            assert(i == 0 || (file[i].getTimestamp() 
                              > file[i-1].getTimestamp()));
            meta._timestamp = slot.getTimestamp();
            meta._gid = slot.getGlobalId();
            meta._flags = slot.getPersistedFlags();
            meta._headerPos = slot.getLocation(HEADER)._pos;
            meta._headerSize = slot.getLocation(HEADER)._size;
            meta._bodyPos = slot.getLocation(BODY)._pos;
            meta._bodySize = slot.getLocation(BODY)._size;
            meta.updateChecksum();
        }
        writer.write(&meta, sizeof(MetaSlot));
    }
    return (writer.getFilePosition() - lastPos);
}

// TODO: make exception safe
MemFileV1Serializer::FlushResult
MemFileV1Serializer::flushUpdatesToFile(MemFile& file, Environment& env)
{
    framework::MilliSecTimer totalWriteTimer(env._clock);
    MemFilePersistenceThreadMetrics& metrics(getMetrics());
    SerializationWriteMetrics& writeMetrics(metrics.serialization.partialWrite);
    SimpleMemFileIOBuffer& ioBuf(
            static_cast<SimpleMemFileIOBuffer&>(file.getMemFileIO()));
    const FileInfo& data(ioBuf.getFileInfo());
    BucketId bid(file.getFile().getBucketId());

    LOG(spam,
        "Attempting partial write of file %s",
        file.getFile().getPath().c_str());

    if (file.getSlotCount() > data._metaDataListSize) {
        LOG(debug,
            "Cannot do partial write of file %s as its "
            "in-memory slot count (%u) is greater than its "
            "persisted metadata list size (%u)",
            file.getFile().getPath().c_str(),
            file.getSlotCount(), data._metaDataListSize);
        return FlushResult::TooFewMetaEntries;
    }

    // TODO: replace this with multimap to avoid vector allocations
    // for every single unique location? Could potentially also use
    // a Boost.Intrusive rbtree with a pool-based allocation scheme
    // to avoid multiple allocations even for the nodes themselves.
    typedef MemFile::LocationMap LocationMap;
    LocationMap headersToWrite, bodiesToWrite;
    LocationMap existingHeaders, existingBodies;

    file.getLocations(headersToWrite, bodiesToWrite,
                      NON_PERSISTED_LOCATIONS);

    // We don't need the slot list for this, just using it to find a
    // gap in the file
    file.getLocations(existingHeaders, existingBodies,
                      PERSISTED_LOCATIONS | NO_SLOT_LIST);

    // Figure out total size of unwritten data for each part and
    // whether or not there exists a single continuous gap in the
    // part's block in which we can fit the data. Also keep track
    // of the total amount of data we actually use so we can check
    // if file should be downsized afterwards.
    uint32_t totalSpaceUsed[2] = { 0 };
    uint32_t maxUsedExtent[2] = { 0 };
    uint32_t bytesToWrite[2] = { 0 };

    for (uint32_t partId = 0; partId < 2; ++partId) {
        DocumentPart part(static_cast<DocumentPart>(partId));
        LocationMap& unwritten(part == HEADER ? headersToWrite : bodiesToWrite);
        LocationMap& existing(part == HEADER ? existingHeaders : existingBodies);

        for (LocationMap::iterator it(unwritten.begin()), e(unwritten.end());
             it != e; ++it)
        {
            bytesToWrite[partId] += it->first._size;
        }
        alignUp(bytesToWrite[partId]);
        for (LocationMap::iterator it(existing.begin()), e(existing.end());
             it != e; ++it)
        {
            totalSpaceUsed[partId] += it->first._size;
            maxUsedExtent[partId] = std::max(maxUsedExtent[partId],
                                             it->first._pos + it->first._size);
        }
        LOG(spam, "Max used %s extent before align: %u",
            getDocumentPartName(part),
            maxUsedExtent[partId]);

        assert(maxUsedExtent[partId] <= data.getBlockSize(part));
        alignUp(maxUsedExtent[partId]);

        if (maxUsedExtent[partId] > data.getBlockSize(part)
            || (bytesToWrite[partId]
                > (data.getBlockSize(part) - maxUsedExtent[partId])))
        {
            LOG(debug, "Could not find sufficient free space in %s to "
                "perform a partial write for %s. Only %u bytes available, "
                "but need at least %u bytes; rewriting entire file.",
                getDocumentPartName(part),
                file.getFile().getPath().c_str(),
                (data.getBlockSize(part) >= maxUsedExtent[partId]
                 ? data.getBlockSize(part) - maxUsedExtent[partId]
                 : 0),
                bytesToWrite[partId]);
            return FlushResult::TooSmall;
        }
    }
    if (LOG_WOULD_LOG(debug)) {
        for (int partId = 0; partId < 2; ++partId) {
            DocumentPart part(static_cast<DocumentPart>(partId));
            LOG(debug,
                "%s: block %s has totalSpaceUsed=%u, maxUsedExtent=%u "
                "bytesToWrite=%u blockIndex=%u blockSize=%u",
                bid.toString().c_str(),
                getDocumentPartName(part),
                totalSpaceUsed[part],
                maxUsedExtent[part],
                bytesToWrite[part],
                data.getBlockIndex(part),
                data.getBlockSize(part));
        }
    }
    // Verify not too much free space. Remember to include bytes to write
    // currently, and count free space forced added for alignment and to
    // overrepresent blocks as used.
    // TODO: are the overrepresent factors correct wrt. new data added?
    std::shared_ptr<const MemFilePersistenceConfig> memFileCfg;
    {
        auto guard = env.acquireConfigReadLock();
        memFileCfg = guard.memFilePersistenceConfig();
    }
    {
        uint32_t usedSpace = static_cast<uint32_t>(
                  sizeof(Header)
                + sizeof(MetaSlot) * file.getSlotCount()
                    * memFileCfg->overrepresentMetaDataFactor
                + totalSpaceUsed[HEADER]
                    * memFileCfg->overrepresentHeaderBlockFactor
                + totalSpaceUsed[BODY]
                + bytesToWrite[HEADER]
                + bytesToWrite[BODY]);
        alignUp(usedSpace, 0, memFileCfg->fileBlockSize);
        alignUp(usedSpace, 0, memFileCfg->minimumFileSize);
        if (double(usedSpace) / data.getFileSize() < memFileCfg->minFillRate) {
            LOG(debug, "File %s only uses %u of %u bytes (%f %%), which is "
                       "less than min fill rate of %f %%. "
                       "Resizing file to become smaller.",
                file.getFile().getPath().c_str(),
                usedSpace, data.getFileSize(),
                100.0 * usedSpace / data.getFileSize(),
                100.0 * memFileCfg->minFillRate);
            return FlushResult::TooLarge;
        }
    }
    // At this point, we've checked if we can downsize the file with
    // a no-go outcome. If there are no altered slots, we can safely
    // do an early exit here to avoid rewriting metadata needlessly.
    if (!file.slotsAltered()) {
        LOG(spam,
            "No slots in %s altered, returning without writing anything.",
            bid.toString().c_str());
        assert(bytesToWrite[HEADER] == 0);
        assert(bytesToWrite[BODY] == 0);
        return FlushResult::UnAltered;
    }

    // Persist dirty locations to disk, updating all slots as we go.
    // NOTE: it is assumed that the buffered data blocks contain pre-
    // serialized checksums, document ids etc as appropriate since
    // we only write the raw data to disk.
    Buffer buffer(1024 * 1024);
    BufferedFileWriter writer(ioBuf.getFileHandle(), buffer, buffer.getSize());

    for (uint32_t partId = 0; partId < 2; ++partId) {
        framework::MilliSecTimer writeTimer(env._clock);
        DocumentPart part(static_cast<DocumentPart>(partId));
        LocationMap& locations(part == HEADER ? headersToWrite : bodiesToWrite);

        uint32_t realPos = data.getBlockIndex(part) + maxUsedExtent[partId];
        alignUp(realPos);
        uint32_t pos = realPos - data.getBlockIndex(part);

        LOG(spam,
            "%s: writing data for part %d, index %d, max "
            "used extent %d, block size %d",
            bid.toString().c_str(),
            part,
            data.getBlockIndex(part),
            maxUsedExtent[partId],
            data.getBlockSize(part));

        writer.setFilePosition(realPos);
        for (LocationMap::iterator it(locations.begin()), e(locations.end());
             it != e; ++it)
        {
            uint32_t size = it->first._size;
            writer.write(ioBuf.getBuffer(it->first, part), size);
            DataLocation newSlotLocation(pos, size);
            ioBuf.persist(part, it->first, newSlotLocation);

            LOG(spam,
                "%s: wrote location %d,%d to disk, resulting location was %d,%d",
                bid.toString().c_str(),
                it->first._pos,
                it->first._size,
                newSlotLocation._pos,
                newSlotLocation._size);

            std::vector<const MemSlot*>& slots(it->second.slots);
            for (uint32_t j = 0; j < slots.size(); ++j) {
                LOG(spam, "%s: setting %s location for slot %s to %u,%u",
                    bid.toString().c_str(),
                    getDocumentPartName(part),
                    slots[j]->toString().c_str(),
                    newSlotLocation._pos,
                    newSlotLocation._size);
                MapperSlotOperation::setLocation(*slots[j], part, newSlotLocation);
            }
            pos += size;
        }
        pos = writer.getFilePosition();
        alignUp(pos);
        assert(part == BODY || pos <= data.getBlockIndex(BODY));
        writer.writeGarbage(pos - writer.getFilePosition());
        
        metrics::DoubleAverageMetric& latency(
                part == HEADER ? writeMetrics.headerLatency
                               : writeMetrics.bodyLatency);
        metrics::LongAverageMetric& sz(
                part == HEADER ? writeMetrics.headerSize
                               : writeMetrics.bodySize);
        latency.addValue(writeTimer.getElapsedTimeAsDouble());
        sz.addValue(bytesToWrite[part]);
    }

    framework::MilliSecTimer metaWriteTimer(env._clock);
    // Write metadata back to file
    writer.setFilePosition(0);
    writeMetaData(writer, file);
    writer.write(&data._firstHeaderBytes[0], data._firstHeaderBytes.size());
    writer.flush();
    MapperSlotOperation::clearFlag(file, SLOTS_ALTERED);

    writeMetrics.metaLatency.addValue(metaWriteTimer.getElapsedTimeAsDouble());
    writeMetrics.totalLatency.addValue(totalWriteTimer.getElapsedTimeAsDouble());
    writeMetrics.metaSize.addValue(writer.getFilePosition());
    return FlushResult::ChangesWritten;
}

namespace {
    uint32_t
    getMetaSlotCount(uint32_t usedSlotCount,
                     const FileSpecification& file,
                     const MemFilePersistenceConfig& cfg,
                     const Options& options)
    {
        uint32_t wanted = static_cast<uint32_t>(
                usedSlotCount * options._growFactor
                * options._overrepresentMetaDataFactor);
        if (wanted < uint32_t(cfg.minimumFileMetaSlots)) {
            wanted = cfg.minimumFileMetaSlots;
        }
        if (wanted > uint32_t(cfg.maximumFileMetaSlots)) {
            if (uint32_t(cfg.maximumFileMetaSlots) >= usedSlotCount) {
                wanted = cfg.maximumFileMetaSlots;
            } else {
                std::ostringstream ost;
                ost << "Need " << usedSlotCount << " slots and want "
                    << wanted << " slots in file, but max slots is "
                    << cfg.maximumFileMetaSlots;
                throw MemFileIoException(
                        ost.str(), file, MemFileIoException::FILE_FULL,
                        VESPA_STRLOC);
            }
        }
        return wanted;
    }

    uint32_t
    getHeaderBlockSize(uint32_t minBytesNeeded,
                       uint32_t startBlockIndex,
                       const FileSpecification& file,
                       const MemFilePersistenceConfig& cfg,
                       const Options& options)
    {
        uint32_t wanted = static_cast<uint32_t>(
                minBytesNeeded * options._growFactor
                * options._overrepresentHeaderBlockFactor);
        if (wanted < uint32_t(cfg.minimumFileHeaderBlockSize)) {
            wanted = cfg.minimumFileHeaderBlockSize;
        }
        if (wanted > uint32_t(cfg.maximumFileHeaderBlockSize)) {
            if (uint32_t(cfg.maximumFileHeaderBlockSize)
                    >= minBytesNeeded)
            {
                wanted = cfg.maximumFileHeaderBlockSize;
            } else {
                std::ostringstream ost;
                ost << "Need " << minBytesNeeded << " header bytes and want "
                    << wanted << " header bytes in file, but max is "
                    << cfg.maximumFileHeaderBlockSize;
                throw MemFileIoException(
                        ost.str(), file, MemFileIoException::FILE_FULL,
                        VESPA_STRLOC);
            }
        }
        alignUp(wanted, startBlockIndex);
        return wanted;
    }

    uint32_t
    getBodyBlockSize(uint32_t minBytesNeeded,
                     uint32_t startBlockIndex,
                     const FileSpecification& file,
                     const MemFilePersistenceConfig& cfg,
                     const Options& options)
    {
        assert(startBlockIndex % 512 == 0);
        uint32_t wanted = static_cast<uint32_t>(
                minBytesNeeded * options._growFactor);
        if (wanted + startBlockIndex < uint32_t(cfg.minimumFileSize)) {
            wanted = cfg.minimumFileSize - startBlockIndex;
        }
        if (wanted + startBlockIndex > uint32_t(cfg.maximumFileSize)) {
            if (uint32_t(cfg.maximumFileSize)
                    >= minBytesNeeded + startBlockIndex)
            {
                wanted = cfg.maximumFileSize - startBlockIndex;
            } else {
                std::ostringstream ost;
                ost << "Need " << minBytesNeeded << " body bytes and want "
                    << wanted << " body bytes in file, but max is "
                    << (cfg.maximumFileSize - startBlockIndex)
                    << " as the body block starts at index " << startBlockIndex;
                throw MemFileIoException(
                        ost.str(), file, MemFileIoException::FILE_FULL,
                        VESPA_STRLOC);
            }
        }
        alignUp(wanted, startBlockIndex, cfg.fileBlockSize);
        return wanted;
    }

    struct TempCache : public BufferedFileWriter::Cache {
        uint32_t _headerBlockIndex;
        std::vector<char> _buffer;

        TempCache(uint32_t headerBlockIndex)
            : _headerBlockIndex(headerBlockIndex),
              _buffer()
        {
            uint32_t firstAligned = _headerBlockIndex;
            alignUp(firstAligned);
            _buffer.resize(firstAligned - _headerBlockIndex);
        }

        uint32_t getCachedAmount() const override { return _buffer.size() + _headerBlockIndex; }
        char* getCache(uint32_t pos) override {
                // We should never get requests to write prior to header block
                // index.
            assert(pos >= _headerBlockIndex);
            return (&_buffer[0] + (pos - _headerBlockIndex));
        }

        bool duplicateCacheWrite() const override { return true; }
        void setData(const char* data, size_t len, uint64_t pos) override {
            if (pos < _headerBlockIndex) {
                if (len <= _headerBlockIndex - pos) return;
                uint32_t diff = (_headerBlockIndex - pos);
                len -= diff;
                pos += diff;
                data += diff;
            }
            Cache::setData(data, len, pos);
        }
    };

}

// Iterate and write locations in timestamp order. Keep track of what
// locations have already been written and what their new location
// is in the rewritten file. Returns total number of bytes written
// for all unique locations. Modifies slot locations in-place in MemFile.
uint32_t
MemFileV1Serializer::writeAndUpdateLocations(
        MemFile& file,
        SimpleMemFileIOBuffer& ioBuf,
        BufferedFileWriter& writer,
        DocumentPart part,
        const MemFile::LocationMap& locationsToWrite,
        const Environment& env)
{
    framework::MilliSecTimer timer(env._clock);
    BucketId bid(file.getFile().getBucketId());
    std::map<DataLocation, DataLocation> writtenLocations;
    uint32_t index = 0;
    for (uint32_t i = 0; i < file.getSlotCount(); ++i) {
        const MemSlot& slot(file[i]);

        DataLocation originalLoc(slot.getLocation(part));
        if (originalLoc._size == 0) {
            LOG(spam, "Slot %s has empty %s, not writing anything",
                slot.toString().c_str(),
                getDocumentPartName(part));
            assert(originalLoc._pos == 0);
            continue;
        }

        MemFile::LocationMap::const_iterator it(
                locationsToWrite.find(originalLoc));
        assert(it != locationsToWrite.end());
        std::map<DataLocation, DataLocation>::iterator written(
                writtenLocations.find(originalLoc));

        DataLocation loc;
        if (written == writtenLocations.end()) {
            uint32_t size = it->first._size;
            loc = DataLocation(index, size);

            LOG(spam, "%s: writing %s for slot %s to location (%u, %u)",
                file.getFile().getBucketId().toString().c_str(),
                getDocumentPartName(part),
                slot.toString().c_str(),
                index, size);

            writer.write(ioBuf.getBuffer(originalLoc, part), size);
            index += size;
            writtenLocations[originalLoc] = loc;
        } else {
            LOG(spam, "%s: %s already written for slot %s; "
                "updating to location (%u, %u)",
                file.getFile().getBucketId().toString().c_str(),
                getDocumentPartName(part),
                slot.toString().c_str(),
                written->second._pos, written->second._size);
            loc = written->second;
        }
        assert(loc.valid());
        MapperSlotOperation::setLocation(slot, part, loc);
    }
    // Move in cache. Cannot be done inside loop.
    ioBuf.remapAndPersistAllLocations(part, writtenLocations);

    SerializationWriteMetrics& writeMetrics(
            getMetrics().serialization.fullWrite);
    metrics::DoubleAverageMetric& latency(
            part == HEADER ? writeMetrics.headerLatency
                           : writeMetrics.bodyLatency);
    metrics::LongAverageMetric& sz(
            part == HEADER ? writeMetrics.headerSize
                           : writeMetrics.bodySize);
    latency.addValue(timer.getElapsedTimeAsDouble());
    sz.addValue(index); // Equal to written size.

    return index;
}

void
MemFileV1Serializer::rewriteFile(MemFile& file, Environment& env)
{
    framework::MilliSecTimer totalWriteTimer(env._clock);
    SerializationWriteMetrics& writeMetrics(
            getMetrics().serialization.fullWrite);
    file.ensureHeaderAndBodyBlocksCached();

    SimpleMemFileIOBuffer& ioBuf(
            static_cast<SimpleMemFileIOBuffer&>(file.getMemFileIO()));

    const FileSpecification& oldSpec(file.getFile());
    std::string newPath = oldSpec.getPath() + ".new";

    LOG(debug, "Rewriting entire file %s", oldSpec.getPath().c_str());
    ioBuf.getFileHandle().close();
    vespalib::LazyFile::UP newFile = env.createFile(newPath);
    newFile->open(ioBuf.getFileHandle().getFlags()
                 | vespalib::File::CREATE | vespalib::File::TRUNC, true);
    MapperSlotOperation::setFlag(file, FILE_EXIST);

    FileInfo::UP data(new FileInfo);
    Buffer buffer(32 * 1024 * 1024);
    BufferedFileWriter writer(*newFile, buffer, buffer.getSize());

    std::shared_ptr<const MemFilePersistenceConfig> memFileCfg;
    std::shared_ptr<const Options> options;
    {
        auto guard = env.acquireConfigReadLock();
        memFileCfg = guard.memFilePersistenceConfig();
        options = guard.options();
    }

    // Create the header
    Header header;
    header._version = getFileVersion();
    header._metaDataListSize = getMetaSlotCount(
            file.getSlotCount(), file.getFile(), *memFileCfg, *options);
    data->_metaDataListSize = header._metaDataListSize;
    header._fileChecksum = file.getBucketInfo().getChecksum();

    // Dump header and metadata to writer, so we can start writing header
    // and bodies. If buffer is too small causing this to be written, we
    // need to write it again after updating it.
    writer.write(&header, sizeof(Header));
    LOG(spam, "Writing garbage for %u meta entries",
        header._metaDataListSize);
    writer.writeGarbage(sizeof(MetaSlot) * header._metaDataListSize);

    TempCache tempCache(writer.getFilePosition());
    writer.setMemoryCache(&tempCache);

    typedef MemFile::LocationMap LocationMap;
    LocationMap headersToWrite, bodiesToWrite;
    // Don't need the slot list, we update that implicitly
    file.getLocations(headersToWrite, bodiesToWrite,
                      PERSISTED_LOCATIONS
                      | NON_PERSISTED_LOCATIONS
                      | NO_SLOT_LIST);

    uint32_t headerIndex = writeAndUpdateLocations(
            file, ioBuf, writer, HEADER, headersToWrite, env);

    header._headerBlockSize = getHeaderBlockSize(
            headerIndex,
            data->getHeaderBlockStartIndex(),
            file.getFile(),
            *memFileCfg,
            *options);
    header._checksum = header.calcHeaderChecksum();
    data->_headerBlockSize = header._headerBlockSize;

    if (headerIndex < header._headerBlockSize) {
        LOG(spam, "Writing %u bytes of header garbage filler",
            header._headerBlockSize - headerIndex);
        writer.writeGarbage(header._headerBlockSize - headerIndex);
    }

    uint32_t bodyIndex = writeAndUpdateLocations(
            file, ioBuf, writer, BODY, bodiesToWrite, env);

    data->_bodyBlockSize = getBodyBlockSize(
            bodyIndex,
            data->getBodyBlockStartIndex(),
            file.getFile(),
            *memFileCfg,
            *options);
    if (bodyIndex < data->_bodyBlockSize) {
        writer.writeGarbage(data->_bodyBlockSize - bodyIndex);
    }

    framework::MilliSecTimer metaWriteTimer(env._clock);
    // Update meta entries
    std::vector<MetaSlot> writeSlots(header._metaDataListSize);

    for (uint32_t i = 0; i < file.getSlotCount(); ++i) {
        const MemSlot& slot(file[i]);
        MetaSlot& meta(writeSlots[i]);

        DataLocation headerLoc = slot.getLocation(HEADER);
        assert(headerLoc.valid());
        DataLocation bodyLoc = slot.getLocation(BODY);
        assert(bodyLoc.valid());
        assert(i == 0 || (file[i].getTimestamp() > file[i - 1].getTimestamp()));

        meta._timestamp = slot.getTimestamp();
        meta._gid = slot.getGlobalId();
        meta._flags = slot.getPersistedFlags();
        meta._headerPos = headerLoc._pos;
        meta._headerSize = headerLoc._size;
        meta._bodyPos = bodyLoc._pos;
        meta._bodySize = bodyLoc._size;
        assert(meta.inUse());

        meta.updateChecksum();
        MapperSlotOperation::setChecksum(slot, meta._checksum);
    }

    if (writer.getWriteCount() != 0) {
        // If we didn't have large enough buffer to hold entire file, reposition
        // to start to write meta data after updates.
        writer.setFilePosition(0);
        writer.write(&header, sizeof(Header));
        writer.write(&writeSlots[0], writeSlots.size() * sizeof(MetaSlot));
        writer.write(&tempCache._buffer[0], tempCache._buffer.size());
    } else {
        // Otherwise, just update the content in the write buffer.
        memcpy(buffer, &header, sizeof(Header));
        memcpy(buffer + sizeof(Header),
               &writeSlots[0], writeSlots.size() * sizeof(MetaSlot));
    }

    writer.flush();
    data->_firstHeaderBytes.swap(tempCache._buffer);
    int64_t sizeDiff = 0;
    if (file.getFormatSpecificData() != 0) {
        sizeDiff = ioBuf.getFileInfo().getFileSize();
    }
    sizeDiff = static_cast<int64_t>(data->getFileSize()) - sizeDiff;

    //file.setFormatSpecificData(MemFile::FormatSpecificData::UP(data.release()));
    ioBuf.setFileInfo(std::move(data));
    file.setCurrentVersion(TRADITIONAL_SLOTFILE);
    newFile->close();
    vespalib::rename(newPath, oldSpec.getPath());

    ioBuf.getFileHandle().open(
            ioBuf.getFileHandle().getFlags(),
            true);

    // Update partitionmonitor with size usage.
    PartitionMonitor* partitionMonitor(
            file.getFile().getDirectory().getPartition().getMonitor());
    if (partitionMonitor == 0) {
        // Only report if monitor exist.
    } else if (sizeDiff > 0) {
        partitionMonitor->addingData(static_cast<uint32_t>(sizeDiff));
    } else if (sizeDiff < 0) {
        partitionMonitor->removingData(static_cast<uint32_t>(-1 * sizeDiff));
    }
    MapperSlotOperation::clearFlag(file, SLOTS_ALTERED);

    writeMetrics.metaLatency.addValue(metaWriteTimer.getElapsedTimeAsDouble());
    writeMetrics.totalLatency.addValue(totalWriteTimer.getElapsedTimeAsDouble());
    writeMetrics.metaSize.addValue(sizeof(MetaSlot) * header._metaDataListSize);
}

bool
MemFileV1Serializer::verify(MemFile& file, Environment& env,
                            std::ostream& reportStream,
                            bool repairErrors, uint16_t fileVerifyFlags)
{
    MemFileV1Verifier verifier;
    SerializationMetrics& metrics(getMetrics().serialization);
    framework::MilliSecTimer timer(env._clock);
    
    bool ok(verifier.verify(file, env, reportStream, repairErrors, fileVerifyFlags));

    metrics.verifyLatency.addValue(timer.getElapsedTimeAsDouble());
    return ok;
}

}
