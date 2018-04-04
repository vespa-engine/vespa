// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "mergehandler.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/storage/common/bucketoperationlogger.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.mergehandler");

namespace storage {

MergeHandler::MergeHandler(spi::PersistenceProvider& spi,
                           PersistenceUtil& env)
    : _spi(spi),
      _env(env),
      _maxChunkSize(env._config.bucketMergeChunkSize)
{
}

MergeHandler::MergeHandler(spi::PersistenceProvider& spi,
                           PersistenceUtil& env,
                           uint32_t maxChunkSize)
    : _spi(spi),
      _env(env),
      _maxChunkSize(maxChunkSize)
{
}

namespace {

int getDeleteFlag() {
    // Referred into old slotfile code before. Where should this number come from?
    return 2;
}

/**
 * Throws std::runtime_error if result has an error.
 */
void
checkResult(const spi::Result& result,
            const spi::Bucket& bucket,
            const document::DocumentId& docId,
            const char* op)
{
    if (result.hasError()) {
        vespalib::asciistream ss;
        ss << "Failed " << op
           << " for " << docId.toString()
           << " in " << bucket
           << ": " << result.toString();
        throw std::runtime_error(ss.str());
    }
}

void
checkResult(const spi::Result& result,
            const spi::Bucket& bucket,
            const char* op)
{
    if (result.hasError()) {
        vespalib::asciistream ss;
        ss << "Failed " << op << " in " << bucket << ": " << result.toString();
        throw std::runtime_error(ss.str());
    }
}


class IteratorGuard
{
    spi::PersistenceProvider& _spi;
    spi::IteratorId _iteratorId;
    spi::Context& _context;
public:
    IteratorGuard(spi::PersistenceProvider& spi,
                  spi::IteratorId iteratorId,
                  spi::Context& context)
        : _spi(spi),
          _iteratorId(iteratorId),
          _context(context)
    {}
    ~IteratorGuard()
    {
        assert(_iteratorId != 0);
        _spi.destroyIterator(_iteratorId, _context);
    }
};

class FlushGuard
{
    spi::PersistenceProvider& _spi;
    spi::Bucket _bucket;
    spi::Context& _context;
    bool _hasFlushed;
public:
    FlushGuard(spi::PersistenceProvider& spi,
               const spi::Bucket& bucket,
               spi::Context& context)
        : _spi(spi),
          _bucket(bucket),
          _context(context),
          _hasFlushed(false)
    {}
    ~FlushGuard()
    {
        if (!_hasFlushed) {
            LOG(debug, "Auto-flushing %s", _bucket.toString().c_str());
            spi::Result result =_spi.flush(_bucket, _context);
            if (result.hasError()) {
                LOG(debug, "Flush %s failed: %s",
                    _bucket.toString().c_str(),
                    result.toString().c_str());
            }
        }
    }
    void flush() {
        LOG(debug, "Flushing %s", _bucket.toString().c_str());
        _hasFlushed = true;
        checkResult(_spi.flush(_bucket, _context), _bucket, "flush");
    }
};

struct IndirectDocEntryTimestampPredicate
{
    bool operator()(const spi::DocEntry::UP& e1,
                    const spi::DocEntry::UP& e2) const
    {
        return e1->getTimestamp() < e2->getTimestamp();
    }

    bool operator()(const spi::DocEntry::UP& e,
                    const spi::Timestamp timestamp) const
    {
        return e->getTimestamp() < timestamp;
    }
};

struct DiffEntryTimestampPredicate
{
    bool operator()(const api::ApplyBucketDiffCommand::Entry& e,
                    const api::Timestamp timestamp) const
    {
        return e._entry._timestamp < timestamp;
    }
};

} // anonymous namespace

void
MergeHandler::populateMetaData(
        const spi::Bucket& bucket,
        Timestamp maxTimestamp,
        std::vector<spi::DocEntry::UP>& entries,
        spi::Context& context)
{
    spi::DocumentSelection docSel("");

    spi::Selection sel(docSel);
    sel.setToTimestamp(spi::Timestamp(maxTimestamp.getTime()));
    spi::CreateIteratorResult createIterResult(_spi.createIterator(
                                                       bucket,
                                                       document::NoFields(),
                                                       sel,
                                                       spi::ALL_VERSIONS,
                                                       context));

    if (createIterResult.getErrorCode() != spi::Result::NONE) {
            std::ostringstream ss;
            ss << "Failed to create iterator for "
               << bucket
               << ": "
               << createIterResult.getErrorMessage();
        throw std::runtime_error(ss.str());
    }
    spi::IteratorId iteratorId(createIterResult.getIteratorId());
    IteratorGuard iteratorGuard(_spi, iteratorId, context);

    while (true) {
        spi::IterateResult result(
                _spi.iterate(iteratorId, UINT64_MAX, context));
        if (result.getErrorCode() != spi::Result::NONE) {
            std::ostringstream ss;
            ss << "Failed to iterate for "
               << bucket
               << ": "
               << result.getErrorMessage();
            throw std::runtime_error(ss.str());
        }
        auto list = result.steal_entries();
        std::move(list.begin(), list.end(), std::back_inserter(entries));
        if (result.isCompleted()) {
            break;
        }
    }
    std::sort(entries.begin(), entries.end(),
              IndirectDocEntryTimestampPredicate());
}

bool
MergeHandler::buildBucketInfoList(
        const spi::Bucket& bucket,
        const documentapi::LoadType& /*loadType*/,
        Timestamp maxTimestamp,
        uint8_t myNodeIndex,
        std::vector<api::GetBucketDiffCommand::Entry>& output,
        spi::Context& context)
{
    assert(output.size() == 0);
    assert(myNodeIndex < 16);
    uint32_t oldSize = output.size();
    typedef api::BucketInfo DbBucketInfo;

    // Always verify that bucket database is correct in merge, such that
    // any out of sync data get fixed. Such errors must of course also be
    // fixed, but by making merge fix it, distributors will stop and spin
    // on merge, never getting their problems fixed.
    {
        StorBucketDatabase& db(_env.getBucketDatabase(bucket.getBucketSpace()));
        StorBucketDatabase::WrappedEntry entry(
                db.get(bucket.getBucketId(), "MergeHandler::buildBucketInfoList"));
        if (entry.exist()) {
            spi::BucketInfoResult infoResult(_spi.getBucketInfo(bucket));

            if (infoResult.getErrorCode() != spi::Result::NONE) {
                std::ostringstream ss;
                ss << "Failed to get bucket info for "
                   << bucket << ": "
                   << infoResult.getErrorMessage();
                LOG(warning, "%s", ss.str().c_str());
                throw std::runtime_error(ss.str());
            }
            DbBucketInfo dbInfo(entry->getBucketInfo());
            const spi::BucketInfo& tmpInfo(infoResult.getBucketInfo());
            DbBucketInfo providerInfo(tmpInfo.getChecksum(),
                                      tmpInfo.getDocumentCount(),
                                      tmpInfo.getDocumentSize(),
                                      tmpInfo.getEntryCount(),
                                      tmpInfo.getUsedSize(),
                                      tmpInfo.isReady(),
                                      tmpInfo.isActive(),
                                      dbInfo.getLastModified());

            if (!dbInfo.equalDocumentInfo(providerInfo)) {
                if (dbInfo.valid()) {
                    LOG(warning, "Prior to merging %s we found that storage "
                        "bucket database was out of sync with content "
                        "of file. Actual file content is %s while "
                        "bucket database content was %s. Updating"
                        " bucket database to get in sync.",
                        bucket.toString().c_str(),
                        providerInfo.toString().c_str(),
                        dbInfo.toString().c_str());
                    DUMP_LOGGED_BUCKET_OPERATIONS(bucket.getBucketId());
                }

                entry->setBucketInfo(providerInfo);
                entry.write();
            }
        } else {
            return false;
        }
    }

    std::vector<spi::DocEntry::UP> entries;
    populateMetaData(bucket, maxTimestamp, entries, context);

    for (size_t i = 0; i < entries.size(); ++i) {
        api::GetBucketDiffCommand::Entry diff;
        const spi::DocEntry& entry(*entries[i]);
        diff._gid = GlobalId();
        // We do not know doc sizes at this point, so just set to 0
        diff._headerSize = 0;
        diff._bodySize = 0;
        diff._timestamp = entry.getTimestamp();
        diff._flags = IN_USE
                      | (entry.isRemove() ? DELETED : 0);
        diff._hasMask = 1 << myNodeIndex;
        output.push_back(diff);

        LOG(spam, "bucket info list of %s: Adding entry %s to diff",
            bucket.toString().c_str(), diff.toString(true).c_str());
    }
    LOG(spam, "Built bucket info list of %s. Got %u entries.",
        bucket.toString().c_str(), (uint32_t) (output.size() - oldSize));
    return true;
}

namespace {

