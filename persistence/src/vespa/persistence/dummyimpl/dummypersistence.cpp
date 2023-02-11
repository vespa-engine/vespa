// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummypersistence.h"
#include <vespa/document/select/parser.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/persistence/spi/i_resource_usage_listener.h>
#include <vespa/persistence/spi/resource_usage.h>
#include <vespa/persistence/spi/bucketexecutor.h>
#include <vespa/persistence/spi/test.h>
#include <vespa/vespalib/util/crc.h>
#include <vespa/document/fieldset/fieldsetrepo.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".dummypersistence");

using vespalib::make_string;
using std::binary_search;
using std::lower_bound;
using document::FixedBucketSpaces;
using document::FieldSet;
using storage::spi::test::cloneDocEntry;
using storage::spi::test::equal;

namespace storage::spi::dummy {

namespace {

std::string asString(const std::vector<BucketEntry> &) __attribute__((noinline));

std::string
asString(const std::vector<BucketEntry> & v) {
    vespalib::asciistream ost;
    for (const auto & e : v) {
        ost << e.entry->toString() << "\n";
    }

    return ost.str();
}

}

BucketContent::BucketContent() noexcept
    : _entries(),
      _gidMap(),
      _info(),
      _inUse(false),
      _outdatedInfo(true),
      _active(false)
{}

BucketContent::~BucketContent() = default;

uint32_t
BucketContent::computeEntryChecksum(const BucketEntry &e) const {
    vespalib::crc_32_type checksummer;

    uint64_t ts(e.entry->getTimestamp());
    checksummer.process_bytes(&e.gid, sizeof(GlobalId));
    checksummer.process_bytes(&ts, sizeof(uint64_t));
    return checksummer.checksum();
}

BucketChecksum
BucketContent::updateRollingChecksum(uint32_t entryChecksum) {
    uint32_t checksum = _info.getChecksum();
    checksum ^= entryChecksum;
    if (checksum == 0) {
        checksum = 1;
    }
    return BucketChecksum(checksum);
}

const BucketInfo &
BucketContent::getBucketInfo() const {
    if (!_outdatedInfo) {
        return _info;
    }

    // Checksum should only depend on the newest entry for each document that
    // has not been removed.
    uint32_t unique = 0;
    uint32_t uniqueSize = 0;
    uint32_t totalSize = 0;
    uint32_t checksum = 0;

    for (const BucketEntry &bucketEntry: _entries) {
        const DocEntry &entry(*bucketEntry.entry);
        const GlobalId &gid(bucketEntry.gid);

        GidMapType::const_iterator gidIt(_gidMap.find(gid));
        assert(gidIt != _gidMap.end());

        totalSize += entry.getSize();
        if (entry.isRemove()) {
            continue;
        }
        // Only include if we're newest entry for the particular GID
        if (gidIt->second.get() != &entry) {
            continue;
        }
        ++unique;
        uniqueSize += entry.getSize();

        checksum ^= computeEntryChecksum(bucketEntry);
    }
    if (!unique) {
        checksum = 0;
    } else if (checksum == 0) {
        checksum = 1;
    }

    _info = BucketInfo(BucketChecksum(checksum),
                       unique,
                       uniqueSize,
                       _entries.size(),
                       totalSize,
                       BucketInfo::READY,
                       _active ? BucketInfo::ACTIVE : BucketInfo::NOT_ACTIVE);

    _outdatedInfo = false;
    return _info;
}

namespace {

struct TimestampLess {
    bool operator()(const BucketEntry &bucketEntry, Timestamp t) {
        return bucketEntry.entry->getTimestamp() < t;
    }

