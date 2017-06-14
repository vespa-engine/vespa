// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memfile.h"
#include "memfilecompactor.h"
#include "shared_data_location_tracker.h"
#include <vespa/memfilepersistence/mapper/memfilemapper.h>
#include <vespa/memfilepersistence/mapper/simplememfileiobuffer.h>
#include <vespa/memfilepersistence/common/environment.h>
#include <vespa/memfilepersistence/common/exceptions.h>
#include <vespa/document/util/stringutil.h>
#include <vespa/vespalib/util/crc.h>
#include <ext/algorithm>
#include <iomanip>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".persistence.memfile.memfile");

namespace {

template<class A>
std::vector<A> toVector(A entry) {
    std::vector<A> entries;
    entries.push_back(entry);
    return entries;
};

}

#define FAIL_INCONSISTENT(msg, slot) \
{ \
    std::ostringstream error; \
    error << msg; \
    throw InconsistentSlotException(slot.toString() + ": " + error.str(), \
                                    _file, slot, VESPA_STRLOC); \
}
#define FAIL_INCONSISTENT_FILE(msg) \
{ \
    std::ostringstream error; \
    error << msg; \
    throw InconsistentException(error.str(), _file, VESPA_STRLOC); \
}

#define RETHROW_NON_MEMFILE_EXCEPTIONS \
    catch (MemFileException& exceptionToRethrow) { \
        throw; \
    } catch (vespalib::IoException& exceptionToRethrow) { \
        std::ostringstream wrappedMessage; \
        wrappedMessage << "Got IO exception while processing within " \
                       << "memfile. Wrapping in memfile exception: "; \
        const std::string& sourceExceptionMessage( \
                exceptionToRethrow.getMessage()); \
        size_t pos = sourceExceptionMessage.find(':'); \
        wrappedMessage << sourceExceptionMessage.substr(pos + 2); \
        throw MemFileIoException(wrappedMessage.str(), _file, \
                                 exceptionToRethrow.getType(), VESPA_STRLOC) \
                .setCause(exceptionToRethrow); \
    } catch (vespalib::Exception& exceptionToRethrow) { \
        throw MemFileWrapperException( \
                "Got generic exception while processing within " \
                "memfile. Wrapping in memfile exception: " \
                + std::string(exceptionToRethrow.getMessage()), \
                _file, VESPA_STRLOC).setCause(exceptionToRethrow); \
    }