    /**
     * Find out whether we need to read data locally yet.
     */
    bool applyDiffNeedLocalData(
            const std::vector<api::ApplyBucketDiffCommand::Entry>& diff,
            uint8_t nodeIndex,
            bool forwards)
    {
        if (!forwards && nodeIndex == 0) return false;
        uint32_t result = 1 << nodeIndex;
        uint32_t mask = 3 << (forwards ? nodeIndex : nodeIndex-1);
        for (std::vector<api::ApplyBucketDiffCommand::Entry>::const_iterator it
                 = diff.begin(); it != diff.end(); ++it)
        {
            if (it->filled()) continue;
            if ((it->_entry._hasMask & mask) == result) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if a diff from an ApplyBucketDiff message has all data
     * needed by this local node.
     */
    bool applyDiffHasLocallyNeededData(
            const std::vector<api::ApplyBucketDiffCommand::Entry>& diff,
            uint8_t nodeIndex)
    {
        uint32_t nodeMask = 1 << nodeIndex;
        bool foundEntries = false;
        for (std::vector<api::ApplyBucketDiffCommand::Entry>::const_iterator it
                 = diff.begin(); it != diff.end(); ++it)
        {
            // Ignore entries we don't need locally
            if ((it->_entry._hasMask & nodeMask) != 0) continue;
            foundEntries = true;
            if (it->filled()) return true;
        }
        if (foundEntries) {
            LOG(spam, "Merge(): Found entries needed, but they don't contain "
                      "data");
        }
        return false;
    }

    int
    countUnfilledEntries(
            const std::vector<api::ApplyBucketDiffCommand::Entry>& diff)
    {
        int count = 0;

        for (uint32_t i=0, n=diff.size(); i<n; ++i) {
            if (!diff[i].filled()) count++;
        }

        return count;
    };

    /**
     * Get the smallest value that is dividable by blocksize, but is not
     * smaller than value.
     */
    template<typename T>
    T align(T value, uint32_t blocksize) {
        value += blocksize - 1;
        value -= value % blocksize;
        return value;
    }

    api::StorageMessageAddress createAddress(const std::string& clusterName,
                                             uint16_t node)
    {
        return api::StorageMessageAddress(
                clusterName, lib::NodeType::STORAGE, node);
    }

    void assertContainedInBucket(const document::DocumentId& docId,
                                 const document::BucketId& bucket,
                                 const document::BucketIdFactory& idFactory)
    {
        document::BucketId docBucket(idFactory.getBucketId(docId));
        if (!bucket.contains(docBucket)) {
            LOG(error,
                "Broken bucket invariant discovered while fetching data from "
                "local persistence layer during merging; document %s does not "
                "belong in %s. Aborting to prevent broken document data from "
                "spreading to other nodes in the cluster.",
                docId.toString().c_str(),
                bucket.toString().c_str());
            assert(!"Document not contained in bucket");
        }
    }

} // End of anonymous namespace

void
MergeHandler::fetchLocalData(
        const spi::Bucket& bucket,
        const documentapi::LoadType& /*loadType*/,
        std::vector<api::ApplyBucketDiffCommand::Entry>& diff,
        uint8_t nodeIndex,
        spi::Context& context)
{
    uint32_t nodeMask = 1 << nodeIndex;
        // Preload documents in memory
    std::vector<spi::Timestamp> slots;
    uint32_t alreadyFilled = 0;
    for (uint32_t i=0, n=diff.size(); i<n; ++i) {
        api::ApplyBucketDiffCommand::Entry& e(diff[i]);
        if ((e._entry._hasMask & nodeMask) != 0 && !e.filled()) {
            slots.push_back(spi::Timestamp(e._entry._timestamp));
        }
        if (e.filled()) {
            alreadyFilled += e._headerBlob.size() + e._bodyBlob.size();
        }
    }
    uint32_t remainingSize = _maxChunkSize - std::min(_maxChunkSize,
                                                      alreadyFilled);
    LOG(debug, "Diff of %s has already filled %u of max %u bytes, "
        "remaining size to fill is %u",
        bucket.toString().c_str(), alreadyFilled, _maxChunkSize, remainingSize);
    if (remainingSize == 0) {
        LOG(debug,
            "Diff already at max chunk size, not fetching any local data");
        return;
    }

    spi::DocumentSelection docSel("");

    spi::Selection sel(docSel);
    sel.setTimestampSubset(slots);
    spi::CreateIteratorResult createIterResult(
            _spi.createIterator(bucket,
                                document::AllFields(),
                                sel,
                                spi::NEWEST_DOCUMENT_OR_REMOVE,
                                context));

    if (createIterResult.getErrorCode() != spi::Result::NONE) {
        std::ostringstream ss;
        ss << "Failed to create iterator for "
           << bucket.toString()
           << ": "
           << createIterResult.getErrorMessage();
        throw std::runtime_error(ss.str());
    }
    spi::IteratorId iteratorId(createIterResult.getIteratorId());
    IteratorGuard iteratorGuard(_spi, iteratorId, context);

    // Fetch all entries
    std::vector<spi::DocEntry::UP> entries;
    entries.reserve(slots.size());
    bool fetchedAllLocalData = false;
    bool chunkLimitReached = false;
    while (true) {
        spi::IterateResult result(
                _spi.iterate(iteratorId, remainingSize, context));
        if (result.getErrorCode() != spi::Result::NONE) {
            std::ostringstream ss;
            ss << "Failed to iterate for "
               << bucket.toString()
               << ": "
               << result.getErrorMessage();
            throw std::runtime_error(ss.str());
        }
        auto list = result.steal_entries();
        for (size_t i = 0; i < list.size(); ++i) {
            if (list[i]->getSize() <= remainingSize
                || (entries.empty() && alreadyFilled == 0))
            {
                remainingSize -= list[i]->getSize();
                LOG(spam, "Added %s, remainingSize is %u",
                    entries.back()->toString().c_str(),
                    remainingSize);
                entries.push_back(std::move(list[i]));
            } else {
                LOG(spam, "Adding %s would exceed chunk size limit of %u; "
                    "not filling up any more diffs for current round",
                    list[i]->toString().c_str(), _maxChunkSize);
                chunkLimitReached = true;
                break;
            }
        }
        if (result.isCompleted() && !chunkLimitReached) {
            fetchedAllLocalData = true;
            break;
        } else if (chunkLimitReached) {
            break;
        }
    }

    document::BucketIdFactory idFactory;

    for (size_t i=0; i<entries.size(); ++i) {
        const spi::DocEntry& docEntry(*entries[i]);
        LOG(spam, "fetchLocalData: processing %s",
            docEntry.toString().c_str());

        std::vector<api::ApplyBucketDiffCommand::Entry>::iterator iter(
                std::lower_bound(diff.begin(),
                                 diff.end(),
                                 api::Timestamp(docEntry.getTimestamp()),
                                 DiffEntryTimestampPredicate()));
        assert(iter != diff.end());
        assert(iter->_entry._timestamp == docEntry.getTimestamp());
        api::ApplyBucketDiffCommand::Entry& e(*iter);

        if (!docEntry.isRemove()) {
            const Document* doc = docEntry.getDocument();
            assert(doc != 0);
            assertContainedInBucket(doc->getId(), bucket, idFactory);
            e._docName = doc->getId().toString();
            {
                vespalib::nbostream stream;
                doc->serializeHeader(stream);
                e._headerBlob.resize(stream.size());
                memcpy(&e._headerBlob[0], stream.peek(), stream.size());
            }
            {
                vespalib::nbostream stream;
                doc->serializeBody(stream);
                e._bodyBlob.resize(stream.size());
                memcpy(&e._bodyBlob[0], stream.peek(), stream.size());
            }
        } else {
            const DocumentId* docId = docEntry.getDocumentId();
            assert(docId != 0);
            assertContainedInBucket(*docId, bucket, idFactory);
            if (e._entry._flags & DELETED) {
                e._docName = docId->toString();
            } else {
                LOG(debug, "Diff contains non-remove entry %s, but local entry "
                    "was remove entry %s. Node will be removed from hasmask",
                    e.toString().c_str(), docEntry.toString().c_str());
            }
        }
        e._repo = _env._repo.get();
     }

    for (size_t i=0; i<diff.size(); ++i) {
        api::ApplyBucketDiffCommand::Entry& e(diff[i]);
        if ((e._entry._hasMask & nodeMask) == 0 || e.filled()) {
            continue;
        }
        if (fetchedAllLocalData) {
            e._entry._hasMask &= ~nodeMask;
            LOG(debug, "During merge, slot %" PRIu64 " no longer exists. "
                       "Removing it from hasmask of current node.",
                e._entry._timestamp);
        }
     }

    LOG(spam, "Fetched %" PRIu64 " entries locally to fill out diff for %s. "
        "Still %d unfilled entries",
        entries.size(), bucket.toString().c_str(), countUnfilledEntries(diff));
}

document::Document::UP
MergeHandler::deserializeDiffDocument(
        const api::ApplyBucketDiffCommand::Entry& e,
        const document::DocumentTypeRepo& repo) const
{
    Document::UP doc(new Document);
    using document::ByteBuffer;
    ByteBuffer hbuf(&e._headerBlob[0], e._headerBlob.size());
    if (e._bodyBlob.size() > 0) {
        ByteBuffer bbuf(&e._bodyBlob[0], e._bodyBlob.size());
        doc->deserialize(repo, hbuf, bbuf);
    } else {
        doc->deserialize(repo, hbuf);
    }
    return doc;
}

void
MergeHandler::applyDiffEntry(const spi::Bucket& bucket,
                             const api::ApplyBucketDiffCommand::Entry& e,
                             spi::Context& context,
                             const document::DocumentTypeRepo& repo)
{
    spi::Timestamp timestamp(e._entry._timestamp);
    if (!(e._entry._flags & (DELETED | DELETED_IN_PLACE))) {
        // Regular put entry
        Document::SP doc(deserializeDiffDocument(e, repo));
        checkResult(_spi.put(bucket, timestamp, doc, context),
                    bucket,
                    doc->getId(),
                    "put");
    } else {
        DocumentId docId(e._docName);
        checkResult(_spi.remove(bucket, timestamp, docId, context),
                    bucket,
                    docId,
                    "remove");
    }
}

/**
 * Apply the diffs needed locally.
 */
api::BucketInfo
MergeHandler::applyDiffLocally(
        const spi::Bucket& bucket,
        const documentapi::LoadType& /*loadType*/,
        std::vector<api::ApplyBucketDiffCommand::Entry>& diff,
        uint8_t nodeIndex,
        spi::Context& context)
{
    // Sort the data to apply by which file they should be added to
    LOG(spam, "Merge(%s): Applying data locally. Diff has %zu entries",
        bucket.toString().c_str(),
        diff.size());
    uint32_t nodeMask = 1 << nodeIndex;
    uint32_t byteCount = 0;
    uint32_t addedCount = 0;
    uint32_t notNeededByteCount = 0;

    std::vector<spi::DocEntry::UP> entries;
    populateMetaData(bucket, MAX_TIMESTAMP, entries, context);

    FlushGuard flushGuard(_spi, bucket, context);

    std::shared_ptr<const document::DocumentTypeRepo> repo(_env._component.getTypeRepo());
    assert(repo.get() != nullptr);

    uint32_t existingCount = entries.size();
    uint32_t i = 0, j = 0;
    while (i < diff.size() && j < existingCount) {
        api::ApplyBucketDiffCommand::Entry& e(diff[i]);
        const spi::DocEntry& existing(*entries[j]);

        if (spi::Timestamp(e._entry._timestamp) > existing.getTimestamp()) {
            ++j;
            LOG(spam, "ApplyBucketDiff(%s): slot %s not in diff and "
                "already present in persistence", bucket.toString().c_str(),
                existing.toString().c_str());
            continue;
        }
        if ((e._entry._hasMask & nodeMask) != 0) {
            ++i;
            if (!e.filled()) continue;
            notNeededByteCount += e._headerBlob.size() + e._bodyBlob.size();
            continue;
        }
        if (!e.filled()) {
            ++i;
            LOG(debug, "Failed to apply unretrieved entry %s to diff "
                "locally on %s. Entry was probably compacted away.",
                e.toString().c_str(), bucket.toString().c_str());
            continue;
        }

        e._entry._hasMask |= nodeMask;
        if (spi::Timestamp(e._entry._timestamp) < existing.getTimestamp()) {
            ++i;
            LOG(spam, "ApplyBucketDiff(%s): Adding slot %s",
                bucket.toString().c_str(), e.toString().c_str());
            applyDiffEntry(bucket, e, context, *repo);
        } else {
            assert(spi::Timestamp(e._entry._timestamp)
                   == existing.getTimestamp());
            // Diffing for existing timestamp; should either both be put
            // dupes (which is a common case) or the new entry should be an
            // unrevertable remove.
            ++i;
            ++j;
            if ((e._entry._flags & DELETED) && !existing.isRemove()) {
                LOG(debug, "Slot in diff is remove for existing "
                    "timestamp in %s. Diff slot: %s. Existing slot: %s",
                    bucket.toString().c_str(), e.toString().c_str(),
                    existing.toString().c_str());
                applyDiffEntry(bucket, e, context, *repo);
            } else {
                // Duplicate put, just ignore it.
                LOG(debug, "During diff apply, attempting to add slot "
                    "whose timestamp already exists in %s, but assuming "
                    "these are for the same entry--ignoring it. "
                    "Diff slot: %s. Existing slot: %s",
                    bucket.toString().c_str(), e.toString().c_str(),
                    existing.toString().c_str());
            }
            continue;
        }
        byteCount += e._headerBlob.size() + e._bodyBlob.size();
    }
    // Handle remaining entries in diff
    for (; i < diff.size(); ++i) {
        api::ApplyBucketDiffCommand::Entry& e(diff[i]);
        if ((e._entry._hasMask & nodeMask) != 0) {
            if (!e.filled()) continue;
            notNeededByteCount += e._headerBlob.size() + e._bodyBlob.size();
            continue;
        }
        if (!e.filled()) {
            LOG(debug, "Failed to apply unretrieved entry %s to diff "
                "locally on %s. Entry was probably compacted away.",
                e.toString().c_str(), bucket.toString().c_str());
            continue;
        }
        e._entry._hasMask |= nodeMask;
        LOG(spam, "ApplyBucketDiff(%s): Adding slot %s",
            bucket.toString().c_str(), e.toString().c_str());

        applyDiffEntry(bucket, e, context, *repo);
        byteCount += e._headerBlob.size() + e._bodyBlob.size();
    }

    if (byteCount + notNeededByteCount != 0) {
        _env._metrics.mergeAverageDataReceivedNeeded.addValue(
                static_cast<double>(byteCount) / (byteCount + notNeededByteCount));
    }
    _env._metrics.bytesMerged.inc(byteCount);
    LOG(debug, "Merge(%s): Applied %u entries locally from ApplyBucketDiff.",
        bucket.toString().c_str(), addedCount);

    flushGuard.flush();

    spi::BucketInfoResult infoResult(_spi.getBucketInfo(bucket));
    if (infoResult.getErrorCode() != spi::Result::NONE) {
        LOG(warning, "Failed to get bucket info for %s: %s",
            bucket.toString().c_str(),
            infoResult.getErrorMessage().c_str());
        throw std::runtime_error("Failed to invoke getBucketInfo on "
                                 "persistence provider");
    }
    const spi::BucketInfo& tmpInfo(infoResult.getBucketInfo());
    api::BucketInfo providerInfo(tmpInfo.getChecksum(),
                                 tmpInfo.getDocumentCount(),
                                 tmpInfo.getDocumentSize(),
                                 tmpInfo.getEntryCount(),
                                 tmpInfo.getUsedSize(),
                                 tmpInfo.isReady(),
                                 tmpInfo.isActive());

    _env.updateBucketDatabase(bucket.getBucket(), providerInfo);
    return providerInfo;
}

namespace {
    void findCandidates(const document::BucketId& id, MergeStatus& status,
                        bool constrictHasMask, uint16_t hasMask,
                        uint16_t newHasMask,
                        uint32_t maxSize, api::ApplyBucketDiffCommand& cmd)
    {
        uint32_t chunkSize = 0;
        for (std::deque<api::GetBucketDiffCommand::Entry>::const_iterator it
                 = status.diff.begin(); it != status.diff.end(); ++it)
        {
            if (constrictHasMask && it->_hasMask != hasMask) {
                continue;
            }
            if (chunkSize != 0 &&
                chunkSize + it->_bodySize + it->_headerSize > maxSize)
            {
                LOG(spam, "Merge of %s used %d bytes, max is %d. Will "
                          "fetch in next merge round.",
                          id.toString().c_str(),
                          chunkSize + it->_bodySize + it->_headerSize,
                          maxSize);
                break;
            }
            chunkSize += it->_bodySize + it->_headerSize;
            cmd.getDiff().push_back(api::ApplyBucketDiffCommand::Entry(*it));
            if (constrictHasMask) {
                cmd.getDiff().back()._entry._hasMask = newHasMask;
            }
        }
    }
}

api::StorageReply::SP
MergeHandler::processBucketMerge(const spi::Bucket& bucket, MergeStatus& status,
                                 MessageSender& sender, spi::Context& context)
{
    // If last action failed, fail the whole merge
    if (status.reply->getResult().failed()) {
        LOG(warning, "Done with merge of %s (failed: %s) %s",
            bucket.toString().c_str(),
            status.reply->getResult().toString().c_str(),
            status.toString().c_str());
        return status.reply;
    }

    // If nothing to update, we're done.
    if (status.diff.size() == 0) {
        LOG(debug, "Done with merge of %s. No more entries in diff.",
            bucket.toString().c_str());
        return status.reply;
    }

    LOG(spam, "Processing merge of %s. %u entries left to merge.",
        bucket.toString().c_str(), (uint32_t) status.diff.size());
    std::shared_ptr<api::ApplyBucketDiffCommand> cmd;

    // If we still have a source only node, eliminate that one from the
    // merge.
    while (status.nodeList.back().sourceOnly) {
        std::vector<api::MergeBucketCommand::Node> nodes;
        for (uint16_t i=0; i<status.nodeList.size(); ++i) {
            if (!status.nodeList[i].sourceOnly) {
                nodes.push_back(status.nodeList[i]);
            }
        }
        nodes.push_back(status.nodeList.back());
        assert(nodes.size() > 1);

        // Add all the metadata, and thus use big limit. Max
        // data to fetch parameter will control amount added.
        uint32_t maxSize =
            (_env._config.enableMergeLocalNodeChooseDocsOptimalization
             ? std::numeric_limits<uint32_t>().max()
             : _maxChunkSize);

        cmd.reset(new api::ApplyBucketDiffCommand(
                          bucket.getBucket(), nodes, maxSize));
        cmd->setAddress(createAddress(_env._component.getClusterName(),
                                      nodes[1].index));
        findCandidates(bucket.getBucketId(),
                       status,
                       true,
                       1 << (status.nodeList.size() - 1),
                       1 << (nodes.size() - 1),
                       maxSize,
                       *cmd);
        if (cmd->getDiff().size() != 0) break;
        cmd.reset();
            // If we found no data to merge from the last source only node,
            // remove it and retry. (Clear it out of the hasmask such that we
            // can match hasmask with operator==)
        status.nodeList.pop_back();
        uint16_t mask = ~(1 << status.nodeList.size());
        for (std::deque<api::GetBucketDiffCommand::Entry>::iterator it
                 = status.diff.begin(); it != status.diff.end(); ++it)
        {
            it->_hasMask &= mask;
        }
            // If only one node left in the merge, return ok.
        if (status.nodeList.size() == 1) {
            LOG(debug, "Done with merge of %s as there is only one node "
                       "that is not source only left in the merge.",
                bucket.toString().c_str());
            return status.reply;
        }
    }
        // If we did not have a source only node, check if we have a path with
        // many documents within it that we'll merge separately
    if (cmd.get() == 0) {
        std::map<uint16_t, uint32_t> counts;
        for (std::deque<api::GetBucketDiffCommand::Entry>::const_iterator it
                 = status.diff.begin(); it != status.diff.end(); ++it)
        {
            ++counts[it->_hasMask];
        }
        for (std::map<uint16_t, uint32_t>::const_iterator it = counts.begin();
             it != counts.end(); ++it)
        {
            if (it->second >= uint32_t(
                        _env._config.commonMergeChainOptimalizationMinimumSize)
                || counts.size() == 1)
            {
                LOG(spam, "Sending separate apply bucket diff for path %x "
                    "with size %u",
                    it->first, it->second);
                std::vector<api::MergeBucketCommand::Node> nodes;
                    // This node always has to be first in chain.
                nodes.push_back(status.nodeList[0]);
                    // Add all the nodes that lack the docs in question
                for (uint16_t i=1; i<status.nodeList.size(); ++i) {
                    if ((it->first & (1 << i)) == 0) {
                        nodes.push_back(status.nodeList[i]);
                    }
                }
                uint16_t newMask = 1;
                    // If this node doesn't have the docs, add a node that has
                    // them to the end of the chain, so the data is applied
                    // going back.
                if ((it->first & 1) == 0) {
                    for (uint16_t i=1; i<status.nodeList.size(); ++i) {
                        if ((it->first & (1 << i)) != 0) {
                            nodes.push_back(status.nodeList[i]);
                            break;
                        }
                    }
                    newMask = 1 << (nodes.size() - 1);
                }
                assert(nodes.size() > 1);
                uint32_t maxSize =
                    (_env._config.enableMergeLocalNodeChooseDocsOptimalization
                     ? std::numeric_limits<uint32_t>().max()
                     : _maxChunkSize);
                cmd.reset(new api::ApplyBucketDiffCommand(
                                  bucket.getBucket(), nodes, maxSize));
                cmd->setAddress(
                        createAddress(_env._component.getClusterName(),
                                      nodes[1].index));
                    // Add all the metadata, and thus use big limit. Max
                    // data to fetch parameter will control amount added.
                findCandidates(bucket.getBucketId(), status, true,
                               it->first, newMask, maxSize, *cmd);
                break;
            }
        }
    }

    // If we found no group big enough to handle on its own, do a common
    // merge to merge the remaining data.
    if (cmd.get() == 0) {
        cmd.reset(new api::ApplyBucketDiffCommand(bucket.getBucket(),
                                                  status.nodeList,
                                                  _maxChunkSize));
        cmd->setAddress(createAddress(_env._component.getClusterName(),
                                      status.nodeList[1].index));
        findCandidates(bucket.getBucketId(), status, false, 0, 0,
                       _maxChunkSize, *cmd);
    }
    cmd->setPriority(status.context.getPriority());
    cmd->setTimeout(status.timeout);
    if (applyDiffNeedLocalData(cmd->getDiff(), 0, true)) {
        framework::MilliSecTimer startTime(_env._component.getClock());
        fetchLocalData(bucket, cmd->getLoadType(), cmd->getDiff(), 0, context);
        _env._metrics.mergeDataReadLatency.addValue(
                startTime.getElapsedTimeAsDouble());
    }
    status.pendingId = cmd->getMsgId();
    LOG(debug, "Sending %s", cmd->toString().c_str());
    sender.sendCommand(cmd);
    return api::StorageReply::SP();
}

/** Ensures merge states are deleted if we fail operation */
class MergeStateDeleter {
public:
    FileStorHandler& _handler;
    document::Bucket _bucket;
    bool _active;

    MergeStateDeleter(FileStorHandler& handler,
                      const document::Bucket& bucket)
        : _handler(handler),
          _bucket(bucket),
          _active(true)
    {
    }

    ~MergeStateDeleter() {
        if (_active) {
            _handler.clearMergeStatus(_bucket);
        }
    }

    void deactivate() { _active = false; }
};

MessageTracker::UP
MergeHandler::handleMergeBucket(api::MergeBucketCommand& cmd,
                                spi::Context& context)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.mergeBuckets,
                                       _env._component.getClock()));