    bool operator()(Timestamp t, const BucketEntry &bucketEntry) {
        return t < bucketEntry.entry->getTimestamp();
    }
};

}  // namespace

bool
BucketContent::hasTimestamp(Timestamp t) const {
    if (!_entries.empty() && _entries.back().entry->getTimestamp() < t) {
        return false;
    }
    return binary_search(_entries.begin(), _entries.end(), t, TimestampLess());
}

/**
 * GID map semantics:
 * The GID map always points to the newest entry for any given GID, no matter
 * its state (that is to say, the GID map will point at both puts and removes).
 *
 * When inserting any valid entry (i.e. not a duplicate), we check the map to
 * see if a mapping exists for this GID already. If it does not, we insert one
 * pointing to the newly inserted entry. If it does exist, we change the mapping
 * to point to the new entry if and only if the new entry has a newer timestamp.
 *
 * When reverting an entry, we must walk through the entries vector and look for
 * the newest entry that will be logically reverted to, then point the GID map
 * to this entry. If no such entry exists (i.e. reverting the only put for a
 * document), we can remove the mapping entirely.
 */

void
BucketContent::insert(DocEntry::SP e) {
    LOG(spam, "insert(%s)", e->toString().c_str());
    const DocumentId *docId(e->getDocumentId());
    assert(docId != 0);
    GlobalId gid(docId->getGlobalId());
    GidMapType::iterator gidIt(_gidMap.find(gid));

    if (!_entries.empty() &&
        _entries.back().entry->getTimestamp() < e->getTimestamp()) {
        _entries.push_back(BucketEntry(e, gid));
    } else {
        auto it = lower_bound(_entries.begin(), _entries.end(), e->getTimestamp(), TimestampLess());
        if (it != _entries.end()) {
            if (it->entry->getTimestamp() == e->getTimestamp()) {
                if (equal(*it->entry, *e)) {
                    LOG(debug, "Ignoring duplicate put entry %s", e->toString().c_str());
                    return;
                } else {
                    LOG(error, "Entry %s was already present. Was trying to insert %s.",
                        it->entry->toString().c_str(), e->toString().c_str());
                    LOG_ABORT("should not reach here");
                }
            }
        }
        _entries.insert(it, BucketEntry(e, gid));
    }

    // GID map points to newest entry for that particular GID
    if (gidIt != _gidMap.end()) {
        if (gidIt->second->getTimestamp() < e->getTimestamp()) {
            // TODO(vekterli): add support for cheap info updates for putting
            // newer versions of a document etc. by XORing away old checksum.
            gidIt->second = e;
        } else {
            LOG(spam, "Newly inserted entry %s was older than existing entry %s; not updating GID mapping",
                e->toString().c_str(), gidIt->second->toString().c_str());
        }
        _outdatedInfo = true;
    } else {
        _gidMap.insert(GidMapType::value_type(gid, e));
        // Since GID didn't exist before, it means we can do a running
        // update of the bucket info. Bucket checksum is XOR of all entry
        // checksums, which is commutative.
        // Only bother to update if we don't have to re-do it all afterwards
        // anyway.
        // Updating bucketinfo before we update entries since we assume rest
        // of function is nothrow.
        if (!_outdatedInfo) {
            if (!e->isRemove()) {
                _info = BucketInfo(updateRollingChecksum(
                                           computeEntryChecksum(BucketEntry(e, gid))),
                                   _info.getDocumentCount() + 1,
                                   _info.getDocumentSize() + e->getSize(),
                                   _info.getEntryCount() + 1,
                                   _info.getUsedSize() + e->getSize(),
                                   _info.getReady(),
                                   _info.getActive());
            } else {
                _info = BucketInfo(_info.getChecksum(),
                                   _info.getDocumentCount(),
                                   _info.getDocumentSize(),
                                   _info.getEntryCount() + 1,
                                   _info.getUsedSize() + e->getSize(),
                                   _info.getReady(),
                                   _info.getActive());
            }

            LOG(spam, "After cheap bucketinfo update, state is %s (inserted %s)",
                _info.toString().c_str(), e->toString().c_str());
        }
    }

    assert(_outdatedInfo || _info.getEntryCount() == _entries.size());
}

DocEntry::SP
BucketContent::getEntry(const DocumentId &did) const {
    auto it(_gidMap.find(did.getGlobalId()));
    if (it != _gidMap.end()) {
        return it->second;
    }
    return DocEntry::SP();
}

DocEntry::SP
BucketContent::getEntry(Timestamp t) const {
    auto iter = lower_bound(_entries.begin(), _entries.end(), t, TimestampLess());

    if (iter == _entries.end() || iter->entry->getTimestamp() != t) {
        return DocEntry::SP();
    } else {
        return iter->entry;
    }
}

void
BucketContent::eraseEntry(Timestamp t) {
    auto iter = lower_bound(_entries.begin(), _entries.end(), t, TimestampLess());

    if (iter != _entries.end() && iter->entry->getTimestamp() == t) {
        assert(iter->entry->getDocumentId() != 0);
        GidMapType::iterator gidIt = _gidMap.find(iter->entry->getDocumentId()->getGlobalId());
        assert(gidIt != _gidMap.end());
        _entries.erase(iter);
        if (gidIt->second->getTimestamp() == t) {
            LOG(debug, "erasing timestamp %" PRIu64 " from GID map", t.getValue());
            // TODO(vekterli): O(1) bucket info update for this case
            // FIXME: is this correct? seems like it could cause wrong behavior!
            _gidMap.erase(gidIt);
        } // else: not erasing newest entry, cannot erase from GID map
        _outdatedInfo = true;
    }
}

DummyPersistence::DummyPersistence(const std::shared_ptr<const document::DocumentTypeRepo> &repo)
    : _initialized(false),
      _repo(repo),
      _content(),
      _nextIterator(1),
      _iterators(),
      _monitor(),
      _clusterState()
{}

DummyPersistence::~DummyPersistence() = default;

document::select::Node::UP
DummyPersistence::parseDocumentSelection(const string &documentSelection, bool allowLeaf) {
    document::select::Node::UP ret;
    try {
        document::select::Parser parser(*_repo, document::BucketIdFactory());
        ret = parser.parse(documentSelection);
    } catch (document::select::ParsingFailedException &e) {
        return document::select::Node::UP();
    }
    if (ret->isLeafNode() && !allowLeaf) {
        return document::select::Node::UP();
    }
    return ret;
}

Result
DummyPersistence::initialize() {
    assert(!_initialized);
    _initialized = true;
    return Result();
}

void
DummyPersistence::verifyInitialized() const noexcept {
    if (!_initialized) {
        LOG(error, "initialize() must always be called first in order to trigger lazy initialization.");
        abort();
    }
}

BucketIdListResult
DummyPersistence::listBuckets(BucketSpace bucketSpace) const
{
    verifyInitialized();
    LOG(debug, "listBuckets()");
    std::lock_guard lock(_monitor);
    BucketIdListResult::List list;
    if (bucketSpace == FixedBucketSpaces::default_space()) {
        for (const auto &entry : _content) {
            list.push_back(entry.first);
        }
    }
    return BucketIdListResult(std::move(list));
}

void
DummyPersistence::setModifiedBuckets(BucketIdListResult::List buckets)
{
    std::lock_guard lock(_monitor);
    _modifiedBuckets = std::move(buckets);
}

void DummyPersistence::set_fake_bucket_set(const std::vector<std::pair<Bucket, BucketInfo>>& fake_info) {
    std::lock_guard lock(_monitor);
    _content.clear();
    for (auto& info : fake_info) {
        const auto& bucket = info.first;
        // DummyPersistence currently only supports default bucket space
        assert(bucket.getBucketSpace() == FixedBucketSpaces::default_space());
        auto bucket_content = std::make_shared<BucketContent>();
        bucket_content->getMutableBucketInfo() = info.second;
        // Must tag as up to date, or bucket info will be recomputed implicitly from zero state in getBucketInfo
        bucket_content->setOutdatedInfo(false);
        _content[bucket] = std::move(bucket_content);
    }
}

BucketIdListResult
DummyPersistence::getModifiedBuckets(BucketSpace bucketSpace) const
{
    std::lock_guard lock(_monitor);
    if (bucketSpace == FixedBucketSpaces::default_space()) {
        return BucketIdListResult(std::move(_modifiedBuckets));
    } else {
        BucketIdListResult::List emptyList;
        return BucketIdListResult(BucketIdListResult::List());
    }
}

Result
DummyPersistence::setClusterState(BucketSpace bucketSpace, const ClusterState& c)
{
    std::lock_guard lock(_monitor);
    if (bucketSpace == FixedBucketSpaces::default_space()) {
        _clusterState.reset(new ClusterState(c));
        if (!_clusterState->nodeUp()) {
            for (const auto &entry : _content) {
                entry.second->setActive(false);
            }
        }
    }
    return Result();
}

void
DummyPersistence::setActiveStateAsync(const Bucket& b, BucketInfo::ActiveState newState, OperationComplete::UP onComplete)
{
    verifyInitialized();
    LOG(debug, "setCurrentState(%s, %s)",
        b.toString().c_str(),
        newState == BucketInfo::ACTIVE ? "ACTIVE" : "INACTIVE");
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());