namespace storage {
namespace memfile {

MemFile::MemFile(const FileSpecification& file,
                 Environment& env,
                 const LoadOptions& opts)
    : _flags(BUCKET_INFO_OUTDATED),
      _info(),
      _entries(),
      _file(file),
      _currentVersion(UNKNOWN),
      _env(env)
{
    try{
        env._memFileMapper.loadFile(*this, env, opts.autoRepair);
    } RETHROW_NON_MEMFILE_EXCEPTIONS;
}

MemFile::~MemFile() {}

MemFile::MemFile(const FileSpecification& file, Environment& env,
                 bool callLoadFile)
    : _flags(BUCKET_INFO_OUTDATED),
      _info(),
      _entries(),
      _file(file),
      _currentVersion(UNKNOWN),
      _env(env)
{
    if (callLoadFile) {
        env._memFileMapper.loadFile(*this, env, false);
    }
}

void
MemFile::verifyConsistent() const
{
    _buffer->verifyConsistent();
}

uint16_t
MemFile::getDisk() const
{
    return _file.getDirectory().getIndex();
}

void
MemFile::move(const FileSpecification& file)
{
    // Any given bucket can either be moved to a more specific or less
    // specific bucket in the same subtree.
    assert(file.getBucketId().contains(_file.getBucketId())
           || _file.getBucketId().contains(file.getBucketId()));
    _buffer->move(file);
    _file = file;
}

uint32_t
MemFile::getSlotCount() const
{
    return _entries.size();
}

const MemSlot*
MemFile::getSlotWithId(const document::DocumentId& id,
                       framework::MicroSecTime maxTimestamp) const
{
    for (uint32_t n=_entries.size(), i=n-1; i<n; --i) {
        if (_entries[i].getTimestamp() > maxTimestamp) continue;
        if (id.getGlobalId() != _entries[i].getGlobalId()) continue;
        if (getDocumentId(_entries[i]) == id) return &_entries[i];
    }
    return 0;
}

namespace {

struct MemSlotTimestampPredicate
{
    bool operator()(const MemSlot& a, Types::Timestamp time) const
    {
        return a.getTimestamp() < time;
    }
};

}

const MemSlot*
MemFile::getSlotAtTime(Timestamp time) const
{
    std::vector<MemSlot>::const_iterator it(
            std::lower_bound(_entries.begin(), _entries.end(),
                             time, MemSlotTimestampPredicate()));
    if (it != _entries.end() && it->getTimestamp() == time) {
        return &*it;
    }
    return 0;
}

void
MemFile::getSlotsByTimestamp(
        const std::vector<Timestamp>& timestamps,
        std::vector<const MemSlot*>& returned) const
{
    assert(__gnu_cxx::is_sorted(timestamps.begin(), timestamps.end()));

    std::size_t source = 0;
    std::size_t target = 0;

    while (source < _entries.size() && target < timestamps.size()) {
        if (_entries[source].getTimestamp() == timestamps[target]) {
            returned.push_back(&_entries[source]);
            ++source;
            ++target;
        } else if (_entries[source].getTimestamp() < timestamps[target]) {
            ++source;
        } else {
            ++target;
        }
    }
}

document::Document::UP
MemFile::getDocument(const MemSlot& slot, GetFlag getFlag) const
{
    LOG(spam,
        "%s: getDocument(%s, %s)",
        _file.getBucketId().toString().c_str(),
        slot.toString().c_str(),
        getFlag == HEADER_ONLY ? "header only" : "full document");
    ensureDocumentCached(slot, getFlag == HEADER_ONLY);

    auto& repo = _env.repo();
    Document::UP doc = _buffer->getDocumentHeader(
            repo, slot.getLocation(HEADER));

    if (doc.get() && getFlag == ALL && slot.getLocation(BODY)._size > 0) {
        _buffer->readBody(repo, slot.getLocation(BODY), *doc);
    }

    return doc;
}

document::DocumentId
MemFile::getDocumentId(const MemSlot& slot) const
{
    LOG(spam,
        "%s: getDocumentId(%s)",
        _file.getBucketId().toString().c_str(),
        slot.toString().c_str());
    ensureDocumentCached(slot, true);

    return _buffer->getDocumentId(slot.getLocation(HEADER));
}

void
MemFile::assertSlotContainedInThisBucket(const MemSlot& slot) const
{
    document::BucketId fileBucket(getBucketId());
    // Non-orderdoc documents should pass this first (very cheap) test.
    if (slot.getGlobalId().containedInBucket(fileBucket)) {
        return;
    }
    // Expensive path: get doc id and check against it instead.
    DocumentId id(getDocumentId(slot));
    document::BucketIdFactory factory;
    document::BucketId slotBucket(factory.getBucketId(id));

    LOG(spam,
        "%s: slot %s has GID not contained in bucket, checking against id %s",
        fileBucket.toString().c_str(),
        slot.toString().c_str(),
        id.toString().c_str());

    if (!fileBucket.contains(slotBucket)) {
        LOG(error,
            "Slot %s with document ID %s is not contained in %s. Terminating "
            "in order to avoid bucket corruption.",
            slot.toString().c_str(),
            id.toString().c_str(),
            fileBucket.toString().c_str());
        assert(false);
    }
}

void
MemFile::addPutSlot(const Document& doc, Timestamp time)
{
    DataLocation headerLoc = _buffer->addHeader(doc);
    DataLocation bodyLoc = _buffer->addBody(doc);

    addSlot(MemSlot(doc.getId().getGlobalId(),
                    time,
                    headerLoc,
                    bodyLoc,
                    IN_USE | CHECKSUM_OUTDATED,
                    0));
}

void
MemFile::addUpdateSlot(const Document& header, const MemSlot& body, Timestamp time)
{
    if (!body.getLocation(BODY).valid()) {
        LOG(error,
            "Slot %s has invalid body location while not "
            "having body cached. This is an invalid state.",
            body.toString().c_str());
        assert(false);
    }

    DataLocation headerLoc = _buffer->addHeader(header);
    DataLocation bodyLoc = body.getLocation(BODY);

    addSlot(MemSlot(header.getId().getGlobalId(),
                    time,
                    headerLoc,
                    bodyLoc,
                    IN_USE | CHECKSUM_OUTDATED,
                    0));
}

void
MemFile::addRemoveSlot(const MemSlot& header, Timestamp time)
{
    addSlot(MemSlot(header.getGlobalId(),
                    time,
                    header.getLocation(HEADER),
                    DataLocation(0,0),
                    DELETED | IN_USE | CHECKSUM_OUTDATED,
                    0));
}

void
MemFile::addRemoveSlotForNonExistingEntry(const DocumentId& docId,
                                          Timestamp time,
                                          RemoveType removeType)
{
    addSlot(MemSlot(docId.getGlobalId(),
                    time,
                    _buffer->addDocumentIdOnlyHeader(docId, _env.repo()),
                    DataLocation(0,0),
                    DELETED
                    | IN_USE
                    | CHECKSUM_OUTDATED
                    | (removeType == UNREVERTABLE_REMOVE ? DELETED_IN_PLACE : 0),
                    0));
}

void
MemFile::addSlot(const MemSlot& slot)
{
    LOG(spam,
        "%s: adding %s to memfile",
        _file.getBucketId().toString().c_str(),
        slot.toString().c_str());
    // TODO: Add exception here?
    //assert(slot.partAvailable(BODY));
    assert(slot.getLocation(HEADER).valid());
    assert(slot.getLocation(BODY).valid());
    // Don't let full disk block remove entries or entries that
    // are already fully persisted

    if (!slot.deleted()
        && !slot.deletedInPlace()
        && !(partPersisted(slot, HEADER)
             && partPersisted(slot, BODY)))
    {
        verifyDiskNotFull();
    }

    // Optimize common case where slot we're adding has a higher
    // timestamp than the last slot already stored.
    if (!_entries.empty()
        && slot.getTimestamp() > _entries.back().getTimestamp())
    {
        _flags |= BUCKET_INFO_OUTDATED | SLOTS_ALTERED;
        _entries.push_back(slot);
        return;
    }

    std::vector<MemSlot> entries;
    entries.reserve(_entries.size() + 1);
    bool inserted = false;
    for (uint32_t i=0; i<_entries.size(); ++i) {
        if (_entries[i].getTimestamp() == slot.getTimestamp()) {
            std::ostringstream err;
            err << "Attempt of adding slot at timestamp "
                << slot.getTimestamp() << " which already exist in file. "
                << "Call modifySlot instead.";
            LOG(error, "%s", err.str().c_str());
            assert(false);
        }
        if (!inserted && _entries[i].getTimestamp() > slot.getTimestamp()) {
            inserted = true;
            entries.push_back(slot);
        }
        entries.push_back(_entries[i]);
    }
    if (!inserted) {
        entries.push_back(slot);
    }
    _flags |= BUCKET_INFO_OUTDATED | SLOTS_ALTERED;
    _entries.swap(entries);
}

void
MemFile::copySlot(const MemFile& source, const MemSlot& slot)
{
    addSlot(MemSlot(slot.getGlobalId(),
                    slot.getTimestamp(),
                    _buffer->copyCache(*source._buffer, HEADER, slot.getLocation(HEADER)),
                    _buffer->copyCache(*source._buffer, BODY, slot.getLocation(BODY)),
                    slot.getFlags(),
                    slot.getChecksum()));
}

class MemFile::MemFileBufferCacheCopier : public BufferCacheCopier
{
public:
    MemFileBufferCacheCopier(MemFile& target, const MemFile& source)
        : _target(target),
          _source(source)
    {
    }

private:
    DataLocation doCopyFromSourceToLocal(
            Types::DocumentPart part,
            DataLocation sourceLocation) override
    {
        return _target._buffer->copyCache(
                *_source._buffer, part, sourceLocation);
    }