    spi::Bucket bucket(cmd.getBucket(), spi::PartitionId(_env._partition));
    LOG(debug, "MergeBucket(%s) with max timestamp %" PRIu64 ".",
        bucket.toString().c_str(), cmd.getMaxTimestamp());

    if (cmd.getNodes().size() < 2) {
        LOG(debug, "Attempt to merge a single instance of a bucket");
        tracker->fail(ReturnCode::ILLEGAL_PARAMETERS,
                     "Cannot merge a single copy");
        return tracker;
    }

    // Verify that first node is not source only, and that all source only
    // nodes are at end of chain
    for (uint16_t i=0; i<cmd.getNodes().size(); ++i) {
        if (i == 0) {
            if (cmd.getNodes()[i].sourceOnly) {
                tracker->fail(ReturnCode::ILLEGAL_PARAMETERS,
                             "Attempted to merge a chain where the first node "
                             "in the chain is source only.");
                return tracker;
            }
        } else {
            if (!cmd.getNodes()[i].sourceOnly
                && cmd.getNodes()[i-1].sourceOnly)
            {
                tracker->fail(ReturnCode::ILLEGAL_PARAMETERS,
                             "Attempted to merge a chain where the source only "
                             "copies are not in end of chain.");
                return tracker;
            }
        }
    }