    BucketContentGuard::UP bc(acquireBucketWithLock(b));
    if (!bc) {
        internal_create_bucket(b);
        bc = acquireBucketWithLock(b);
    }
    if ( ! bc ) {
        onComplete->onComplete(std::make_unique<BucketInfoResult>(Result::ErrorType::TRANSIENT_ERROR, "Bucket not found"));
    } else {
        (*bc)->setActive(newState == BucketInfo::ACTIVE);
        onComplete->onComplete(std::make_unique<Result>());
    }
}

BucketInfoResult
DummyPersistence::getBucketInfo(const Bucket& b) const
{
    verifyInitialized();
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    BucketContentGuard::UP bc(acquireBucketWithLock(b));
    if (!bc.get()) {
        LOG(debug, "getBucketInfo(%s) : (bucket not found)",
            b.toString().c_str());
        BucketInfo info(BucketChecksum(0), 0, 0, 0, 0);
        return BucketInfoResult(info);
    }

    BucketInfo info((*bc)->getBucketInfo());
    LOG(debug, "getBucketInfo(%s) -> %s",
        b.toString().c_str(),
        info.toString().c_str());
    return BucketInfoResult(info);
}

void
DummyPersistence::putAsync(const Bucket& b, Timestamp t, Document::SP doc, OperationComplete::UP onComplete)
{
    verifyInitialized();
    LOG(debug, "put(%s, %" PRIu64 ", %s)",
        b.toString().c_str(), uint64_t(t), doc->getId().toString().c_str());
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    BucketContentGuard::UP bc(acquireBucketWithLock(b));
    while (!bc) {
        internal_create_bucket(b);
        bc = acquireBucketWithLock(b);
    }
    DocEntry::SP existing = (*bc)->getEntry(t);
    if (existing) {
        bc.reset();
        if (doc->getId() == *existing->getDocumentId()) {
            onComplete->onComplete(std::make_unique<Result>());
        } else {
            onComplete->onComplete(std::make_unique<Result>(Result::ErrorType::TIMESTAMP_EXISTS,
                                                            "Timestamp already existed"));
        }
    } else {
        LOG(spam, "Inserting document %s", doc->toString(true).c_str());
        auto entry = DocEntry::create(t, Document::UP(doc->clone()));
        (*bc)->insert(std::move(entry));
        bc.reset();
        onComplete->onComplete(std::make_unique<Result>());
    }
}

