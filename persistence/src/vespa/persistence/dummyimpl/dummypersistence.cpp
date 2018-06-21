// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummypersistence.h"
#include <vespa/document/select/parser.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/vespalib/util/crc.h>
#include <vespa/document/fieldset/fieldsetrepo.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".dummypersistence");

using vespalib::make_string;
using std::binary_search;
using std::lower_bound;
using document::FixedBucketSpaces;

namespace storage::spi::dummy {

BucketContent::BucketContent()
    : _entries(),
      _gidMap(),
      _info(),
      _inUse(false),
      _outdatedInfo(true),
      _active(false)
{ }
BucketContent::~BucketContent() { }

uint32_t
BucketContent::computeEntryChecksum(const BucketEntry& e) const
{
    vespalib::crc_32_type checksummer;

    uint64_t ts(e.entry->getTimestamp());
    checksummer.process_bytes(&e.gid, sizeof(GlobalId));
    checksummer.process_bytes(&ts, sizeof(uint64_t));
    return checksummer.checksum();
}

BucketChecksum
BucketContent::updateRollingChecksum(uint32_t entryChecksum)
{
    uint32_t checksum = _info.getChecksum();
    checksum ^= entryChecksum;
    if (checksum == 0) {
        checksum = 1;
    }
    return BucketChecksum(checksum);
}

const BucketInfo&
BucketContent::getBucketInfo() const
{
    if (!_outdatedInfo) {
        return _info;
    }

    // Checksum should only depend on the newest entry for each document that
    // has not been removed.
    uint32_t unique = 0;
    uint32_t uniqueSize = 0;
    uint32_t totalSize = 0;
    uint32_t checksum = 0;

    for (std::vector<BucketEntry>::const_iterator
             it = _entries.begin(); it != _entries.end(); ++it)
    {
        const DocEntry& entry(*it->entry);
        const GlobalId& gid(it->gid);

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

        checksum ^= computeEntryChecksum(*it);
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
struct HasDocId {
    const DocumentId &_did;
    HasDocId(const DocumentId &did) : _did(did) {}
    bool operator()(const DocEntry &entry)
    { return *entry.getDocumentId() == _did; }
};

struct TimestampLess {
    bool operator()(const BucketEntry &bucketEntry, Timestamp t)
    { return bucketEntry.entry->getTimestamp() < t; }
    bool operator()(Timestamp t, const BucketEntry &bucketEntry)
    { return t < bucketEntry.entry->getTimestamp(); }
};

template <typename Iter>
typename std::iterator_traits<Iter>::value_type
dereferenceOrDefaultIfAtEnd(Iter it, Iter end) {
    if (it == end) {
        return typename std::iterator_traits<Iter>::value_type();
    }
    return *it;
}

}  // namespace

bool
BucketContent::hasTimestamp(Timestamp t) const
{
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
BucketContent::insert(DocEntry::SP e)
{
    LOG(spam, "insert(%s)", e->toString().c_str());
    const DocumentId* docId(e->getDocumentId());
    assert(docId != 0);
    GlobalId gid(docId->getGlobalId());
    GidMapType::iterator gidIt(_gidMap.find(gid));

    if (!_entries.empty() &&
        _entries.back().entry->getTimestamp() < e->getTimestamp()) {
        _entries.push_back(BucketEntry(e, gid));
    } else {
        std::vector<BucketEntry>::iterator it =
            lower_bound(_entries.begin(),
                        _entries.end(),
                        e->getTimestamp(),
                        TimestampLess());
        if (it != _entries.end()) {
            if (it->entry->getTimestamp() == e->getTimestamp()) {
                if (*it->entry.get() == *e) {
                    LOG(debug, "Ignoring duplicate put entry %s",
                        e->toString().c_str());
                    return;
                } else {
                    LOG(error, "Entry %s was already present."
                        "Was trying to insert %s.",
                        it->entry->toString().c_str(),
                        e->toString().c_str());
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
            LOG(spam,
                "Newly inserted entry %s was older than existing entry %s; "
                "not updating GID mapping",
                e->toString().c_str(),
                gidIt->second->toString().c_str());
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

            LOG(spam,
                "After cheap bucketinfo update, state is %s (inserted %s)",
                _info.toString().c_str(),
                e->toString().c_str());
        }
    }

    assert(_outdatedInfo || _info.getEntryCount() == _entries.size());
}

DocEntry::SP
BucketContent::getEntry(const DocumentId& did) const
{
    GidMapType::const_iterator it(_gidMap.find(did.getGlobalId()));
    if (it != _gidMap.end()) {
        return it->second;
    }
    return DocEntry::SP();
}

DocEntry::SP
BucketContent::getEntry(Timestamp t) const
{
    std::vector<BucketEntry>::const_iterator iter =
        lower_bound(_entries.begin(), _entries.end(), t, TimestampLess());

    if (iter == _entries.end() || iter->entry->getTimestamp() != t) {
        return DocEntry::SP();
    } else {
        return iter->entry;
    }
}

void
BucketContent::eraseEntry(Timestamp t)
{
    std::vector<BucketEntry>::iterator iter =
        lower_bound(_entries.begin(), _entries.end(), t, TimestampLess());

    if (iter != _entries.end() && iter->entry->getTimestamp() == t) {
        assert(iter->entry->getDocumentId() != 0);
        GidMapType::iterator gidIt(
                _gidMap.find(iter->entry->getDocumentId()->getGlobalId()));
        assert(gidIt != _gidMap.end());
        _entries.erase(iter);
        if (gidIt->second->getTimestamp() == t) {
            LOG(debug, "erasing timestamp %zu from GID map", t.getValue());
            // TODO(vekterli): O(1) bucket info update for this case
            // FIXME: is this correct? seems like it could cause wrong behavior!
            _gidMap.erase(gidIt);
        } // else: not erasing newest entry, cannot erase from GID map
        _outdatedInfo = true;
    }
}

DummyPersistence::DummyPersistence(
        const std::shared_ptr<const document::DocumentTypeRepo>& repo,
        uint16_t partitionCount)
    : _initialized(false),
      _repo(repo),
      _partitions(partitionCount),
      _content(partitionCount),
      _nextIterator(1),
      _iterators(),
      _monitor(),
      _clusterState(),
      _simulateMaintainFailure(false)
{}

DummyPersistence::~DummyPersistence() {}

document::select::Node::UP
DummyPersistence::parseDocumentSelection(const string& documentSelection,
                                         bool allowLeaf)
{
    document::select::Node::UP ret;
    try {
        document::select::Parser parser(
                *_repo, document::BucketIdFactory());
        ret = parser.parse(documentSelection);
    } catch (document::select::ParsingFailedException& e) {
        return document::select::Node::UP();
    }
    if (ret->isLeafNode() && !allowLeaf) {
        return document::select::Node::UP();
    }
    return ret;
}

PartitionStateListResult
DummyPersistence::getPartitionStates() const
{
    _initialized = true;
    LOG(debug, "getPartitionStates()");
    vespalib::MonitorGuard lock(_monitor);
    return PartitionStateListResult(_partitions);
}

#define DUMMYPERSISTENCE_VERIFY_INITIALIZED \
    if (!_initialized) throw vespalib::IllegalStateException( \
            "getPartitionStates() must always be called first in order to " \
            "trigger lazy initialization.", VESPA_STRLOC)


BucketIdListResult
DummyPersistence::listBuckets(BucketSpace bucketSpace, PartitionId id) const
{
    DUMMYPERSISTENCE_VERIFY_INITIALIZED;
    LOG(debug, "listBuckets(%u)", uint16_t(id));
    vespalib::MonitorGuard lock(_monitor);
    BucketIdListResult::List list;
    if (bucketSpace == FixedBucketSpaces::default_space()) {
        for (PartitionContent::const_iterator it = _content[id].begin();
             it != _content[id].end(); ++it)
        {
            list.push_back(it->first);
        }
    }
    return BucketIdListResult(list);
}

void
DummyPersistence::setModifiedBuckets(const BucketIdListResult::List& buckets)
{
    vespalib::MonitorGuard lock(_monitor);
    _modifiedBuckets = buckets;
}

BucketIdListResult
DummyPersistence::getModifiedBuckets(BucketSpace bucketSpace) const
{
    vespalib::MonitorGuard lock(_monitor);
    if (bucketSpace == FixedBucketSpaces::default_space()) {
        return BucketIdListResult(_modifiedBuckets);
    } else {
        BucketIdListResult::List emptyList;
        return BucketIdListResult(emptyList);
    }
}

Result
DummyPersistence::setClusterState(BucketSpace bucketSpace, const ClusterState& c)
{
    vespalib::MonitorGuard lock(_monitor);
    if (bucketSpace == FixedBucketSpaces::default_space()) {
        _clusterState.reset(new ClusterState(c));
        if (!_clusterState->nodeUp()) {
            for (uint32_t i=0, n=_content.size(); i<n; ++i) {
                for (PartitionContent::iterator it = _content[i].begin();
                     it != _content[i].end(); ++it)
                {
                    it->second->setActive(false);
                }
            }
        }
    }
    return Result();
}

Result
DummyPersistence::setActiveState(const Bucket& b,
                                 BucketInfo::ActiveState newState)
{
    DUMMYPERSISTENCE_VERIFY_INITIALIZED;
    LOG(debug, "setCurrentState(%s, %s)",
        b.toString().c_str(),
        newState == BucketInfo::ACTIVE ? "ACTIVE" : "INACTIVE");
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());

    BucketContentGuard::UP bc(acquireBucketWithLock(b));
    if (!bc.get()) {
        return BucketInfoResult(Result::TRANSIENT_ERROR, "Bucket not found");
    }
    (*bc)->setActive(newState == BucketInfo::ACTIVE);
    return Result();
}

BucketInfoResult
DummyPersistence::getBucketInfo(const Bucket& b) const
{
    DUMMYPERSISTENCE_VERIFY_INITIALIZED;
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    BucketContentGuard::UP bc(acquireBucketWithLock(b));
    if (!bc.get()) {
        LOG(debug, "getBucketInfo(%s) : (bucket not found)",
            b.toString().c_str());
        return BucketInfoResult(Result::TRANSIENT_ERROR, "Bucket not found");
    }

    BucketInfo info((*bc)->getBucketInfo());
    LOG(debug, "getBucketInfo(%s) -> %s",
        b.toString().c_str(),
        info.toString().c_str());
    return BucketInfoResult(info);
}

Result
DummyPersistence::put(const Bucket& b, Timestamp t, const Document::SP& doc,
                      Context&)
{
    DUMMYPERSISTENCE_VERIFY_INITIALIZED;
    LOG(debug, "put(%s, %zu, %s)",
        b.toString().c_str(),
        uint64_t(t),
        doc->getId().toString().c_str());
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    BucketContentGuard::UP bc(acquireBucketWithLock(b));
    if (!bc.get()) {
        return BucketInfoResult(Result::TRANSIENT_ERROR, "Bucket not found");
    }

    DocEntry::SP existing = (*bc)->getEntry(t);
    if (existing.get()) {
        if (doc->getId() == *existing->getDocumentId()) {
            return Result();
        } else {
            return Result(Result::TIMESTAMP_EXISTS,
                          "Timestamp already existed");
        }
    }

    LOG(spam, "Inserting document %s", doc->toString(true).c_str());

    DocEntry::UP entry(new DocEntry(t, NONE, Document::UP(doc->clone())));
    (*bc)->insert(std::move(entry));
    return Result();
}

Result
DummyPersistence::maintain(const Bucket& b,
                           MaintenanceLevel)
{
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    if (_simulateMaintainFailure) {
        BucketContentGuard::UP bc(acquireBucketWithLock(b));
        if (!bc.get()) {
            return BucketInfoResult(Result::TRANSIENT_ERROR, "Bucket not found");
        }

        if (!(*bc)->_entries.empty()) {
            // Simulate a corruption in a document, remove it.
            (*bc)->_entries.pop_back();
        }
        (*bc)->setOutdatedInfo(true);
        _simulateMaintainFailure = false;
    }

    return Result();
}

RemoveResult
DummyPersistence::remove(const Bucket& b,
                         Timestamp t,
                         const DocumentId& did,
                         Context&)
{
    DUMMYPERSISTENCE_VERIFY_INITIALIZED;
    LOG(debug, "remove(%s, %zu, %s)",
        b.toString().c_str(),
        uint64_t(t),
        did.toString().c_str());
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());

    BucketContentGuard::UP bc(acquireBucketWithLock(b));
    if (!bc.get()) {
        return RemoveResult(Result::TRANSIENT_ERROR, "Bucket not found");
    }

    DocEntry::SP entry((*bc)->getEntry(did));
    bool foundPut(entry.get() && !entry->isRemove());
    DocEntry::UP remEntry(new DocEntry(t, REMOVE_ENTRY, did));

    if ((*bc)->hasTimestamp(t)) {
        (*bc)->eraseEntry(t);
    }
    (*bc)->insert(std::move(remEntry));
    return RemoveResult(foundPut);
}

GetResult
DummyPersistence::get(const Bucket& b,
                      const document::FieldSet& fieldSet,
                      const DocumentId& did,
                      Context&) const
{
    DUMMYPERSISTENCE_VERIFY_INITIALIZED;
    LOG(debug, "get(%s, %s)",
        b.toString().c_str(),
        did.toString().c_str());
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    BucketContentGuard::UP bc(acquireBucketWithLock(b));
    if (!bc.get()) {
    } else {
        DocEntry::SP entry((*bc)->getEntry(did));
        if (entry.get() == 0 || entry->isRemove()) {
        } else {
            Document::UP doc(entry->getDocument()->clone());
            if (fieldSet.getType() != document::FieldSet::ALL) {
                document::FieldSet::stripFields(*doc, fieldSet);
            }
            return GetResult(std::move(doc), entry->getTimestamp());
        }
    }

    return GetResult();
}

CreateIteratorResult
DummyPersistence::createIterator(
        const Bucket& b,
        const document::FieldSet& fs,
        const Selection& s,
        IncludedVersions v,
        Context&)
{
    DUMMYPERSISTENCE_VERIFY_INITIALIZED;
    LOG(debug, "createIterator(%s)", b.toString().c_str());
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    std::unique_ptr<document::select::Node> docSelection;
    if (!s.getDocumentSelection().getDocumentSelection().empty()) {
        docSelection.reset(
                parseDocumentSelection(
                        s.getDocumentSelection().getDocumentSelection(),
                        true).release());
        if (!docSelection.get()) {
            return CreateIteratorResult(
                    Result::PERMANENT_ERROR,
                    "Got invalid/unparseable document selection string");
        }
    }
    BucketContentGuard::UP bc(acquireBucketWithLock(b));
    if (!bc.get()) {
        return CreateIteratorResult(Result::TRANSIENT_ERROR, "Bucket not found");
    }

    Iterator* it;
    IteratorId id;
    {
        vespalib::MonitorGuard lock(_monitor);
        id = _nextIterator;
        ++_nextIterator;
        assert(_iterators.find(id) == _iterators.end());
        it = new Iterator;
        _iterators[id] = Iterator::UP(it);
        assert(it->_bucket.getBucketId().getRawId() == 0); // Wrap detection
        it->_bucket = b;
    }
    // Memory pointed to by 'it' should now be valid from here on out

    it->_fieldSet = std::unique_ptr<document::FieldSet>(fs.clone());
    const BucketContent::GidMapType& gidMap((*bc)->_gidMap);

    if (s.getTimestampSubset().empty()) {
        typedef std::vector<BucketEntry>::const_reverse_iterator reverse_iterator;
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
            BucketContent::GidMapType::const_iterator gidIt(
                    gidMap.find(bucketEntry.gid));
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
DummyPersistence::iterate(IteratorId id, uint64_t maxByteSize, Context& ctx) const
{
    DUMMYPERSISTENCE_VERIFY_INITIALIZED;
    LOG(debug, "iterate(%zu, %zu)", uint64_t(id), maxByteSize);
    ctx.trace(9, "started iterate()");
    Iterator* it;
    {
        vespalib::MonitorGuard lock(_monitor);
        std::map<IteratorId, Iterator::UP>::iterator iter(_iterators.find(id));
        if (iter == _iterators.end()) {
            return IterateResult(Result::PERMANENT_ERROR,
                        "Bug! Used iterate without sending createIterator first");
        }
        it = iter->second.get();
    }

    BucketContentGuard::UP bc(acquireBucketWithLock(it->_bucket));
    if (!bc.get()) {
        ctx.trace(9, "finished iterate(); bucket not found");
        return IterateResult(Result::TRANSIENT_ERROR, "Bucket not found");
    }
    LOG(debug, "Iterator %zu acquired bucket lock", uint64_t(id));

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
                && it->_fieldSet->getType() != document::FieldSet::ALL)
            {
                assert(entry->getDocument());
                // Create new document with only wanted fields.
                Document::UP filtered(
                        document::FieldSet::createDocumentSubsetCopy(
                                *entry->getDocument(),
                                *it->_fieldSet));
                DocEntry::UP ret(new DocEntry(entry->getTimestamp(),
                                              entry->getFlags(),
                                              std::move(filtered),
                                              entry->getPersistedDocumentSize()));
                entries.push_back(std::move(ret));
            } else {
                // Use entry as-is.
                entries.push_back(DocEntry::UP(entry->clone()));
                ++fastPath;
            }
        }
        it->_leftToIterate.pop_back();
    }
    if (ctx.shouldTrace(9)) {
        ctx.trace(9, make_string("finished iterate(), returning %zu documents with %u bytes of data",
                                 entries.size(), currentSize));
    }
    LOG(debug, "finished iterate(%zu, %zu), returning %zu documents "
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
DummyPersistence::destroyIterator(IteratorId id, Context&)
{
    DUMMYPERSISTENCE_VERIFY_INITIALIZED;
    LOG(debug, "destroyIterator(%zu)", uint64_t(id));
    vespalib::MonitorGuard lock(_monitor);
    if (_iterators.find(id) != _iterators.end()) {
        _iterators.erase(id);
    }
    return Result();
}

Result
DummyPersistence::createBucket(const Bucket& b, Context&)
{
    DUMMYPERSISTENCE_VERIFY_INITIALIZED;
    LOG(debug, "createBucket(%s)", b.toString().c_str());
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    vespalib::MonitorGuard lock(_monitor);
    if (_content[b.getPartition()].find(b) == _content[b.getPartition()].end()) {
        _content[b.getPartition()][b] = std::make_shared<BucketContent>();
    } else {
        assert(!_content[b.getPartition()][b]->_inUse);
        LOG(debug, "%s already existed", b.toString().c_str());
    }
    return Result();
}

Result
DummyPersistence::deleteBucket(const Bucket& b, Context&)
{
    DUMMYPERSISTENCE_VERIFY_INITIALIZED;
    LOG(debug, "deleteBucket(%s)", b.toString().c_str());
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    vespalib::MonitorGuard lock(_monitor);
    if (_content[b.getPartition()][b].get()) {
        assert(!_content[b.getPartition()][b]->_inUse);
    }
    _content[b.getPartition()].erase(b);
    return Result();
}

Result
DummyPersistence::split(const Bucket& source,
                        const Bucket& target1,
                        const Bucket& target2,
                        Context& context)
{
    DUMMYPERSISTENCE_VERIFY_INITIALIZED;
    LOG(debug, "split(%s -> %s, %s)",
        source.toString().c_str(),
        target1.toString().c_str(),
        target2.toString().c_str());
    assert(source.getBucketSpace() == FixedBucketSpaces::default_space());
    assert(target1.getBucketSpace() == FixedBucketSpaces::default_space());
    assert(target2.getBucketSpace() == FixedBucketSpaces::default_space());
    createBucket(source, context);
    createBucket(target1, context);
    createBucket(target2, context);

    BucketContentGuard::UP sourceGuard(acquireBucketWithLock(source));
    if (!sourceGuard.get()) {
        LOG(debug, "%s not found", source.toString().c_str());
        return Result(Result::TRANSIENT_ERROR, "Bucket not found");
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
    deleteBucket(source, context);

    return Result();
}

Result
DummyPersistence::join(const Bucket& source1, const Bucket& source2,
                       const Bucket& target, Context& context)
{
    DUMMYPERSISTENCE_VERIFY_INITIALIZED;
    LOG(debug, "join(%s, %s -> %s)",
        source1.toString().c_str(),
        source2.toString().c_str(),
        target.toString().c_str());
    assert(source1.getBucketSpace() == FixedBucketSpaces::default_space());
    assert(source2.getBucketSpace() == FixedBucketSpaces::default_space());
    assert(target.getBucketSpace() == FixedBucketSpaces::default_space());
    createBucket(target, context);
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
        deleteBucket(source, context);
    }
    (*targetGuard)->setActive(active);

    return Result();
}

Result
DummyPersistence::revert(const Bucket& b, Timestamp t, Context&)
{
    DUMMYPERSISTENCE_VERIFY_INITIALIZED;
    LOG(debug, "revert(%s, %zu)",
        b.toString().c_str(),
        uint64_t(t));
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());

    BucketContentGuard::UP bc(acquireBucketWithLock(b));
    if (!bc.get()) {
        return BucketInfoResult(Result::TRANSIENT_ERROR, "Bucket not found");
    }

    BucketContent& content(**bc);
    DocEntry::SP docEntry(content.getEntry(t));
    if (!docEntry.get()) {
        return Result();
    }

    GlobalId gid(docEntry->getDocumentId()->getGlobalId());
    BucketContent::GidMapType::iterator gidIt(content._gidMap.find(gid));
    assert(gidIt != content._gidMap.end());

    std::vector<BucketEntry> newEntries;
    newEntries.reserve(content._entries.size() - 1);
    Timestamp timestampToRestore(0);
    for (uint32_t i=0; i<content._entries.size(); ++i) {
        BucketEntry e(content._entries[i]);
        if (e.entry->getTimestamp() == t) continue;
        if (e.gid == gid
            && e.entry->getTimestamp() > timestampToRestore)
        {
            // Set GID map entry to newest non-reverted doc entry
            assert(e.entry.get() != gidIt->second.get());
            LOG(spam, "Remapping GID to point to %s",
                e.entry->toString().c_str());
            gidIt->second = e.entry;
            timestampToRestore = e.entry->getTimestamp();
        }
        newEntries.push_back(e);
    }
    if (timestampToRestore == 0) {
        LOG(spam, "Found no entry to revert to for %s; erasing from GID map",
            docEntry->toString().c_str());
        content._gidMap.erase(gidIt);
    }
    newEntries.swap(content._entries);
    content.setOutdatedInfo(true);

    return Result();
}

std::string
DummyPersistence::dumpBucket(const Bucket& b) const
{
    DUMMYPERSISTENCE_VERIFY_INITIALIZED;
    LOG(spam, "dumpBucket(%s)", b.toString().c_str());
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    vespalib::MonitorGuard lock(_monitor);
    PartitionContent::const_iterator it(_content[b.getPartition()].find(b));
    if (it == _content[b.getPartition()].end()) {
        return "DOESN'T EXIST";
    } else {
        vespalib::asciistream ost;
        for (uint32_t i=0; i<it->second->_entries.size(); ++i) {
            const DocEntry& entry(*it->second->_entries[i].entry);
            ost << entry.toString() << "\n";
        }

        return ost.str();
    }
}

bool
DummyPersistence::isActive(const Bucket& b) const
{
    DUMMYPERSISTENCE_VERIFY_INITIALIZED;
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    vespalib::MonitorGuard lock(_monitor);
    LOG(spam, "isActive(%s)", b.toString().c_str());
    PartitionContent::const_iterator it(_content[b.getPartition()].find(b));
    if (it == _content[b.getPartition()].end()) {
        return false;
    }
    return it->second->isActive();
}

BucketContentGuard::~BucketContentGuard()
{
    _persistence.releaseBucketNoLock(_content);
}

BucketContentGuard::UP
DummyPersistence::acquireBucketWithLock(const Bucket& b) const
{
    assert(b.getBucketSpace() == FixedBucketSpaces::default_space());
    vespalib::MonitorGuard lock(_monitor);
    DummyPersistence& ncp(const_cast<DummyPersistence&>(*this));
    PartitionContent::iterator it(ncp._content[b.getPartition()].find(b));
    if (it == ncp._content[b.getPartition()].end()) {
        return BucketContentGuard::UP();
    }
    // Sanity check that SPI-level locking is doing its job correctly.
    // Atomic CAS might be a bit overkill, but since we "release" the bucket
    // outside of the mutex, we want to ensure the write is visible across all
    // threads.
    bool my_false(false);
    bool bucketNotInUse(it->second->_inUse.compare_exchange_strong(my_false, true));
    if (!bucketNotInUse) {
        LOG(error, "Attempted to acquire %s, but it was already marked as being in use!",
            b.toString().c_str());
        LOG_ABORT("should not reach here");
    }

    return BucketContentGuard::UP(new BucketContentGuard(ncp, *it->second));
}

void
DummyPersistence::releaseBucketNoLock(const BucketContent& bc) const
{
    bool my_true(true);
    bool bucketInUse(bc._inUse.compare_exchange_strong(my_true, false));
    assert(bucketInUse);
    (void) bucketInUse;
}

}

VESPALIB_HASH_MAP_INSTANTIATE_H(storage::spi::Bucket, std::shared_ptr<storage::spi::dummy::BucketContent>, document::BucketId::hash)