    if (_env._fileStorHandler.isMerging(bucket.getBucket())) {
        const char* err = "A merge is already running on this bucket.";
        LOG(debug, "%s", err);
        tracker->fail(ReturnCode::BUSY, err);
        return tracker;
    }
    checkResult(_spi.createBucket(bucket, context), bucket, "create bucket");

    MergeStateDeleter stateGuard(_env._fileStorHandler, bucket.getBucket());
    MergeStatus::SP s = MergeStatus::SP(new MergeStatus(
            _env._component.getClock(), cmd.getLoadType(),
            cmd.getPriority(), cmd.getTrace().getLevel()));
    _env._fileStorHandler.addMergeStatus(bucket.getBucket(), s);
    s->nodeList = cmd.getNodes();
    s->maxTimestamp = Timestamp(cmd.getMaxTimestamp());
    s->timeout = cmd.getTimeout();
    s->startTime = framework::MilliSecTimer(_env._component.getClock());

    std::shared_ptr<api::GetBucketDiffCommand> cmd2(
            new api::GetBucketDiffCommand(bucket.getBucket(),
                                          s->nodeList,
                                          s->maxTimestamp.getTime()));
    if (!buildBucketInfoList(bucket,
                             cmd.getLoadType(),
                             s->maxTimestamp,
                             0,
                             cmd2->getDiff(),
                             context))
    {
        LOG(debug, "Bucket non-existing in db. Failing merge.");
        tracker->fail(ReturnCode::BUCKET_DELETED,
                     "Bucket not found in buildBucketInfo step");
        return tracker;
    }
    _env._metrics.mergeMetadataReadLatency.addValue(
            s->startTime.getElapsedTimeAsDouble());
    LOG(spam, "Sending GetBucketDiff %" PRIu64 " for %s to next node %u "
        "with diff of %u entries.",
        cmd2->getMsgId(),
        bucket.toString().c_str(),
        s->nodeList[1].index,
        uint32_t(cmd2->getDiff().size()));
    cmd2->setAddress(createAddress(_env._component.getClusterName(),
                                   s->nodeList[1].index));
    cmd2->setPriority(s->context.getPriority());
    cmd2->setTimeout(s->timeout);
    cmd2->setSourceIndex(cmd.getSourceIndex());