void
DummyPersistence::updateAsync(const Bucket& bucket, Timestamp ts, DocumentUpdateSP upd, OperationComplete::UP onComplete)
{
    Context context(0x80, 0);
    GetResult getResult = get(bucket, document::AllFields(), upd->getId(), context);

    if (getResult.hasError()) {
        onComplete->onComplete(std::make_unique<UpdateResult>(getResult.getErrorCode(), getResult.getErrorMessage()));
        return;
    }
    auto docToUpdate = getResult.getDocumentPtr();
    Timestamp updatedTs = getResult.getTimestamp();
    if (!docToUpdate) {
        if (!upd->getCreateIfNonExistent()) {
            onComplete->onComplete(std::make_unique<UpdateResult>());
            return;
        } else {
            docToUpdate = std::make_shared<document::Document>(upd->getType(), upd->getId());
            updatedTs = ts;
        }
    }

    upd->applyTo(*docToUpdate);

    Result putResult = put(bucket, ts, std::move(docToUpdate));

    if (putResult.hasError()) {
        onComplete->onComplete(std::make_unique<UpdateResult>(putResult.getErrorCode(), putResult.getErrorMessage()));
    } else {
        onComplete->onComplete(std::make_unique<UpdateResult>(updatedTs));
    }
}

void
DummyPersistence::removeAsync(const Bucket& b, std::vector<spi::IdAndTimestamp> ids, OperationComplete::UP onComplete)
{
    verifyInitialized();
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    BucketContentGuard::UP bc(acquireBucketWithLock(b));

    uint32_t numRemoves(0);
    for (const spi::IdAndTimestamp & stampedId : ids) {
        const DocumentId & id = stampedId.id;
        Timestamp t = stampedId.timestamp;
        LOG(debug, "remove(%s, %" PRIu64 ", %s)", b.toString().c_str(), uint64_t(t), id.toString().c_str());

        while (!bc) {
            internal_create_bucket(b);
            bc = acquireBucketWithLock(b);
        }
        DocEntry::SP entry((*bc)->getEntry(id));
        if (!entry || entry->getTimestamp() <= t) {
            numRemoves += (entry && !entry->isRemove()) ? 1 : 0;
            auto remEntry = DocEntry::create(t, DocumentMetaEnum::REMOVE_ENTRY, id);

            if ((*bc)->hasTimestamp(t)) {
                (*bc)->eraseEntry(t);
            }
            (*bc)->insert(std::move(remEntry));
        } else {
            LOG(debug, "Not adding tombstone for %s at %" PRIu64 " since it has already "
                       "been succeeded by a newer write at timestamp %" PRIu64,
                id.toString().c_str(), t.getValue(), entry->getTimestamp().getValue());
        }
    }
    bc.reset();
    onComplete->onComplete(std::make_unique<RemoveResult>(numRemoves));
}

