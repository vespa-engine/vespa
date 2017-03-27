// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <iterator>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/select/bodyfielddetector.h>
#include <vespa/document/select/node.h>
#include <vespa/memfilepersistence/spi/iteratorhandler.h>
#include <vespa/memfilepersistence/spi/visitorslotmatcher.h>
#include <vespa/memfilepersistence/spi/cacheevictionguard.h>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.memfile.handler.iterator");

namespace storage {
namespace memfile {

CachePrefetchRequirements
CachePrefetchRequirements::createFromSelection(const document::DocumentTypeRepo& repo,
                                            const document::select::Node& sel)
{
    CachePrefetchRequirements ret;
    document::select::BodyFieldDetector bfd(repo);
    sel.visit(bfd);
    ret.setHeaderPrefetchRequired(bfd.foundHeaderField);
    ret.setBodyPrefetchRequired(bfd.foundBodyField);
    return ret;
}

IteratorHandler::IteratorHandler(Environment& env)
    : OperationHandler(env)
{
}

IteratorHandler::~IteratorHandler()
{
}

void
IteratorHandler::sanityCheckActiveIteratorCount()
{
    if (_sharedState._iterators.size()
            >= SharedIteratorHandlerState::WARN_ACTIVE_ITERATOR_COUNT
            && !_sharedState._hasWarnedLargeIteratorCount)
    {
        LOG(warning, "Number of active iterators has reached warn-limit "
                "of %zu. Potential iterator leak? destroyIterator() must be "
                "invoked for each successful createIterator() invocation.",
                SharedIteratorHandlerState::WARN_ACTIVE_ITERATOR_COUNT);
        _sharedState._hasWarnedLargeIteratorCount = true;
    }
}

spi::CreateIteratorResult
IteratorHandler::createIterator(const spi::Bucket& bucket,
                                const document::FieldSet& fields,
                                const spi::Selection& sel,
                                spi::IncludedVersions versions)
{
    uint64_t id;
    // By default, no explicit prefetching is required.
    CachePrefetchRequirements prefetcher;

    std::unique_ptr<document::select::Node> docSelection;
    if (!sel.getDocumentSelection().getDocumentSelection().empty()) {
        docSelection.reset(
                parseDocumentSelection(
                        sel.getDocumentSelection().getDocumentSelection(),
                        true).release());
        if (!docSelection.get()) {
            return spi::CreateIteratorResult(
                    spi::Result::PERMANENT_ERROR,
                    "Got invalid/unparseable document selection string");
        }
        prefetcher = CachePrefetchRequirements::createFromSelection(
                _env.repo(), *docSelection);
        // NOTE: Suboptimal behavior; since the field detector doesn't
        // understand that ID-related selections require header reads,
        // we take the safest route here and simply always require the
        // header to be prefetched if we have _any_ kind of non-empty
        // document selection.
        prefetcher.setHeaderPrefetchRequired(true);
    }
    prefetcher.setFromTimestamp(Timestamp(sel.getFromTimestamp().getValue()));
    prefetcher.setToTimestamp(Timestamp(sel.getToTimestamp().getValue()));

    {
        vespalib::LockGuard lock(_sharedState._stateLock);
        id = _sharedState._nextId;

        std::pair<IteratorStateMap::iterator, bool> inserted(
                _sharedState._iterators.insert(
                        IteratorStateMap::value_type(
                                id,
                                IteratorState(
                                        bucket,
                                        sel,
                                        document::FieldSet::UP(fields.clone()),
                                        versions,
                                        std::move(docSelection),
                                        prefetcher))));

        assert(inserted.second); // Should never have duplicates
        ++_sharedState._nextId;
        sanityCheckActiveIteratorCount();
    }
    LOG(debug, "Created new iterator state for bucket %s "
        "with iterator id %zu",
        bucket.getBucketId().toString().c_str(),
        id);
    return spi::CreateIteratorResult(spi::IteratorId(id));
}

spi::Result
IteratorHandler::destroyIterator(spi::IteratorId id)
{
    vespalib::LockGuard lock(_sharedState._stateLock);
    uint64_t iterId = id;
    IteratorStateMap::iterator iter(
            _sharedState._iterators.find(iterId));
    if (iter == _sharedState._iterators.end()) {
        std::ostringstream ss;
        ss << "destroyIterator called with unknown iterator id ("
           << iterId << ")";
        LOG(error, "%s", ss.str().c_str());
        return spi::Result();
    }
    LOG(debug, "Destroying iterator state for iterator id %zu", iterId);
    assert(!iter->second.isActive());
    _sharedState._iterators.erase(iter);
    return spi::Result();
}

spi::DocEntry::SizeType
IteratorHandler::getDocumentSize(const MemFile& file,
                                 const MemSlot& slot,
                                 bool headerOnly) const
{
    spi::DocEntry::SizeType size = file.getSerializedSize(slot, HEADER);
    if (!headerOnly) {
        size += file.getSerializedSize(slot, BODY);
    }
    return size;
}

spi::DocEntry::SizeType
IteratorHandler::getEntrySize(spi::DocEntry::SizeType docSize) const
{
    return docSize + sizeof(spi::DocEntry);
}

void
IteratorHandler::prefetch(const CachePrefetchRequirements& requirements,
                          MemFile& file) const
{
    if (requirements.noPrefetchRequired()) {
        LOG(spam, "%s: no prefetching required",
            file.getFile().getBucketId().toString().c_str());
        return;
    }
    // Let body prefetching also imply header prefetching, at least for now.
    // If this changes, so must the explicit caching of remaining timestamps
    // in iterate().
    bool headerOnly = !requirements.isBodyPrefetchRequired();
    if (requirements.prefetchEntireBlocks()) {
        LOG(spam, "%s: prefetching entire blocks for header: yes, body: %s",
            file.getFile().getBucketId().toString().c_str(),
            headerOnly ? "no" : "yes");
        if (headerOnly) {
            file.ensureHeaderBlockCached();
        } else {
            file.ensureHeaderAndBodyBlocksCached();
        }
    } else {
        std::vector<Timestamp> timestamps;
        for (size_t i = 0; i < file.getSlotCount(); ++i) {
            const MemSlot& slot(file[i]);
            // TODO(vekterli): replace this sub-optimal code with a lower bound search
            if (slot.getTimestamp() < requirements.getFromTimestamp()) {
                continue;
            }
            if (slot.getTimestamp() > requirements.getToTimestamp()) {
                break;
            }
            timestamps.push_back(slot.getTimestamp());
        }
        LOG(spam, "%s: prefetching %zu slots in timestamp range [%zu, %zu]",
            file.getFile().getBucketId().toString().c_str(),
            timestamps.size(),
            requirements.getFromTimestamp().getTime(),
            requirements.getToTimestamp().getTime());
        file.ensureDocumentCached(timestamps, headerOnly);
    }
}

std::vector<Types::Timestamp>&
IteratorHandler::getOrFillRemainingTimestamps(MemFile& file,
                                              IteratorState& state)
{
    std::vector<Types::Timestamp>& remaining(state.getRemaining());
    if (remaining.empty()) {
        if (state.getSelection().getTimestampSubset().empty()) {
            VisitorSlotMatcher matcher(
                    _env.repo(), state.getDocumentSelectionPtr());

            int flags = 0;
            switch (state.getIncludedVersions()) {
            case spi::NEWEST_DOCUMENT_ONLY:
                flags = ITERATE_GID_UNIQUE;
                break;
            case spi::NEWEST_DOCUMENT_OR_REMOVE:
                flags = ITERATE_GID_UNIQUE | ITERATE_REMOVED;
                break;
            case spi::ALL_VERSIONS:
                flags = ITERATE_REMOVED;
                break;
            }

            remaining = select(
                    file,
                    matcher,
                    flags,
                    Timestamp(state.getSelection().getFromTimestamp()),
                    Timestamp(state.getSelection().getToTimestamp()));
        } else {
            const std::vector<spi::Timestamp>& subset(
                    state.getSelection().getTimestampSubset());
            remaining.reserve(subset.size());
            for (size_t i = 0; i < subset.size(); ++i) {
                // Ensure timestamps are strictly increasing
                assert(i == 0 || subset[i] > subset[i - 1]);
                remaining.push_back(Types::Timestamp(subset[i]));
            }

            state.setIncludedVersions(spi::ALL_VERSIONS);
        }
    }
    return remaining;
}

bool
IteratorHandler::addMetaDataEntry(spi::IterateResult::List& result,
                                  const MemSlot& slot,
                                  uint64_t& totalSize,
                                  uint64_t maxByteSize) const
{
    size_t entrySize = getEntrySize(0);
    if (totalSize + entrySize >= maxByteSize && !result.empty()) {
        return false;
    }
    totalSize += entrySize;

    int metaFlags = (slot.deleted() || slot.deletedInPlace()) ? spi::REMOVE_ENTRY : 0;
    spi::DocEntry::UP docEntry(
            new spi::DocEntry(
                    spi::Timestamp(slot.getTimestamp().getTime()),
                    metaFlags));
    result.push_back(std::move(docEntry));
    return true;
}

bool
IteratorHandler::addRemoveEntry(spi::IterateResult::List& results,
                                const MemFile& file,
                                const MemSlot& slot,
                                uint64_t& totalSize,
                                uint64_t maxByteSize) const
{
    DocumentId did = file.getDocumentId(slot);
    size_t idSize = did.getSerializedSize();
    size_t entrySize = getEntrySize(idSize);

    if (totalSize + entrySize >= maxByteSize && !results.empty()) {
        return false;
    }
    totalSize += entrySize;

    spi::DocEntry::UP docEntry(
            new spi::DocEntry(
                    spi::Timestamp(slot.getTimestamp().getTime()),
                    spi::REMOVE_ENTRY,
                    did));
    results.push_back(std::move(docEntry));
    return true;
}

bool
IteratorHandler::addPutEntry(spi::IterateResult::List& results,
                             const MemFile& file,
                             const MemSlot& slot,
                             bool headerOnly,
                             const document::FieldSet& fieldsToKeep,
                             uint64_t& totalSize,
                             uint64_t maxByteSize) const
{
    size_t docSize = getDocumentSize(file, slot, headerOnly);
    size_t entrySize = getEntrySize(docSize);
    if (totalSize + entrySize >= maxByteSize && !results.empty()) {
        return false;
    }
    Document::UP doc(
            file.getDocument(slot, headerOnly ? HEADER_ONLY : ALL));
    totalSize += entrySize;
    // If we want either the full doc or just the header, don't waste time
    // stripping unwanted document fields.
    if (fieldsToKeep.getType() != document::FieldSet::ALL
        && fieldsToKeep.getType() != document::FieldSet::HEADER)
    {
        document::FieldSet::stripFields(*doc, fieldsToKeep);
    }
    spi::DocEntry::UP docEntry(
            new spi::DocEntry(spi::Timestamp(slot.getTimestamp().getTime()),
                              0,
                              std::move(doc),
                              docSize));
    results.push_back(std::move(docEntry));
    return true;
}

spi::IterateResult
IteratorHandler::iterate(spi::IteratorId id, uint64_t maxByteSize)
{
    spi::IterateResult::List results;

    IteratorState* state;
    {
        vespalib::LockGuard lock(_sharedState._stateLock);
        IteratorStateMap::iterator iter(
                _sharedState._iterators.find(id));
        if (iter == _sharedState._iterators.end()) {
            LOG(error, "Invoked iterate(id=%zu, maxByteSize=%zu) "
                "with unknown id",
                uint64_t(id),
                maxByteSize);

            return spi::IterateResult(spi::Result::PERMANENT_ERROR,
                            "Unknown iterator ID");
        }
        assert(!iter->second.isActive());
        state = &iter->second;
        if (state->isCompleted()) {
            return spi::IterateResult(std::move(results), true);
        }
        state->setActive(true);
    }

    ActiveGuard activeGuard(*state);
    MemFileCacheEvictionGuard file(getMemFile(state->getBucket()));

    const document::FieldSet& fields(state->getFields());
    bool metaDataOnly = (fields.getType() == document::FieldSet::NONE);
    bool headerOnly = true;

    // Ensure we have relevant parts of the file prefetched if this is required.
    const CachePrefetchRequirements& prefetchRequirements(
            state->getCachePrefetchRequirements());
    prefetch(prefetchRequirements, *file);

    std::vector<Timestamp>& remaining(
            getOrFillRemainingTimestamps(*file, *state));

    if (!metaDataOnly) {
        document::HeaderFields h;
        headerOnly = h.contains(fields);
        // Don't bother doing duplicate work if we've already prefetched
        // everything we need.
        if (!((headerOnly && prefetchRequirements.isHeaderPrefetchRequired())
              || prefetchRequirements.isBodyPrefetchRequired()))
        {
            LOG(spam, "Caching %zu remaining slots from disk for %s",
                remaining.size(),
                state->getBucket().getBucketId().toString().c_str());
            file->ensureDocumentCached(remaining, headerOnly);
        }
    } else {
        LOG(spam, "Not caching any of the %zu remaining slots from disk "
            "for %s since iteration is metadata only",
            remaining.size(),
            state->getBucket().getBucketId().toString().c_str());
    }

    size_t totalSize = 0;
    while (!remaining.empty()) {
        Timestamp ts = remaining.back();
        const MemSlot* slot = file->getSlotAtTime(ts);

        if (slot) {
            if (metaDataOnly) {
                if (!addMetaDataEntry(results, *slot, totalSize, maxByteSize)) {
                    break;
                }
            } else if (slot->deleted() || slot->deletedInPlace()) {
                if (state->getIncludedVersions() == spi::NEWEST_DOCUMENT_ONLY) {
                    // Probably altered by unrevertable remove between time
                    // of timestamp gathering and actual iteration.
                    remaining.pop_back();
                    continue;
                }
                if (!addRemoveEntry(results, *file, *slot,
                                    totalSize, maxByteSize))
                {
                    break;
                }
            } else {
                if (!addPutEntry(results, *file, *slot,
                                 headerOnly, fields, totalSize, maxByteSize))
                {
                    break;
                }
            }
        }
        remaining.pop_back();
    }

    file.unguard();

    LOG(debug, "Iteration of bucket %s returned result with %zu entries "
        "and %zu bytes. Remaining docs: %zu",
        state->getBucket().getBucketId().toString().c_str(),
        results.size(),
        totalSize,
        remaining.size());

    if (remaining.empty()) {
        state->setCompleted();
        return spi::IterateResult(std::move(results), true);
    }

    return spi::IterateResult(std::move(results), false);
}

}
}