    s->pendingId = cmd2->getMsgId();
    _env._fileStorHandler.sendCommand(cmd2);
    // All went well. Dont delete state or send reply.
    stateGuard.deactivate();
    s->reply = api::StorageReply::SP(cmd.makeReply().release());
    tracker->dontReply();
    return tracker;
}

namespace {

    uint8_t findOwnIndex(
            const std::vector<api::MergeBucketCommand::Node>& nodeList,
            uint16_t us)
    {
        for (uint32_t i=0, n=nodeList.size(); i<n; ++i) {
            if (nodeList[i].index == us) return i;
        }
        throw vespalib::IllegalStateException(
                "Got GetBucketDiff cmd on node not in nodelist in command",
                VESPA_STRLOC);
    }

    struct DiffEntryTimestampOrder
        : public std::binary_function<api::GetBucketDiffCommand::Entry,
                                      api::GetBucketDiffCommand::Entry, bool>
    {
        bool operator()(const api::GetBucketDiffCommand::Entry& x,
                        const api::GetBucketDiffCommand::Entry& y) const
        { return (x._timestamp < y._timestamp); }
    };

    /**
     * Merges list A and list B together and puts the result in result.
     * Result is swapped in as last step to keep function exception safe. Thus
     * result can be listA or listB if wanted.
     *
     * listA and listB are assumed to be in the order found in the slotfile, or
     * in the order given by a previous call to this function. (In both cases
     * this will be sorted by timestamp)
     *
     * @return false if any suspect entries was found.
     */
    bool mergeLists(
            const std::vector<api::GetBucketDiffCommand::Entry>& listA,
            const std::vector<api::GetBucketDiffCommand::Entry>& listB,
            std::vector<api::GetBucketDiffCommand::Entry>& finalResult)
    {
        bool suspect = false;
        std::vector<api::GetBucketDiffCommand::Entry> result;
        uint32_t i = 0, j = 0;
        while (i < listA.size() && j < listB.size()) {
            const api::GetBucketDiffCommand::Entry& a(listA[i]);
            const api::GetBucketDiffCommand::Entry& b(listB[j]);
            if (a._timestamp < b._timestamp) {
                result.push_back(a);
                ++i;
            } else if (a._timestamp > b._timestamp) {
                result.push_back(b);
                ++j;
            } else {
                    // If we find equal timestamped entries that are not the
                    // same.. Flag an error. But there is nothing we can do
                    // about it. Note it as if it is the same entry so we
                    // dont try to merge them.
                if (!(a == b)) {
                    if (a._gid == b._gid && a._flags == b._flags) {
                        if ((a._flags & getDeleteFlag()) != 0 &&
                            (b._flags & getDeleteFlag()) != 0)
                        {
                                // Unfortunately this can happen, for instance
                                // if a remove comes to a bucket out of sync
                                // and reuses different headers in the two
                                // versions.
                            LOG(debug, "Found entries with equal timestamps of "
                                       "the same gid who both are remove "
                                       "entries: %s <-> %s.",
                                a.toString(true).c_str(),
                                b.toString(true).c_str());
                        } else {
                            LOG(error, "Found entries with equal timestamps of "
                                       "the same gid. This is likely same "
                                       "document where size of document varies:"
                                       " %s <-> %s.",
                                a.toString(true).c_str(),
                                b.toString(true).c_str());
                        }
                        result.push_back(a);
                        result.back()._hasMask |= b._hasMask;
                        suspect = true;
                    } else if ((a._flags & getDeleteFlag())
                               != (b._flags & getDeleteFlag()))
                    {
                            // If we find one remove and one put entry on the
                            // same timestamp we are going to keep the remove
                            // entry to make the copies consistent.
                        const api::GetBucketDiffCommand::Entry& deletedEntry(
                                (a._flags & getDeleteFlag()) != 0 ? a : b);
                        result.push_back(deletedEntry);
                        LOG(debug,
                            "Found put and remove on same timestamp. Keeping"
                            "remove as it is likely caused by remove with "
                            "copies unavailable at the time: %s, %s.",
                            a.toString().c_str(), b.toString().c_str());
                    } else {
                        LOG(error, "Found entries with equal timestamps that "
                                   "weren't the same entry: %s, %s.",
                            a.toString().c_str(), b.toString().c_str());
                        result.push_back(a);
                        result.back()._hasMask |= b._hasMask;
                        suspect = true;
                    }
                } else {
                    result.push_back(a);
                    result.back()._hasMask |= b._hasMask;
                }
                ++i;
                ++j;
            }
        }
        if (i < listA.size()) {
            assert(j >= listB.size());
            for (uint32_t n = listA.size(); i<n; ++i) {
                result.push_back(listA[i]);
            }
        } else if (j < listB.size()) {
            assert(i >= listA.size());
            for (uint32_t n = listB.size(); j<n; ++j) {
                result.push_back(listB[j]);
            }
        }
        result.swap(finalResult);
        return !suspect;
    }

}