GetResult
DummyPersistence::get(const Bucket& b, const FieldSet& fieldSet, const DocumentId& did, Context&) const
{
    verifyInitialized();
    LOG(debug, "get(%s, %s)",
        b.toString().c_str(),
        did.toString().c_str());
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    BucketContentGuard::UP bc(acquireBucketWithLock(b, LockMode::Shared));
    if (!bc.get()) {
    } else {
        DocEntry::SP entry((*bc)->getEntry(did));
        if (!entry) {
            return GetResult();
        } else if (entry->isRemove()) {
            return GetResult::make_for_tombstone(entry->getTimestamp());
        } else {
            Document::UP doc(entry->getDocument()->clone());
            if (fieldSet.getType() != FieldSet::Type::ALL) {
                FieldSet::stripFields(*doc, fieldSet);
            }
            return GetResult(std::move(doc), entry->getTimestamp());
        }
    }

    return GetResult();
}

CreateIteratorResult
DummyPersistence::createIterator(const Bucket &b, FieldSetSP fs, const Selection &s, IncludedVersions v, Context &)
{
    verifyInitialized();
    LOG(debug, "createIterator(%s)", b.toString().c_str());
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    std::unique_ptr<document::select::Node> docSelection;
    if (!s.getDocumentSelection().getDocumentSelection().empty()) {
        docSelection = parseDocumentSelection(s.getDocumentSelection().getDocumentSelection(), true);
        if (!docSelection.get()) {
            return CreateIteratorResult(
                    Result::ErrorType::PERMANENT_ERROR,
                    "Got invalid/unparseable document selection string");
        }
    }
    BucketContentGuard::UP bc(acquireBucketWithLock(b, LockMode::Shared));

    Iterator* it;
    IteratorId id;
    {
        std::lock_guard lock(_monitor);
        id = _nextIterator;
        ++_nextIterator;
        assert(_iterators.find(id) == _iterators.end());
        it = new Iterator;
        _iterators[id] = Iterator::UP(it);
        assert(it->_bucket.getBucketId().getRawId() == 0); // Wrap detection
        it->_bucket = b;
    }
    // Memory pointed to by 'it' should now be valid from here on out

    if (!bc.get()) {
        // Bucket not found.
        return CreateIteratorResult(id);
    }
    it->_fieldSet = std::move(fs);
    const BucketContent::GidMapType& gidMap((*bc)->_gidMap);

    if (s.getTimestampSubset().empty()) {
        using reverse_iterator = std::vector<BucketEntry>::const_reverse_iterator;
        for (reverse_iterator entryIter((*bc)->_entries.rbegin()),
                 entryEnd((*bc)->_entries.rend());
             entryIter != entryEnd; ++entryIter)
        {
            const BucketEntry& bucketEntry(*entryIter);
            const DocEntry& entry(*bucketEntry.entry);
            if (entry.getTimestamp() < s.getFromTimestamp() ||
                entry.getTimestamp() > s.getToTimestamp()) {
                continue;
            }
            BucketContent::GidMapType::const_iterator gidIt(gidMap.find(bucketEntry.gid));
            assert(gidIt != gidMap.end());

            if (entry.isRemove()) {
                if (v == NEWEST_DOCUMENT_ONLY) {
                    continue;
                }
                if (docSelection.get()
                    && (docSelection->contains(*entry.getDocumentId())
                        != document::select::Result::True))
                {
                    continue;
                }
                it->_leftToIterate.push_back(entry.getTimestamp());
            } else {
                if (v != ALL_VERSIONS && gidIt->second.get() != &entry) {
                    // Not newest version of document; skip it. Commonly, the
                    // document may have been removed, meaning the GID map entry
                    // points to a remove instead.
                    continue;
                }
                if (docSelection.get()
                    && (docSelection->contains(*entry.getDocument())
                        != document::select::Result::True))
                {
                    continue;
                }
                it->_leftToIterate.push_back(entry.getTimestamp());
            }
        }
    } else {
        it->_leftToIterate = s.getTimestampSubset();
    }
    return CreateIteratorResult(id);
}

