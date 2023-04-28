// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reference_attribute.h"
#include "attributesaver.h"
#include "load_utils.h"
#include "readerbase.h"
#include "reference_attribute_saver.h"
#include "search_context.h"
#include <vespa/document/base/documentid.h>
#include <vespa/document/base/idstringexception.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper_factory.h>
#include <vespa/searchlib/query/query_term_simple.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/datastore/unique_store_builder.h>
#include <vespa/vespalib/datastore/datastore.hpp>
#include <vespa/vespalib/datastore/unique_store.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.reference_attribute");

namespace search::attribute {

using document::DocumentId;
using document::GlobalId;
using document::IdParseException;
using vespalib::datastore::CompactionSpec;

namespace {

const vespalib::string uniqueValueCountTag = "uniqueValueCount";

uint64_t
extractUniqueValueCount(const vespalib::GenericHeader &header)
{
    return (header.hasTag(uniqueValueCountTag)) ? header.getTag(uniqueValueCountTag).asInteger() : 0u;
}

}

ReferenceAttribute::ReferenceAttribute(const vespalib::stringref baseFileName)
    : ReferenceAttribute(baseFileName, Config(BasicType::REFERENCE))
{}

ReferenceAttribute::ReferenceAttribute(const vespalib::stringref baseFileName, const Config & cfg)
    : NotImplementedAttribute(baseFileName, cfg),
      _store(get_memory_allocator()),
      _indices(cfg.getGrowStrategy(), getGenerationHolder(), get_initial_alloc()),
      _compaction_spec(),
      _gidToLidMapperFactory(),
      _referenceMappings(getGenerationHolder(), getCommittedDocIdLimitRef(), get_initial_alloc())
{
    setEnum(true);
}

ReferenceAttribute::~ReferenceAttribute()
{
    _referenceMappings.clearBuilder();
    incGeneration(); // Force freeze
    const auto &store = _store;
    const auto enumerator = _store.getEnumerator(true);
    enumerator.foreach_key([&store,this](const AtomicEntryRef& ref)
                      {   const Reference &entry = store.get(ref.load_relaxed());
                          _referenceMappings.clearMapping(entry);
                      });
    incGeneration(); // Force freeze
}

void
ReferenceAttribute::onAddDocs(DocId limit)
{
    _indices.reserve(limit);
    _referenceMappings.onAddDocs(limit);
}

bool
ReferenceAttribute::addDoc(DocId &doc)
{
    bool incGen = _indices.isFull();
    doc = _indices.size();
    _indices.push_back(AtomicEntryRef());
    _referenceMappings.addDoc();
    incNumDocs();
    updateUncommittedDocIdLimit(doc);
    if (incGen) {
        incGeneration();
    } else {
        reclaim_unused_memory();
    }
    return true;
}

void
ReferenceAttribute::removeReverseMapping(EntryRef oldRef, uint32_t lid)
{
    const auto &entry = _store.get(oldRef);
    _referenceMappings.removeReverseMapping(entry, lid);
}

void
ReferenceAttribute::addReverseMapping(EntryRef newRef, uint32_t lid)
{
    const auto &entry = _store.get(newRef);
    _referenceMappings.addReverseMapping(entry, lid);
}

void
ReferenceAttribute::buildReverseMapping(EntryRef newRef, const std::vector<ReverseMapping::KeyDataType> &adds)
{
    const auto &entry = _store.get(newRef);
    _referenceMappings.buildReverseMapping(entry, adds);
}

void
ReferenceAttribute::buildReverseMapping()
{
    using EntryPair = std::pair<EntryRef, uint32_t>;
    std::vector<EntryPair, vespalib::allocator_large<EntryPair>> indices;
    uint32_t numDocs = _indices.size();
    indices.reserve(numDocs);
    for (uint32_t lid = 0; lid < numDocs; ++lid) {
        EntryRef ref = _indices[lid].load_relaxed();
        if (ref.valid()) {
            indices.emplace_back(ref, lid);
        }
    }
    std::sort(indices.begin(), indices.end());
    EntryRef prevRef;
    std::vector<ReverseMapping::KeyDataType> adds;
    for (const auto & elem : indices) {
        if (elem.first != prevRef) {
            if (prevRef.valid()) {
                buildReverseMapping(prevRef, adds);
                adds.clear();
            }
            prevRef = elem.first;
        }
        adds.emplace_back(elem.second, vespalib::btree::BTreeNoLeafData());
    }
    if (prevRef.valid()) {
        buildReverseMapping(prevRef, adds);
    }
}

uint32_t
ReferenceAttribute::clearDoc(DocId doc)
{
    updateUncommittedDocIdLimit(doc);
    assert(doc < _indices.size());
    EntryRef oldRef = _indices[doc].load_relaxed();
    if (oldRef.valid()) {
        removeReverseMapping(oldRef, doc);
        _indices[doc].store_release(EntryRef());
        _store.remove(oldRef);
        return 1u;
    } else {
        return 0u;
    }
}

void
ReferenceAttribute::reclaim_memory(generation_t oldest_used_gen)
{
    _referenceMappings.reclaim_memory(oldest_used_gen);
    _store.reclaim_memory(oldest_used_gen);
    getGenerationHolder().reclaim(oldest_used_gen);
}

void
ReferenceAttribute::before_inc_generation(generation_t current_gen)
{
    _referenceMappings.freeze();
    _store.freeze();
    _referenceMappings.assign_generation(current_gen);
    _store.assign_generation(current_gen);
    getGenerationHolder().assign_generation(current_gen);
}

void
ReferenceAttribute::onCommit()
{
    // Note: Cost can be reduced if unneeded generation increments are dropped
    incGeneration();
    if (consider_compact_values(getConfig().getCompactionStrategy())) {
        incGeneration();
        updateStat(true);
    }
    if (consider_compact_dictionary(getConfig().getCompactionStrategy())) {
        incGeneration();
        updateStat(true);
    }
}

void
ReferenceAttribute::onUpdateStat()
{
    auto& compaction_strategy = getConfig().getCompactionStrategy();
    vespalib::MemoryUsage total = _store.get_values_memory_usage();
    auto& dictionary = _store.get_dictionary();
    auto dictionary_memory_usage = dictionary.get_memory_usage();
    _compaction_spec = ReferenceAttributeCompactionSpec(compaction_strategy.should_compact_memory(total),
                                                        compaction_strategy.should_compact_memory(dictionary_memory_usage));
    total.merge(dictionary_memory_usage);
    total.mergeGenerationHeldBytes(getGenerationHolder().get_held_bytes());
    total.merge(_indices.getMemoryUsage());
    total.merge(_referenceMappings.getMemoryUsage());
    updateStatistics(getTotalValueCount(), getUniqueValueCount(),
                     total.allocatedBytes(),
                     total.usedBytes(), total.deadBytes(), total.allocatedBytesOnHold());
}

std::unique_ptr<AttributeSaver>
ReferenceAttribute::onInitSave(vespalib::stringref fileName)
{
    vespalib::GenerationHandler::Guard guard(this->getGenerationHandler().takeGuard());
    return std::make_unique<ReferenceAttributeSaver>
        (std::move(guard),
         createAttributeHeader(fileName),
         make_entry_ref_vector_snapshot(_indices, getCommittedDocIdLimit()),
         _store);
}

bool
ReferenceAttribute::onLoad(vespalib::Executor *)
{
    ReaderBase attrReader(*this);
    bool ok(attrReader.getHasLoadData());
    if (!ok) {
        return false;
    }
    setCreateSerialNum(attrReader.getCreateSerialNum());
    assert(attrReader.getEnumerated());
    assert(!attrReader.hasIdx());
    size_t numDocs(0);
    uint64_t numValues = attrReader.getEnumCount();
    numDocs = numValues;
    auto udatBuffer = attribute::LoadUtils::loadUDAT(*this);
    const GenericHeader &header = udatBuffer->getHeader();
    uint32_t uniqueValueCount = extractUniqueValueCount(header);
    assert(uniqueValueCount * sizeof(GlobalId) == udatBuffer->size());
    vespalib::ConstArrayRef<GlobalId> uniques(static_cast<const GlobalId *>(udatBuffer->buffer()), uniqueValueCount);

    auto builder = _store.getBuilder(uniqueValueCount);
    for (const auto &value : uniques) {
        builder.add(value);
    }
    builder.setupRefCounts();
    _referenceMappings.onLoad(numDocs);
    _indices.clear();
    _indices.unsafe_reserve(numDocs);
    for (uint32_t doc = 0; doc < numDocs; ++doc) {
        uint32_t enumValue = attrReader.getNextEnum();
        _indices.push_back(AtomicEntryRef(builder.mapEnumValueToEntryRef(enumValue)));
    }
    builder.makeDictionary();
    setNumDocs(numDocs);
    setCommittedDocIdLimit(numDocs);
    buildReverseMapping();
    incGeneration();
    return true;
}

void
ReferenceAttribute::update(DocId doc, const GlobalId &gid)
{
    updateUncommittedDocIdLimit(doc);
    assert(doc < _indices.size());
    EntryRef oldRef = _indices[doc].load_relaxed();
    Reference refToAdd(gid);
    EntryRef newRef = _store.add(refToAdd).ref();
    std::atomic_thread_fence(std::memory_order_release);
    _indices[doc].store_release(newRef);
    if (oldRef.valid()) {
        if (oldRef != newRef) {
            removeReverseMapping(oldRef, doc);
        }
        _store.remove(oldRef);
    }
    if (oldRef != newRef) {
        addReverseMapping(newRef, doc);
    }
}

const Reference *
ReferenceAttribute::getReference(DocId doc) const
{
    if (doc >= getCommittedDocIdLimit()) {
        return nullptr;
    }
    EntryRef ref = _indices.acquire_elem_ref(doc).load_acquire();
    if (!ref.valid()) {
        return nullptr;
    } else {
        return &_store.get(ref);
    }
}

bool
ReferenceAttribute::consider_compact_values(const CompactionStrategy &compactionStrategy)
{
    if (_compaction_spec.values()) {
        compact_worst_values(compactionStrategy);
        return true;
    }
    return false;
}

void
ReferenceAttribute::compact_worst_values(const CompactionStrategy& compaction_strategy)
{
    CompactionSpec compaction_spec(true, true);
    auto remapper(_store.compact_worst(compaction_spec, compaction_strategy));
    if (remapper) {
        remapper->remap(vespalib::ArrayRef<AtomicEntryRef>(&_indices[0], _indices.size()));
        remapper->done();
    }
}

bool
ReferenceAttribute::consider_compact_dictionary(const CompactionStrategy &compaction_strategy)
{
    auto& dictionary = _store.get_dictionary();
    if (dictionary.has_held_buffers()) {
        return false;
    }
    if (_compaction_spec.dictionary()) {
        dictionary.compact_worst(true, true, compaction_strategy);
        return true;
    }
    return false;
}

uint64_t
ReferenceAttribute::getUniqueValueCount() const
{
    return _store.getNumUniques();
}

void
ReferenceAttribute::setGidToLidMapperFactory(std::shared_ptr<IGidToLidMapperFactory> gidToLidMapperFactory)
{
    _gidToLidMapperFactory = std::move(gidToLidMapperFactory);
}

void
ReferenceAttribute::notifyReferencedPutNoCommit(const GlobalId &gid, DocId targetLid)
{
    assert(targetLid != 0);
    EntryRef ref = _store.find(gid);
    if (!ref.valid() || _store.get(ref).lid() == 0) {
        Reference refToAdd(gid);
        ref = _store.add(refToAdd).ref();
    }
    const auto &entry = _store.get(ref);
    _referenceMappings.notifyReferencedPut(entry, targetLid);
}

void
ReferenceAttribute::notifyReferencedPut(const GlobalId &gid, DocId targetLid)
{
    notifyReferencedPutNoCommit(gid, targetLid);
    commit();
}

bool
ReferenceAttribute::notifyReferencedRemoveNoCommit(const GlobalId &gid)
{
    EntryRef ref = _store.find(gid);
    if (ref.valid()) {
        const auto &entry = _store.get(ref);
        uint32_t oldTargetLid = entry.lid();
        _referenceMappings.notifyReferencedRemove(entry);
        if (oldTargetLid != 0) {
            _store.remove(ref);
        }
        return true;
    }
    return false;
}

void
ReferenceAttribute::notifyReferencedRemove(const GlobalId &gid)
{
    if (notifyReferencedRemoveNoCommit(gid)) {
        commit();
    }
}

namespace {

class TargetLidPopulator : public IGidToLidMapperVisitor
{
    ReferenceAttribute &_attr;
public:
    explicit TargetLidPopulator(ReferenceAttribute &attr)
        : IGidToLidMapperVisitor(),
          _attr(attr)
    {
    }
    ~TargetLidPopulator() override = default;
    void visit(const document::GlobalId &gid, uint32_t lid) const override {
        _attr.notifyReferencedPutNoCommit(gid, lid);
    }
};

}

void
ReferenceAttribute::populateTargetLids(const std::vector<GlobalId>& removes)
{
    if (_gidToLidMapperFactory) {
        std::unique_ptr<IGidToLidMapper> mapperUP = _gidToLidMapperFactory->getMapper();
        const IGidToLidMapper &mapper = *mapperUP;
        TargetLidPopulator populator(*this);
        mapper.foreach(populator);
    }
    for (auto& remove : removes) {
        notifyReferencedRemoveNoCommit(remove);
    }
    commit();
}

void
ReferenceAttribute::clearDocs(DocId lidLow, DocId lidLimit, bool)
{
    assert(lidLow <= lidLimit);
    assert(lidLimit <= getNumDocs());
    for (DocId lid = lidLow; lid < lidLimit; ++lid) {
        EntryRef oldRef = _indices[lid].load_relaxed();
        if (oldRef.valid()) {
            removeReverseMapping(oldRef, lid);
            _indices[lid].store_release(EntryRef());
            _store.remove(oldRef);
        }
    }
}

void
ReferenceAttribute::onShrinkLidSpace()
{
    // References for lids > committedDocIdLimit have been cleared.
    uint32_t committedDocIdLimit = getCommittedDocIdLimit();
    assert(_indices.size() >= committedDocIdLimit);
    _indices.shrink(committedDocIdLimit);
    _referenceMappings.shrink(committedDocIdLimit);
    setNumDocs(committedDocIdLimit);
}

namespace {

class ReferenceSearchContext : public attribute::SearchContext {
private:
    const ReferenceAttribute& _ref_attr;
    GlobalId _term;
    uint32_t _docid_limit;

public:
    ReferenceSearchContext(const ReferenceAttribute& ref_attr, const GlobalId& term)
        : attribute::SearchContext(ref_attr),
          _ref_attr(ref_attr),
          _term(term),
          _docid_limit(ref_attr.getCommittedDocIdLimit())
    {
    }
    bool valid() const override {
        return _term != GlobalId();
    }
    int32_t onFind(DocId docId, int32_t elementId, int32_t& weight) const override {
        if (elementId != 0) {
            return -1;
        }
        auto* ref = _ref_attr.getReference(docId);
        if (ref == nullptr) {
            return -1;
        }
        weight = 1;
        return (_term == ref->gid()) ? 0 : -1;
    }
    int32_t onFind(DocId docId, int32_t elementId) const override {
        int32_t weight;
        return onFind(docId, elementId, weight);
    }
    uint32_t get_committed_docid_limit() const noexcept override;
};

uint32_t
ReferenceSearchContext::get_committed_docid_limit() const noexcept
{
    return _docid_limit;
}

}

std::unique_ptr<attribute::SearchContext>
ReferenceAttribute::getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams& params) const
{
    (void) params;
    GlobalId gid;
    try {
        DocumentId docId(term->getTerm());
        gid = docId.getGlobalId();
    } catch (const IdParseException&) {
        // The query term is not valid, which will result in an empty search iterator.
    }
    return std::make_unique<ReferenceSearchContext>(*this, gid);
}

}

namespace vespalib::datastore {

using Reference = search::attribute::Reference;

template class UniqueStoreAllocator<Reference, EntryRefT<22>>;
template class UniqueStore<Reference, EntryRefT<22>>;
template class UniqueStoreBuilder<UniqueStoreAllocator<Reference, EntryRefT<22>>>;
template class UniqueStoreEnumerator<EntryRefT<22>>;

}