MessageTracker::UP
MergeHandler::handleGetBucketDiff(api::GetBucketDiffCommand& cmd,
                                  spi::Context& context)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.getBucketDiff,
                                       _env._component.getClock()));
    spi::Bucket bucket(cmd.getBucket(), spi::PartitionId(_env._partition));
    LOG(debug, "GetBucketDiff(%s)", bucket.toString().c_str());
    checkResult(_spi.createBucket(bucket, context), bucket, "create bucket");

    if (_env._fileStorHandler.isMerging(bucket.getBucket())) {
        tracker->fail(ReturnCode::BUSY,
                     "A merge is already running on this bucket.");
        return tracker;
    }
    uint8_t index = findOwnIndex(cmd.getNodes(), _env._nodeIndex);
    // Merge info for retrieved and local info.
    std::vector<api::GetBucketDiffCommand::Entry>& remote(cmd.getDiff());
    std::vector<api::GetBucketDiffCommand::Entry> local;
    framework::MilliSecTimer startTime(_env._component.getClock());
    if (!buildBucketInfoList(bucket, cmd.getLoadType(),
                             Timestamp(cmd.getMaxTimestamp()),
                             index, local, context))
    {
        LOG(debug, "Bucket non-existing in db. Failing merge.");
        tracker->fail(ReturnCode::BUCKET_DELETED,
                     "Bucket not found in buildBucketInfo step");
        return tracker;
    }
    if (!mergeLists(remote, local, local)) {
        LOG(error, "Diffing %s found suspect entries.",
            bucket.toString().c_str());
    }
    _env._metrics.mergeMetadataReadLatency.addValue(
            startTime.getElapsedTimeAsDouble());

    // If last node in merge chain, we can send reply straight away
    if (index + 1u >= cmd.getNodes().size()) {
        // Remove entries everyone has from list first.
        uint16_t completeMask = 0;
        for (uint32_t i=0; i<cmd.getNodes().size(); ++i) {
            if (!cmd.getNodes()[i].sourceOnly) {
                completeMask |= (1 << i);
            }
        }
        std::vector<api::GetBucketDiffCommand::Entry> final;
        for (uint32_t i=0, n=local.size(); i<n; ++i) {
            if ((local[i]._hasMask & completeMask) != completeMask) {
                final.push_back(local[i]);
            }
        }
        // Send reply
        LOG(spam, "Replying to GetBucketDiff %" PRIu64 " for %s to node %d"
                  ". Diff has %" PRIu64 " entries. (%" PRIu64 " before compaction)",
            cmd.getMsgId(), bucket.toString().c_str(),
            cmd.getNodes()[index - 1].index, final.size(), local.size());

        api::GetBucketDiffReply* reply = new api::GetBucketDiffReply(cmd);
        tracker->setReply(api::StorageReply::SP(reply));
        reply->getDiff().swap(final);
    } else {
        // When not the last node in merge chain, we must save reply, and
        // send command on.
        MergeStateDeleter stateGuard(_env._fileStorHandler, bucket.getBucket());
        MergeStatus::SP s(new MergeStatus(_env._component.getClock(),
                                          cmd.getLoadType(), cmd.getPriority(),
                                          cmd.getTrace().getLevel()));
        _env._fileStorHandler.addMergeStatus(bucket.getBucket(), s);

        s->pendingGetDiff =
            api::GetBucketDiffReply::SP(new api::GetBucketDiffReply(cmd));
        s->pendingGetDiff->setPriority(cmd.getPriority());

        LOG(spam, "Sending GetBucketDiff for %s on to node %d, "
                  "added %" PRIu64 " new entries to diff.",
            bucket.toString().c_str(), cmd.getNodes()[index + 1].index,
            local.size() - remote.size());
        std::shared_ptr<api::GetBucketDiffCommand> cmd2(
                new api::GetBucketDiffCommand(
                    bucket.getBucket(), cmd.getNodes(), cmd.getMaxTimestamp()));
        cmd2->setAddress(createAddress(_env._component.getClusterName(),
                                       cmd.getNodes()[index + 1].index));
        cmd2->getDiff().swap(local);
        cmd2->setPriority(cmd.getPriority());
        cmd2->setTimeout(cmd.getTimeout());
        s->pendingId = cmd2->getMsgId();
        _env._fileStorHandler.sendCommand(cmd2);

        // Everything went fine. Don't delete state but wait for reply
        stateGuard.deactivate();
        tracker->dontReply();
    }

    return tracker;
}