IterateResult
DummyPersistence::iterate(IteratorId id, uint64_t maxByteSize) const
{
    verifyInitialized();
    LOG(debug, "iterate(%" PRIu64 ", %" PRIu64 ")", uint64_t(id), maxByteSize);
    Iterator* it;
    {
        std::lock_guard lock(_monitor);
        std::map<IteratorId, Iterator::UP>::iterator iter(_iterators.find(id));
        if (iter == _iterators.end()) {
            return IterateResult(Result::ErrorType::PERMANENT_ERROR,
                        "Bug! Used iterate without sending createIterator first");
        }
        it = iter->second.get();
    }

    BucketContentGuard::UP bc(acquireBucketWithLock(it->_bucket, LockMode::Shared));
    if (!bc.get()) {
        return IterateResult(std::vector<DocEntry::UP>(), true);
    }
    LOG(debug, "Iterator %" PRIu64 " acquired bucket lock", uint64_t(id));

    std::vector<DocEntry::UP> entries;
    uint32_t currentSize = 0;
    uint32_t fastPath = 0;
    while (!it->_leftToIterate.empty()) {
        Timestamp next(it->_leftToIterate.back());
        DocEntry::SP entry((*bc)->getEntry(next));
        if (entry.get() != 0) {
            uint32_t size = entry->getSize();
            if (currentSize != 0 && currentSize + size > maxByteSize) break;
            currentSize += size;
            if (!entry->isRemove()
                && it->_fieldSet->getType() != FieldSet::Type::ALL)
            {
                assert(entry->getDocument());
                // Create new document with only wanted fields.
                Document::UP filtered(FieldSet::createDocumentSubsetCopy(*entry->getDocument(), *it->_fieldSet));
                auto ret = DocEntry::create(entry->getTimestamp(), std::move(filtered), entry->getSize());
                entries.push_back(std::move(ret));
            } else {
                // Use entry as-is.
                entries.push_back(cloneDocEntry(*entry));
                ++fastPath;
            }
        }
        it->_leftToIterate.pop_back();
    }

    LOG(debug, "finished iterate(%" PRIu64 ", %" PRIu64 "), returning %zu documents "
        "with %u bytes of data. %u docs cloned in fast path",
        uint64_t(id),
        maxByteSize,
        entries.size(),
        currentSize,
        fastPath);
    if (it->_leftToIterate.empty()) {
        return IterateResult(std::move(entries), true);
    }

    return IterateResult(std::move(entries), false);
}