    MemFile& _target;
    const MemFile& _source;
};

void
MemFile::copySlotsFrom(
        const MemFile& source,
        const std::vector<const MemSlot*>& sourceSlots)
{
    // TODO we probably want a pre-allocation hint here to avoid many mmaps
    MemFileBufferCacheCopier cacheCopier(*this, source);
    SharedDataLocationTracker headerTracker(cacheCopier, HEADER);
    SharedDataLocationTracker bodyTracker(cacheCopier, BODY);

    for (auto slot : sourceSlots) {
        auto headerLoc = headerTracker.getOrCreateSharedLocation(
                slot->getLocation(HEADER));
        auto bodyLoc = bodyTracker.getOrCreateSharedLocation(
                slot->getLocation(BODY));
        addSlot(MemSlot(slot->getGlobalId(),
                        slot->getTimestamp(),
                        headerLoc,
                        bodyLoc,
                        slot->getFlags(),
                        slot->getChecksum()));
    }
}

void
MemFile::removeSlot(const MemSlot& slot)
{
    _flags |= BUCKET_INFO_OUTDATED | SLOTS_ALTERED;
    std::vector<MemSlot>::iterator it(
            std::lower_bound(_entries.begin(), _entries.end(),
                             slot.getTimestamp(),
                             MemSlotTimestampPredicate()));
    if (it != _entries.end()
        && it->getTimestamp() == slot.getTimestamp())
    {
        _entries.erase(it);
    } else {
        LOG(error,
            "Attempted to remove a slot that does not exist: %s",
            slot.toString().c_str());
        assert(false);
    }
}

void
MemFile::removeSlots(const std::vector<const MemSlot*>& slotsToRemove)
{
    if (slotsToRemove.empty()) return;
        // Optimized way of removing slots. Should not throw exceptions,
        // (and is not exception safe)
    std::vector<MemSlot> slots(
            _entries.size() - slotsToRemove.size(),
            MemSlot(GlobalId(), Timestamp(0), DataLocation(), DataLocation(),
                    0, 0));
    uint32_t r=0;
    for (uint32_t i=0,j=0; i<_entries.size(); ++i) {
        if (r >= slotsToRemove.size() || slotsToRemove[r] != &_entries[i]) {
            _entries[i].swap(slots[j]);
            ++j;
        } else {
            ++r;
        }
    }
    _entries.swap(slots);
    if (_entries.size() != slots.size()) {
        _flags |= BUCKET_INFO_OUTDATED | SLOTS_ALTERED;
    }
        // Verify that we found all slots to remove
    if (r < slotsToRemove.size()) {
        Timestamp ts(0);
        for (uint32_t i=0; i<slotsToRemove.size(); ++i) {
            assert(slotsToRemove[i]->getTimestamp() > ts);
            ts = slotsToRemove[i]->getTimestamp();
        }
        LOG(error,
            "Slot %s wasn't in the file. Only existing slots may be "
            "given to removeSlots as non-existing slot stops other "
            "slots from being removed.",
            slotsToRemove[r]->toString().c_str());
        assert(false);
    }
}

void
MemFile::modifySlot(const MemSlot& slot)
{
    _flags |= BUCKET_INFO_OUTDATED | SLOTS_ALTERED;
    // MemSlot actually pointed to by const MemSlot* is non-const
    // in entries-vector, so this should be well defined according
    // to the C++ ISO standard
    MemSlot* slotToModify = const_cast<MemSlot*>(
            getSlotAtTime(slot.getTimestamp()));

    assert(slotToModify != NULL);

    LOG(spam, "Modifying %s -> %s",
        slotToModify->toString().c_str(),
        slot.toString().c_str());
    *slotToModify = slot;
}

void
MemFile::matchLocationWithFlags(LocationMap& result,
                                DocumentPart part,
                                const MemSlot* slot,
                                uint32_t flags) const
{
    DataLocation loc = slot->getLocation(part);
    bool isPersisted = _buffer->isPersisted(loc, part);

    if ((flags & NON_PERSISTED_LOCATIONS) && !isPersisted) {
        result[loc].slots.push_back(slot);
    } else if ((flags & PERSISTED_LOCATIONS) && isPersisted) {
        result[loc].slots.push_back(slot);
    }
}

void
MemFile::getLocations(LocationMap& headers,
                      LocationMap& bodies,
                      uint32_t flags) const
{
    for (uint32_t i = 0; i < _entries.size(); ++i) {
        matchLocationWithFlags(headers, HEADER, &_entries[i], flags);
        matchLocationWithFlags(bodies, BODY, &_entries[i], flags);
    }
}

bool
MemFile::compact()
{
    auto options = _env.acquireConfigReadLock().options();
    MemFileCompactor compactor(
            _env._clock.getTimeInMicros(),
            CompactionOptions()
                .revertTimePeriod(options->_revertTimePeriod)
                .keepRemoveTimePeriod(options->_keepRemoveTimePeriod)
                .maxDocumentVersions(options->_maxDocumentVersions));
    std::vector<const MemSlot*> slotsToRemove(
            compactor.getSlotsToRemove(*this));
    removeSlots(slotsToRemove);
    return !slotsToRemove.empty();
}

MemFile::const_iterator
MemFile::begin(uint32_t iteratorFlags,
               Timestamp fromTimestamp,
               Timestamp toTimestamp) const
{
    if (iteratorFlags & ITERATE_GID_UNIQUE) {
        return const_iterator(SlotIterator::CUP(new GidUniqueSlotIterator(
                *this, iteratorFlags & ITERATE_REMOVED,
                fromTimestamp, toTimestamp)));
    } else {
        return const_iterator(SlotIterator::CUP(new AllSlotsIterator(
                *this, iteratorFlags & ITERATE_REMOVED,
                fromTimestamp, toTimestamp)));
    }
}

void
MemFile::ensureDocumentIdCached(const MemSlot& slot) const
{
    _buffer->ensureCached(_env, HEADER, toVector(slot.getLocation(HEADER)));
}

void
MemFile::ensureDocumentCached(const MemSlot& slot, bool headerOnly) const
{
    _buffer->ensureCached(_env, HEADER, toVector(slot.getLocation(HEADER)));
    if (!headerOnly) {
        _buffer->ensureCached(_env, BODY, toVector(slot.getLocation(BODY)));
    }
}

void
MemFile::ensureDocumentCached(const std::vector<Timestamp>& timestamps,
                              bool headerOnly) const
{
    LOG(spam, "ensureDocumentCached with %zu timestamps",
        timestamps.size());
    if (!fileExists()) {
        return;
    }
    try{
        std::vector<const MemSlot*> slots;
        getSlotsByTimestamp(timestamps, slots);

        std::vector<DataLocation> headerLocations;
        headerLocations.reserve(timestamps.size());
        std::vector<DataLocation> bodyLocations;
        if (!headerOnly) {
            bodyLocations.reserve(timestamps.size());
        }
        for (uint32_t i = 0; i < slots.size(); ++i) {
            headerLocations.push_back(slots[i]->getLocation(HEADER));

            if (!headerOnly) {
                bodyLocations.push_back(slots[i]->getLocation(BODY));
            }
        }

        _buffer->ensureCached(_env, HEADER, headerLocations);
        if (!headerOnly) {
            _buffer->ensureCached(_env, BODY, bodyLocations);
        }
    } RETHROW_NON_MEMFILE_EXCEPTIONS;
}

void
MemFile::ensureEntriesCached(bool includeBody) const
{
    if (!fileExists()) {
        return;
    }

    try{
        std::vector<DataLocation> headerLocations;
        std::vector<DataLocation> bodyLocations;

        for (uint32_t i = 0; i < _entries.size(); ++i) {
            headerLocations.push_back(_entries[i].getLocation(HEADER));

            if (includeBody) {
                bodyLocations.push_back(_entries[i].getLocation(BODY));
            }
        }

        _buffer->ensureCached(_env, HEADER, headerLocations);
        if (includeBody) {
            _buffer->ensureCached(_env, BODY, bodyLocations);
        }
    } RETHROW_NON_MEMFILE_EXCEPTIONS;
}

void
MemFile::ensureHeaderBlockCached() const
{
    ensureEntriesCached(false);
}

void
MemFile::ensureBodyBlockCached() const
{
    ensureEntriesCached(true);
}

/**
 * Functionally this is the same as ensureBodyBlockCached, but with
 * clearer semantics.
 */
void
MemFile::ensureHeaderAndBodyBlocksCached() const
{
    ensureEntriesCached(true);
}

bool
MemFile::documentIdAvailable(const MemSlot& slot) const
{
    return partAvailable(slot, HEADER);
}

bool
MemFile::partAvailable(const MemSlot& slot, DocumentPart part) const
{
    return _buffer->isCached(slot.getLocation(part), part);
}

bool
MemFile::partPersisted(const MemSlot& slot, DocumentPart part) const
{
    assert(_buffer.get());

    return _buffer->isPersisted(slot.getLocation(part), part);
}

uint32_t
MemFile::getSerializedSize(const MemSlot& slot, DocumentPart part) const {
    DataLocation loc = slot.getLocation(part);
    return _buffer->getSerializedSize(part, loc);
}

const Types::BucketInfo&
MemFile::getBucketInfo() const
{
    if (_flags & BUCKET_INFO_OUTDATED) {
        uint32_t uniqueCount = 0, uniqueSize = 0, usedSize = 0;
        uint32_t checksum = 0;

        typedef vespalib::hash_set<GlobalId, GlobalId::hash> SeenMap;
        SeenMap seen(_entries.size() * 2);
        uint32_t maxHeaderExtent = 0, maxBodyExtent = 0;

        MemSlotVector::const_reverse_iterator e(_entries.rend());
        for (MemSlotVector::const_reverse_iterator it(_entries.rbegin());
             it != e; ++it)
        {
            const MemSlot& slot(*it);
            // We now always write sequentially within the blocks, so used size
            // for one block is effectively the max location extent seen within
            // it.
            maxHeaderExtent = std::max(maxHeaderExtent,
                                       slot.getLocation(HEADER)._pos
                                       + slot.getLocation(HEADER)._size);
            maxBodyExtent = std::max(maxBodyExtent,
                                     slot.getLocation(BODY)._pos
                                     + slot.getLocation(BODY)._size);

            SeenMap::insert_result inserted(seen.insert(slot.getGlobalId()));
            if (!inserted.second) {
                continue;
            }
            if (slot.deleted()) continue;

            const uint32_t slotSize = slot.getLocation(HEADER)._size
                                      + slot.getLocation(BODY)._size;
            uniqueSize += slotSize;
            ++uniqueCount;

            vespalib::crc_32_type calculator;
            calculator.process_bytes(slot.getGlobalId().get(),
                                     GlobalId::LENGTH);
            Timestamp time = slot.getTimestamp();
            calculator.process_bytes(&time, sizeof(Timestamp));
            checksum ^= calculator.checksum();
        }

        if (uniqueCount > 0 && checksum < 2) {
            checksum += 2;
        }

        // Only set used size if we have any entries at all.
        if (!_entries.empty()) {
            usedSize = 64 + 40 * _entries.size()
                       + maxHeaderExtent + maxBodyExtent;
        }

        spi::BucketInfo info(spi::BucketChecksum(checksum),
                             uniqueCount,
                             uniqueSize,
                             _entries.size(),
                             usedSize,
                             BucketInfo::READY,
                             BucketInfo::NOT_ACTIVE);

        _info = info;
        _flags &= ~BUCKET_INFO_OUTDATED;
    }
    return _info;
}

void
MemFile::flushToDisk(FlushFlag flag)
{
    if ((flag == CHECK_NON_DIRTY_FILE_FOR_SPACE) || (_flags & SLOTS_ALTERED)) {
        LOG(spam, "Flushing %s to disk since flags is %x", toString().c_str(), _flags);
        try{
            _env._memFileMapper.flush(*this, _env);
        } RETHROW_NON_MEMFILE_EXCEPTIONS;
    } else {
        LOG(spam, "Not flushing %s as it is not altered", toString().c_str());
    }

    // For now, close all files after done flushing, to avoid getting
    // too many open at the same time. Later cache may cache limited
    // amount of file handles
    getMemFileIO().close();
}

void
MemFile::clearCache(DocumentPart part)
{
    _buffer->clear(part);
    if (part == HEADER) {
        _cacheSizeOverride.headerSize = 0;
    } else {
        _cacheSizeOverride.bodySize = 0;
    }
}

bool
MemFile::repair(std::ostream& errorReport, uint32_t verifyFlags)
{
    try{
        return _env._memFileMapper.repair(
                *this, _env, errorReport, verifyFlags);
    } RETHROW_NON_MEMFILE_EXCEPTIONS;
}

void
MemFile::resetMetaState()
{
    LOG(debug, "Resetting meta state for MemFile");
    _flags = BUCKET_INFO_OUTDATED;
    _currentVersion = UNKNOWN;
    _info = BucketInfo();
    _entries.clear();
}

MemSlot::MemoryUsage
MemFile::getCacheSize() const
{
    assert(_buffer.get());

    if (_cacheSizeOverride.sum() > 0) {
        return _cacheSizeOverride;
    }

    MemSlot::MemoryUsage retVal;
    retVal.metaSize = sizeof(MemSlot) * _entries.size();
    retVal.headerSize += _buffer->getCachedSize(HEADER);
    retVal.bodySize += _buffer->getCachedSize(BODY);
    return retVal;
}

void
MemFile::verifyDiskNotFull()
{
    const double maxFillRate(
            _env.acquireConfigReadLock().options()->_diskFullFactor);

    Directory& dir = _file.getDirectory();

    if (dir.getPartition().getMonitor() == 0) {
        LOG(warning, "No partition monitor found for directory %s. Skipping "
            "disk full test.", dir.toString(true).c_str());
    } else if (dir.isFull(0, maxFillRate)) {
        std::ostringstream token;
        token << dir << " is full";
        std::ostringstream ost;
        ost << "Disallowing operation on file " << getFile().getPath()
            << " because disk is or would be "
            << (100 * dir.getPartition().getMonitor()
                    ->getFillRate()) << " % full, which is "
            << "more than the max setting of "
            << 100 * maxFillRate << " % full."
            << " (Note that this may be both due to space or inodes. "
            << "Check \"df -i\" too if manually checking)"
            << " (" << dir.toString(true) << ")";
        LOGBT(warning, token.str(), "%s", ost.str().c_str());
        throw vespalib::IoException(
                ost.str(), vespalib::IoException::NO_SPACE, VESPA_STRLOC);
    } else {
        LOG(spam, "Disk will only be %f %% full after operation, which "
            "is below limit of %f %%; allowing it to go through.",
            100.0 * dir.getPartition().getMonitor()
                            ->getFillRate(),
            100.0 * maxFillRate);
    }
}

bool
MemFile::operator==(const MemFile& other) const
{
    if (_info == other._info &&
        _entries.size() == other._entries.size() &&
        _file == other._file &&
        _currentVersion == other._currentVersion)
    {
        for (uint32_t i=0, n=_entries.size(); i<n; ++i) {
            if (_entries[i] != other._entries[i]) return false;
        }
        return true;
    }
    return false;
}

namespace {
    void printMemFlags(std::ostream& out, uint32_t flags) {
        bool anyPrinted = false;
        for (uint32_t val=1,i=1; i<=32; ++i, val *= 2) {
            if (flags & val) {
                if (anyPrinted) { out << "|"; }
                anyPrinted = true;
                const char* name = Types::getMemFileFlagName(
                                        static_cast<Types::MemFileFlag>(val));
                if (strcmp(name, "INVALID") == 0) {
                    out << "INVALID(" << std::hex << val << std::dec << ")";
                } else {
                    out << name;
                }
            }
        }
        if (!anyPrinted) out << "none";
    }
}

void
MemFile::printHeader(std::ostream& out, bool verbose,
                     const std::string& indent) const
{
    if (!verbose) {
        out << "MemFile(" << _file.getBucketId() << ", dir "
            << _file.getDirectory().getIndex();
    } else {
        out << "MemFile(" << _file.getBucketId()
            << "\n" << indent << "        Path(\""
            << _file.getPath() << "\")"
            << "\n" << indent << "        Wanted version("
            << Types::getFileVersionName(_file.getWantedFileVersion()) 
            << "(" << std::hex << _file.getWantedFileVersion() << "))"
            << "\n" << indent << "        Current version("
            << Types::getFileVersionName(_currentVersion) 
            << "(" << std::hex << _currentVersion << "))"
            << "\n" << indent << "        " << getBucketInfo()
            << "\n" << indent << "        Flags ";
        printMemFlags(out, _flags);

        if (_formatData.get()) {
            out << "\n" << indent << "        " << _formatData->toString();
        }
    }
}

void
MemFile::printEntries(std::ostream& out, bool verbose,
                      const std::string& indent) const
{
    if (verbose && !_entries.empty()) {
        out << ") {";
        for (uint32_t i=0; i<_entries.size(); ++i) {
            out << "\n" << indent << "  ";
            print(_entries[i], out, false, indent + "  ");
        }
        out << "\n" << indent << "}";
    } else {
        out << ", " << _entries.size() << " entries)";
    }
}

void
MemFile::printEntriesState(std::ostream& out, bool verbose,
                           const std::string& indent) const
{
    for (uint32_t i=0; i<_entries.size(); ++i) {
        if (verbose) {
            printUserFriendly(_entries[i], out, indent);
        } else {
            print(_entries[i], out, false, indent);
        }
        out << "\n" << indent;
    }
    const SimpleMemFileIOBuffer& ioBuf(
            static_cast<const SimpleMemFileIOBuffer&>(getMemFileIO()));
    const FileInfo& fileInfo(ioBuf.getFileInfo());

    unsigned int emptyCount = fileInfo._metaDataListSize - _entries.size();
    if (emptyCount > 0) {
        out << std::dec << emptyCount << " empty entries.\n" << indent;
    }
}

void
MemFile::print(std::ostream& out, bool verbose,
               const std::string& indent) const
{
    printHeader(out, verbose, indent);
    printEntries(out, verbose, indent);
}

void
MemFile::printUserFriendly(const MemSlot& slot,
                           std::ostream& out,
                           const std::string& indent) const
{
    out << "MemSlot(" << slot.getGlobalId().toString()
        << std::setfill(' ')
        << std::dec << "\n"
        << indent << "  Header pos: "
        << std::setw(10) << slot.getLocation(HEADER)._pos
        << " - " << std::setw(10) << slot.getLocation(HEADER)._size
        << ", Body pos: " << std::setw(10) << slot.getLocation(BODY)._pos
        << " - " << std::setw(10) << slot.getLocation(BODY)._size << "\n" << indent
        << "  Timestamp:      " << slot.getTimestamp().toString()
        << " (" << slot.getTimestamp().getTime() << ")\n"
        << indent << "  Checksum: 0x"
        << std::hex << std::setw(4) << slot.getChecksum()
        << std::setfill(' ') << "\n" << indent << "  Flags: 0x"
        << std::setw(4) << slot.getFlags();
    std::list<std::string> flags;

    if ((slot.getFlags() & IN_USE) == 0) flags.push_back("NOT IN USE");
    if ((slot.getFlags() & DELETED) != 0) flags.push_back("DELETED");
    if ((slot.getFlags() & DELETED_IN_PLACE) != 0) flags.push_back("DELETED_IN_PLACE");
    if ((slot.getFlags() & CHECKSUM_OUTDATED) != 0) flags.push_back("CHECKSUM_OUTDATED");

    for (std::list<std::string>::iterator it = flags.begin();
         it != flags.end(); ++it)
    {
        out << ", " << *it;
    }

    const document::DocumentId id = getDocumentId(slot);

    out << "\n" << indent << "  Name: " << id;
    document::BucketIdFactory factory;
    document::BucketId bucket(
            factory.getBucketId(
                    document::DocumentId(id)));
    out << "\n" << indent << "  Bucket: " << bucket;
    out << ")";
}

void
MemFile::print(const MemSlot& slot,
               std::ostream& out,
               bool verbose,
               const std::string& indent) const
{
    if (verbose) {
        out << "MemSlot(";
    }
    out << std::dec << slot.getTimestamp() << ", " << slot.getGlobalId().toString() << ", h "
        << slot.getLocation(HEADER)._pos << " - " << slot.getLocation(HEADER)._size << ", b "
        << slot.getLocation(BODY)._pos << " - " << slot.getLocation(BODY)._size << ", f "

        << std::hex << slot.getFlags() << ", c " << slot.getChecksum()
        << ", C(" << (documentIdAvailable(slot) ? "D" : "")
        << (partAvailable(slot, HEADER) ? "H" : "")
        << (partAvailable(slot, BODY) ? "B" : "")
        << ")";
    if (verbose) {
        out << ") {";
        if (documentIdAvailable(slot)) {
            out << "\n" << indent << "  ";

            getDocument(slot, ALL)
                ->print(out, true, indent + "  ");
        } else {
            out << "\n" << indent << "  Nothing cached beyond metadata.";
        }
        out << "\n" << indent << "}";
    }
}

void
MemFile::printState(std::ostream& out, bool userFriendlyOutput,
                    bool printBody, bool printHeader2,
                    //SlotFile::MetaDataOrder order,
                    const std::string& indent) const
{
    const SimpleMemFileIOBuffer& ioBuf(
            static_cast<const SimpleMemFileIOBuffer&>(getMemFileIO()));
    const FileInfo& fileInfo(ioBuf.getFileInfo());

    out << "\n" << indent << "Filename: '" << getFile().getPath() << "'";
    if (!fileExists()) {
        out << " (non-existing)";
        return;
    } else if (ioBuf.getFileHandle().isOpen()) {
        out << " (fd " << ioBuf.getFileHandle().getFileDescriptor() << ")";
    }
    out << "\n";

    uint32_t filesize = ioBuf.getFileHandle().getFileSize();
    out << "Filesize: " << filesize << "\n";
    Buffer buffer(filesize);
    char* buf = buffer.getBuffer();
    uint32_t readBytes = ioBuf.getFileHandle().read(buf, filesize, 0);
    if (readBytes != filesize) {
        out << "Failed to read whole file of size " << filesize
            << ". Adjusting file size to " << readBytes
            << " we managed to read.";
        filesize = readBytes;
    }

    const Header* header(reinterpret_cast<const Header*>(buf));
    header->print(out);
    out << "\n" << indent;

    if (filesize < fileInfo.getHeaderBlockStartIndex())
    {
        out << "File not big enough to contain all "
            << fileInfo._metaDataListSize << " meta data entries.\n"
            << indent;
    } else {
        printEntriesState(out, userFriendlyOutput, indent);
    }

    if (filesize < fileInfo.getBodyBlockStartIndex())
    {
        out << "File not big enough to contain the whole "
            << fileInfo._headerBlockSize << " byte header block.\n" << indent;
    } else {
        out << "Header block: (" << std::dec << fileInfo._headerBlockSize
            << "b)";
        if (printHeader2) {
            const char* start = &buf[0] + fileInfo.getHeaderBlockStartIndex();
            out << "\n" << indent;
            document::StringUtil::printAsHex(
                    out, start, fileInfo._headerBlockSize, 16, false);
        }
        out << "\n" << indent;
    }

    if (filesize < fileInfo.getFileSize())
    {
        out << "File not big enough to contain the whole "
            << fileInfo._bodyBlockSize << " byte content block.\n" << indent;
    } else {
        out << "Content block: (" << std::dec << fileInfo._bodyBlockSize << "b)";
        if (printBody) {
            const char* start = &buf[0] + fileInfo.getBodyBlockStartIndex();
            out << "\n" << indent;
            document::StringUtil::printAsHex(
                    out, start, fileInfo._bodyBlockSize, 16, false);
        }
        out << "\n" << indent;
    }
}


} // memfile
} // storage