namespace {

    struct DiffInfoTimestampOrder
        : public std::binary_function<api::GetBucketDiffCommand::Entry,
                                      api::GetBucketDiffCommand::Entry, bool>
    {
        bool operator()(const api::GetBucketDiffCommand::Entry& x,
                        const api::GetBucketDiffCommand::Entry& y)
        {
            return (x._timestamp < y._timestamp);
        }
    };

    struct ApplyDiffInfoTimestampOrder
        : public std::binary_function<api::ApplyBucketDiffCommand::Entry,
                                      api::ApplyBucketDiffCommand::Entry, bool>
    {
        bool operator()(const api::ApplyBucketDiffCommand::Entry& x,
                        const api::ApplyBucketDiffCommand::Entry& y)
        {
            return (x._entry._timestamp
                        < y._entry._timestamp);
        }
    };

} // End of anonymous namespace

void
MergeHandler::handleGetBucketDiffReply(api::GetBucketDiffReply& reply,
                                       MessageSender& sender)
{
    ++_env._metrics.getBucketDiffReply;
    spi::Bucket bucket(reply.getBucket(), spi::PartitionId(_env._partition));
    LOG(debug, "GetBucketDiffReply(%s)", bucket.toString().c_str());

    if (!_env._fileStorHandler.isMerging(bucket.getBucket())) {
        LOG(warning, "Got GetBucketDiffReply for %s which we have no "
                     "merge state for.",
            bucket.toString().c_str());
        DUMP_LOGGED_BUCKET_OPERATIONS(bucket.getBucketId());
        return;
    }

    MergeStatus& s = _env._fileStorHandler.editMergeStatus(bucket.getBucket());
    if (s.pendingId != reply.getMsgId()) {
        LOG(warning, "Got GetBucketDiffReply for %s which had message "
                     "id %" PRIu64 " when we expected %" PRIu64 ". Ignoring reply.",
            bucket.toString().c_str(), reply.getMsgId(), s.pendingId);
        DUMP_LOGGED_BUCKET_OPERATIONS(bucket.getBucketId());
        return;
    }
    api::StorageReply::SP replyToSend;
    bool clearState = true;

    try {
        if (s.isFirstNode()) {
            if (reply.getResult().failed()) {
                // We failed, so we should reply to the pending message.
                replyToSend = s.reply;
            } else {
                // If we didn't fail, reply should have good content
                // Sanity check for nodes
                assert(reply.getNodes().size() >= 2);

                // Get bucket diff should retrieve all info at once
                assert(s.diff.size() == 0);
                s.diff.insert(s.diff.end(),
                              reply.getDiff().begin(),
                              reply.getDiff().end());

                replyToSend = processBucketMerge(bucket, s, sender, s.context);

                if (!replyToSend.get()) {
                    // We have sent something on, and shouldn't reply now.
                    clearState = false;
                } else {
                    _env._metrics.mergeLatencyTotal.addValue(
                            s.startTime.getElapsedTimeAsDouble());
                }
            }
        } else {
            // Exists in send on list, send on!
            replyToSend = s.pendingGetDiff;
            LOG(spam, "Received GetBucketDiffReply for %s with diff of "
                "size %" PRIu64 ". Sending it on.",
                bucket.toString().c_str(), reply.getDiff().size());
            s.pendingGetDiff->getDiff().swap(reply.getDiff());
        }
    } catch (std::exception& e) {
        _env._fileStorHandler.clearMergeStatus(
                bucket.getBucket(),
                api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE,
                                e.what()));
        throw;
    } catch (...) {
        assert(false);
    }

    if (clearState) {
        _env._fileStorHandler.clearMergeStatus(bucket.getBucket());
    }
    if (replyToSend.get()) {
        replyToSend->setResult(reply.getResult());
        sender.sendReply(replyToSend);
    }
}