Result
DummyPersistence::destroyIterator(IteratorId id)
{
    verifyInitialized();
    LOG(debug, "destroyIterator(%" PRIu64 ")", uint64_t(id));
    std::lock_guard lock(_monitor);
    if (_iterators.find(id) != _iterators.end()) {
        _iterators.erase(id);
    }
    return Result();
}

void
DummyPersistence::createBucketAsync(const Bucket& b, OperationComplete::UP onComplete) noexcept
{
    verifyInitialized();
    LOG(debug, "createBucket(%s)", b.toString().c_str());
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    std::lock_guard lock(_monitor);
    if (find(b) == _content.end()) {
        _content[b] = std::make_shared<BucketContent>();
    } else {
        assert(!_content[b]->_inUse);
        LOG(debug, "%s already existed", b.toString().c_str());
    }
    onComplete->onComplete(std::make_unique<Result>());
}

void
DummyPersistence::deleteBucketAsync(const Bucket& b, OperationComplete::UP onComplete) noexcept
{
    verifyInitialized();
    LOG(debug, "deleteBucket(%s)", b.toString().c_str());
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    std::lock_guard lock(_monitor);
    if (_content[b].get()) {
        assert(!_content[b]->_inUse);
    }
    _content.erase(b);
    onComplete->onComplete(std::make_unique<Result>());
}

Result
DummyPersistence::split(const Bucket& source, const Bucket& target1, const Bucket& target2)
{
    verifyInitialized();
    LOG(debug, "split(%s -> %s, %s)",
        source.toString().c_str(),
        target1.toString().c_str(),
        target2.toString().c_str());
    assert(source.getBucketSpace() == FixedBucketSpaces::default_space());
    assert(target1.getBucketSpace() == FixedBucketSpaces::default_space());
    assert(target2.getBucketSpace() == FixedBucketSpaces::default_space());
    createBucket(source);
    createBucket(target1);
    createBucket(target2);

    BucketContentGuard::UP sourceGuard(acquireBucketWithLock(source));
    if (!sourceGuard.get()) {
        LOG(debug, "%s not found", source.toString().c_str());
        return Result(Result::ErrorType::TRANSIENT_ERROR, "Bucket not found");
    }
    BucketContentGuard::UP target1Guard(acquireBucketWithLock(target1));
    BucketContentGuard::UP target2Guard(acquireBucketWithLock(target2));
    assert(target1Guard.get());
    assert(target2Guard.get());

    BucketContent& sour(**sourceGuard);
    BucketContent& targ1(**target1Guard);
    BucketContent& targ2(**target2Guard);

    document::BucketIdFactory idFactory;

    // Add entries
    for (uint32_t i=0; i<sour._entries.size(); ++i) {
        DocEntry::SP entry(sour._entries[i].entry);

        document::BucketId bId(
                target1.getBucketId().getUsedBits(),
                idFactory.getBucketId(*entry->getDocumentId()).getRawId());

        if (bId == target1.getBucketId()) {
            targ1.insert(std::move(entry));
        } else {
            targ2.insert(std::move(entry));
        }
    }
    targ1.setActive(sour.isActive());
    targ2.setActive(sour.isActive());
    sourceGuard.reset(0);
    LOG(debug, "erasing split source %s",
        source.toString().c_str());
    deleteBucket(source);

    return Result();
}

Result
DummyPersistence::join(const Bucket& source1, const Bucket& source2, const Bucket& target)
{
    verifyInitialized();
    LOG(debug, "join(%s, %s -> %s)",
        source1.toString().c_str(),
        source2.toString().c_str(),
        target.toString().c_str());
    assert(source1.getBucketSpace() == FixedBucketSpaces::default_space());
    assert(source2.getBucketSpace() == FixedBucketSpaces::default_space());
    assert(target.getBucketSpace() == FixedBucketSpaces::default_space());
    createBucket(target);
    BucketContentGuard::UP targetGuard(acquireBucketWithLock(target));
    assert(targetGuard.get());

    bool active = false;
    for (uint32_t j=0; j<2; ++j) {
        Bucket source(j == 0 ? source1 : source2);
        BucketContentGuard::UP sourceGuard(acquireBucketWithLock(source));

        if (!sourceGuard.get()) {
            continue;
        }
        BucketContent& sour(**sourceGuard);
        active |= sour.isActive();

        for (uint32_t i=0; i<sour._entries.size(); ++i) {
            DocEntry::SP entry(sour._entries[i].entry);
            (*targetGuard)->insert(std::move(entry));
        }
        sourceGuard.reset(0);
        deleteBucket(source);
    }
    (*targetGuard)->setActive(active);

    return Result();
}

std::unique_ptr<vespalib::IDestructorCallback>
DummyPersistence::register_resource_usage_listener(IResourceUsageListener &listener)
{
    ResourceUsage usage(0.5, 0.4);
    listener.update_resource_usage(usage);
    return {};
}

namespace {

class ExecutorRegistration : public vespalib::IDestructorCallback {
public:
    explicit ExecutorRegistration(std::shared_ptr<BucketExecutor> executor) : _executor(std::move(executor)) { }
    ~ExecutorRegistration() override = default;
private:
    std::shared_ptr<BucketExecutor> _executor;
};

}

std::unique_ptr<vespalib::IDestructorCallback>
DummyPersistence::register_executor(std::shared_ptr<BucketExecutor> executor)
{
    assert(_bucket_executor.expired());
    _bucket_executor = executor;
    return std::make_unique<ExecutorRegistration>(executor);
}

std::string
DummyPersistence::dumpBucket(const Bucket& b) const
{
    verifyInitialized();
    LOG(spam, "dumpBucket(%s)", b.toString().c_str());
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    std::lock_guard lock(_monitor);
    auto it = find(b);
    return (it != _content.end()) ? asString(it->second->_entries) : "DOESN'T EXIST";
}

bool
DummyPersistence::isActive(const Bucket& b) const
{
    verifyInitialized();
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    std::lock_guard lock(_monitor);
    LOG(spam, "isActive(%s)", b.toString().c_str());
    auto it(find(b));
    if (it == _content.end()) {
        return false;
    }
    return it->second->isActive();
}

BucketContentGuard::~BucketContentGuard()
{
    _persistence.releaseBucketNoLock(_content, _lock_mode);
}

BucketContentGuard::UP
DummyPersistence::acquireBucketWithLock(const Bucket& b, LockMode lock_mode) const
{
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    std::lock_guard lock(_monitor);
    DummyPersistence& ncp(const_cast<DummyPersistence&>(*this));
    auto it(ncp.find(b));
    if (it == ncp._content.end()) {
        return BucketContentGuard::UP();
    }
    if (lock_mode == LockMode::Exclusive) {
        // Sanity check that SPI-level locking is doing its job correctly.
        // Atomic CAS might be a bit overkill, but since we "release" the bucket
        // outside of the mutex, we want to ensure the write is visible across all
        // threads.
        bool my_false(false);
        bool bucketNotInUse(it->second->_inUse.compare_exchange_strong(my_false, true));
        if (!bucketNotInUse) {
            LOG(error, "Attempted to acquire %s, but it was already marked as being in use!",
                b.toString().c_str());
            LOG_ABORT("dummy persistence bucket locking invariant violation");
        }
    }

    return std::make_unique<BucketContentGuard>(ncp, *it->second, lock_mode);
}

void
DummyPersistence::releaseBucketNoLock(const BucketContent& bc, LockMode lock_mode) const noexcept
{
    if (lock_mode == LockMode::Exclusive) {
        bool my_true(true);
        bool bucketInUse(bc._inUse.compare_exchange_strong(my_true, false));
        assert(bucketInUse);
        (void) bucketInUse;
    }
}

void
DummyPersistence::internal_create_bucket(const Bucket& b)
{
    std::lock_guard lock(_monitor);
    if (find(b) == _content.end()) {
        _content[b] = std::make_shared<BucketContent>();
    }
}

DummyPersistence::Content::const_iterator
DummyPersistence::find(const Bucket & bucket) const {
    return _content.find(bucket);
}

}

VESPALIB_HASH_MAP_INSTANTIATE_H(storage::spi::Bucket, std::shared_ptr<storage::spi::dummy::BucketContent>, document::BucketId::hash)