MessageTracker::UP
MergeHandler::handleApplyBucketDiff(api::ApplyBucketDiffCommand& cmd,
                                    spi::Context& context)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.applyBucketDiff,
                                       _env._component.getClock()));

    spi::Bucket bucket(cmd.getBucket(), spi::PartitionId(_env._partition));
    LOG(debug, "%s", cmd.toString().c_str());

    if (_env._fileStorHandler.isMerging(bucket.getBucket())) {
        tracker->fail(ReturnCode::BUSY,
                      "A merge is already running on this bucket.");
        return tracker;
    }

    uint8_t index = findOwnIndex(cmd.getNodes(), _env._nodeIndex);
    bool lastInChain = index + 1u >= cmd.getNodes().size();
    if (applyDiffNeedLocalData(cmd.getDiff(), index, !lastInChain)) {
       framework::MilliSecTimer startTime(_env._component.getClock());
        fetchLocalData(bucket, cmd.getLoadType(), cmd.getDiff(), index,
                       context);
        _env._metrics.mergeDataReadLatency.addValue(
                startTime.getElapsedTimeAsDouble());
    } else {
        LOG(spam, "Merge(%s): Moving %" PRIu64 " entries, didn't need "
                  "local data on node %u (%u).",
            bucket.toString().c_str(),
            cmd.getDiff().size(),
            _env._nodeIndex,
            index);
    }
    if (applyDiffHasLocallyNeededData(cmd.getDiff(), index)) {
       framework::MilliSecTimer startTime(_env._component.getClock());
        api::BucketInfo info(applyDiffLocally(bucket, cmd.getLoadType(),
                                              cmd.getDiff(), index, context));
        _env._metrics.mergeDataWriteLatency.addValue(
                startTime.getElapsedTimeAsDouble());
    } else {
        LOG(spam, "Merge(%s): Didn't need fetched data on node %u (%u).",
            bucket.toString().c_str(), _env._nodeIndex, index);
    }

    // If last node in merge chain, we can send reply straight away
    if (lastInChain) {
        // Unfill entries everyone has filled in before returning.
        uint16_t completeMask = 0;
        for (uint32_t i=0; i<cmd.getNodes().size(); ++i) {
            if (!cmd.getNodes()[i].sourceOnly) {
                completeMask |= (1 << i);
            }
        }
        std::vector<api::ApplyBucketDiffCommand::Entry>& local(cmd.getDiff());
        for (uint32_t i=0, n=local.size(); i<n; ++i) {
            if ((local[i]._entry._hasMask & completeMask) == completeMask) {
                local[i]._headerBlob.clear();
                local[i]._bodyBlob.clear();
                local[i]._docName.clear();
            }
        }

        tracker->setReply(api::StorageReply::SP(new api::ApplyBucketDiffReply(cmd)));
        static_cast<api::ApplyBucketDiffReply&>(*tracker->getReply()).getDiff().swap(
                cmd.getDiff());
        LOG(spam, "Replying to ApplyBucketDiff for %s to node %d.",
            bucket.toString().c_str(), cmd.getNodes()[index - 1].index);
    } else {
        // When not the last node in merge chain, we must save reply, and
        // send command on.
        MergeStateDeleter stateGuard(_env._fileStorHandler, bucket.getBucket());
        MergeStatus::SP s(new MergeStatus(_env._component.getClock(),
                                          cmd.getLoadType(), cmd.getPriority(),
                                          cmd.getTrace().getLevel()));
        _env._fileStorHandler.addMergeStatus(bucket.getBucket(), s);
        s->pendingApplyDiff =
            api::ApplyBucketDiffReply::SP(new api::ApplyBucketDiffReply(cmd));

        LOG(spam, "Sending ApplyBucketDiff for %s on to node %d",
            bucket.toString().c_str(), cmd.getNodes()[index + 1].index);
        std::shared_ptr<api::ApplyBucketDiffCommand> cmd2(
                new api::ApplyBucketDiffCommand(
                    bucket.getBucket(), cmd.getNodes(), cmd.getMaxBufferSize()));
        cmd2->setAddress(createAddress(_env._component.getClusterName(),
                                       cmd.getNodes()[index + 1].index));
        cmd2->getDiff().swap(cmd.getDiff());
        cmd2->setPriority(cmd.getPriority());
        cmd2->setTimeout(cmd.getTimeout());
        s->pendingId = cmd2->getMsgId();
        _env._fileStorHandler.sendCommand(cmd2);
            // Everything went fine. Don't delete state but wait for reply
        stateGuard.deactivate();
        tracker->dontReply();
    }

    return tracker;
}

void
MergeHandler::handleApplyBucketDiffReply(api::ApplyBucketDiffReply& reply,
                                         MessageSender& sender)
{
    ++_env._metrics.applyBucketDiffReply;
    spi::Bucket bucket(reply.getBucket(), spi::PartitionId(_env._partition));
    std::vector<api::ApplyBucketDiffCommand::Entry>& diff(reply.getDiff());
    LOG(debug, "%s", reply.toString().c_str());

    if (!_env._fileStorHandler.isMerging(bucket.getBucket())) {
        LOG(warning, "Got ApplyBucketDiffReply for %s which we have no "
                     "merge state for.",
            bucket.toString().c_str());
        DUMP_LOGGED_BUCKET_OPERATIONS(bucket.getBucketId());
        return;
    }

    MergeStatus& s = _env._fileStorHandler.editMergeStatus(bucket.getBucket());
    if (s.pendingId != reply.getMsgId()) {
        LOG(warning, "Got ApplyBucketDiffReply for %s which had message "
                     "id %" PRIu64 " when we expected %" PRIu64 ". Ignoring reply.",
            bucket.toString().c_str(), reply.getMsgId(), s.pendingId);
        DUMP_LOGGED_BUCKET_OPERATIONS(bucket.getBucketId());
        return;
    }
    bool clearState = true;
    api::StorageReply::SP replyToSend;
    // Process apply bucket diff locally
    api::ReturnCode returnCode = reply.getResult();
    try {
        if (reply.getResult().failed()) {
            LOG(debug, "Got failed apply bucket diff reply %s",
                reply.toString().c_str());
        } else {
            assert(reply.getNodes().size() >= 2);
            uint8_t index = findOwnIndex(reply.getNodes(), _env._nodeIndex);
            if (applyDiffNeedLocalData(diff, index, false)) {
                framework::MilliSecTimer startTime(_env._component.getClock());
                fetchLocalData(bucket, reply.getLoadType(), diff, index,
                               s.context);
                _env._metrics.mergeDataReadLatency.addValue(
                        startTime.getElapsedTimeAsDouble());
            }
            if (applyDiffHasLocallyNeededData(diff, index)) {
                framework::MilliSecTimer startTime(_env._component.getClock());
                api::BucketInfo info(
                        applyDiffLocally(bucket, reply.getLoadType(), diff,
                                         index, s.context));
                _env._metrics.mergeDataWriteLatency.addValue(
                        startTime.getElapsedTimeAsDouble());
            } else {
                LOG(spam, "Merge(%s): Didn't need fetched data on node %u (%u)",
                    bucket.toString().c_str(),
                    _env._nodeIndex,
                    static_cast<unsigned int>(index));
            }
        }

        if (s.isFirstNode()) {
            uint16_t hasMask = 0;
            for (uint16_t i=0; i<reply.getNodes().size(); ++i) {
                hasMask |= (1 << i);
            }

            const size_t diffSizeBefore = s.diff.size();
            const bool altered = s.removeFromDiff(diff, hasMask);
            if (reply.getResult().success()
                && s.diff.size() == diffSizeBefore
                && !altered)
            {
                std::string msg(
                        vespalib::make_string(
                                "Completed merge cycle without fixing "
                                "any entries (merge state diff at %zu entries)",
                                s.diff.size()));
                returnCode = api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE, msg);
                LOG(warning,
                    "Got reply indicating merge cycle did not fix any entries: %s",
                    reply.toString(true).c_str());
                LOG(warning,
                    "Merge state for which there was no progress across a "
                    "full merge cycle: %s",
                    s.toString().c_str());
            }

            if (returnCode.failed()) {
                // Should reply now, since we failed.
                replyToSend = s.reply;
            } else {
                replyToSend = processBucketMerge(bucket, s, sender, s.context);

                if (!replyToSend.get()) {
                    // We have sent something on and shouldn't reply now.
                    clearState = false;
                } else {
                    _env._metrics.mergeLatencyTotal.addValue(
                            s.startTime.getElapsedTimeAsDouble());
                }
            }
        } else {
            replyToSend = s.pendingApplyDiff;
            LOG(debug, "ApplyBucketDiff(%s) finished. Sending reply.",
                bucket.toString().c_str());
            s.pendingApplyDiff->getDiff().swap(reply.getDiff());
        }
    } catch (std::exception& e) {
        _env._fileStorHandler.clearMergeStatus(
                bucket.getBucket(),
                api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE,
                                e.what()));
        throw;
    } catch (...) {
        assert(false);
    }

    if (clearState) {
        _env._fileStorHandler.clearMergeStatus(bucket.getBucket());
    }
    if (replyToSend.get()) {
        // Send on
        replyToSend->setResult(returnCode);
        sender.sendReply(replyToSend);
    }
}

} // storage
