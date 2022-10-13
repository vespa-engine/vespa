// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "singleenumattribute.h"
#include "enumattribute.hpp"
#include "ipostinglistattributebase.h"
#include "singleenumattributesaver.h"
#include "load_utils.h"
#include "enum_store_loaders.h"
#include "valuemodifier.h"
#include <vespa/vespalib/datastore/unique_store_remapper.h>

namespace search {

template <typename B>
SingleValueEnumAttribute<B>::
SingleValueEnumAttribute(const vespalib::string &baseFileName,
                         const AttributeVector::Config &cfg)
    : B(baseFileName, cfg),
      SingleValueEnumAttributeBase(cfg, getGenerationHolder(), this->get_initial_alloc())
{
}

template <typename B>
SingleValueEnumAttribute<B>::~SingleValueEnumAttribute()
{
    getGenerationHolder().reclaim_all();
}

template <typename B>
bool
SingleValueEnumAttribute<B>::onAddDoc(DocId doc)
{
    if (doc < _enumIndices.capacity()) {
        _enumIndices.reserve(doc+1);
        return true;
    }
    return false;
}

template <typename B>
void
SingleValueEnumAttribute<B>::onAddDocs(DocId limit)
{
    _enumIndices.reserve(limit);
}

template <typename B>
bool
SingleValueEnumAttribute<B>::addDoc(DocId & doc)
{
    bool incGen = false;
    doc = SingleValueEnumAttributeBase::addDoc(incGen);
    if (doc > 0u) {
        // Make sure that a valid value(magic default) is referenced,
        // even between addDoc and commit().
        if (_enumIndices[0].load_relaxed().valid()) {
            _enumIndices[doc] = _enumIndices[0];
            this->_enumStore.inc_ref_count(_enumIndices[0].load_relaxed());
        }
    }
    this->incNumDocs();
    this->updateUncommittedDocIdLimit(doc);
    incGen |= onAddDoc(doc);
    if (incGen) {
        this->incGeneration();
    } else
        this->reclaim_unused_memory();
    return true;
}

template <typename B>
uint32_t
SingleValueEnumAttribute<B>::getValueCount(DocId doc) const
{
    if (doc >= this->getNumDocs()) {
        return 0;
    }
    return 1;
}

template <typename B>
void
SingleValueEnumAttribute<B>::onCommit()
{
    this->checkSetMaxValueCount(1);

    // update enum store
    auto updater = this->_enumStore.make_batch_updater();
    this->insertNewUniqueValues(updater);
    // apply updates
    applyValueChanges(updater);
    this->_changes.clear();
    updater.commit();
    freezeEnumDictionary();
    std::atomic_thread_fence(std::memory_order_release);
    this->reclaim_unused_memory();
    auto remapper = this->_enumStore.consider_compact_values(this->getConfig().getCompactionStrategy());
    if (remapper) {
        remap_enum_store_refs(*remapper, *this);
        remapper->done();
        remapper.reset();
        this->incGeneration();
        this->updateStat(true);
    }
    if (this->_enumStore.consider_compact_dictionary(this->getConfig().getCompactionStrategy())) {
        this->incGeneration();
        this->updateStat(true);
    }
    auto *pab = this->getIPostingListAttributeBase();
    if (pab != nullptr) {
        if (pab->consider_compact_worst_btree_nodes(this->getConfig().getCompactionStrategy())) {
            this->incGeneration();
            this->updateStat(true);
        }
        if (pab->consider_compact_worst_buffers(this->getConfig().getCompactionStrategy())) {
            this->incGeneration();
            this->updateStat(true);
        }
    }
}

template <typename B>
void
SingleValueEnumAttribute<B>::onUpdateStat()
{
    // update statistics
    vespalib::MemoryUsage total = _enumIndices.getMemoryUsage();
    auto& compaction_strategy = this->getConfig().getCompactionStrategy();
    total.mergeGenerationHeldBytes(getGenerationHolder().get_held_bytes());
    total.merge(this->_enumStore.update_stat(compaction_strategy));
    total.merge(this->getChangeVectorMemoryUsage());
    mergeMemoryStats(total);
    this->updateStatistics(_enumIndices.size(), this->_enumStore.get_num_uniques(), total.allocatedBytes(),
                     total.usedBytes(), total.deadBytes(), total.allocatedBytesOnHold());
}

template <typename B>
void
SingleValueEnumAttribute<B>::considerUpdateAttributeChange(const Change & c, EnumStoreBatchUpdater & inserter)
{
    EnumIndex idx;
    if (!this->_enumStore.find_index(c._data.raw(), idx)) {
        c.set_entry_ref(inserter.insert(c._data.raw()).ref());
    } else {
        c.set_entry_ref(idx.ref());
    }
    considerUpdateAttributeChange(c); // for numeric
}

template <typename B>
void
SingleValueEnumAttribute<B>::considerAttributeChange(const Change & c, EnumStoreBatchUpdater & inserter)
{
    if (c._type == ChangeBase::UPDATE) {
        considerUpdateAttributeChange(c, inserter);
    } else if (c._type >= ChangeBase::ADD && c._type <= ChangeBase::DIV) {
        considerArithmeticAttributeChange(c, inserter); // for numeric
    } else if (c._type == ChangeBase::CLEARDOC) {
        Change clearDoc(this->_defaultValue);
        clearDoc._doc = c._doc;
        considerUpdateAttributeChange(clearDoc, inserter);
    }
}

template <typename B>
void
SingleValueEnumAttribute<B>::applyUpdateValueChange(const Change& c, EnumStoreBatchUpdater& updater)
{
    EnumIndex oldIdx = _enumIndices[c._doc].load_relaxed();
    EnumIndex newIdx;
    if (c.has_entry_ref()) {
        newIdx = EnumIndex(vespalib::datastore::EntryRef(c.get_entry_ref()));
    } else {
        this->_enumStore.find_index(c._data.raw(), newIdx);
    }
    updateEnumRefCounts(c, newIdx, oldIdx, updater);
}

template <typename B>
void
SingleValueEnumAttribute<B>::applyValueChanges(EnumStoreBatchUpdater& updater)
{
    ValueModifier valueGuard(this->getValueModifier());
    // This avoids searching for the defaultValue in the enum store for each CLEARDOC in the change vector.
    this->cache_change_data_entry_ref(this->_defaultValue);
    for (const auto& change : this->_changes.getInsertOrder()) {
        if (change._type == ChangeBase::UPDATE) {
            applyUpdateValueChange(change, updater);
        } else if (change._type >= ChangeBase::ADD && change._type <= ChangeBase::DIV) {
            applyArithmeticValueChange(change, updater);
        } else if (change._type == ChangeBase::CLEARDOC) {
            Change clearDoc(this->_defaultValue);
            clearDoc._doc = change._doc;
            applyUpdateValueChange(clearDoc, updater);
        }
    }
    // We must clear the cached entry ref as the defaultValue might be located in another data buffer on later invocations.
    this->_defaultValue.clear_entry_ref();
}

template <typename B>
void
SingleValueEnumAttribute<B>::updateEnumRefCounts(const Change& c, EnumIndex newIdx, EnumIndex oldIdx,
                                                 EnumStoreBatchUpdater& updater)
{
    updater.inc_ref_count(newIdx);
    _enumIndices[c._doc].store_release(newIdx);
    if (oldIdx.valid()) {
        updater.dec_ref_count(oldIdx);
    }
}

template <typename B>
void
SingleValueEnumAttribute<B>::fillValues(LoadedVector & loaded)
{
    if constexpr (!std::is_same_v<LoadedVector, NoLoadedVector>) {
        uint32_t numDocs = this->getNumDocs();
        getGenerationHolder().reclaim_all();
        _enumIndices.reset();
        _enumIndices.unsafe_reserve(numDocs);
        for (DocId doc = 0; doc < numDocs; ++doc, loaded.next()) {
            _enumIndices.push_back(AtomicEntryRef(loaded.read().getEidx()));
        }
    }
}

template <typename B>
void
SingleValueEnumAttribute<B>::load_enumerated_data(ReaderBase& attrReader,
                                                  enumstore::EnumeratedPostingsLoader& loader,
                                                  size_t num_values)
{
    loader.reserve_loaded_enums(num_values);
    attribute::loadFromEnumeratedSingleValue(_enumIndices,
                                             getGenerationHolder(),
                                             attrReader,
                                             loader.get_enum_indexes(),
                                             loader.get_enum_value_remapping(),
                                             attribute::SaveLoadedEnum(loader.get_loaded_enums()));
    loader.free_enum_value_remapping();
    loader.sort_loaded_enums();
}
    
template <typename B>
void
SingleValueEnumAttribute<B>::load_enumerated_data(ReaderBase& attrReader,
                                                  enumstore::EnumeratedLoader& loader)
{
    loader.allocate_enums_histogram();
    attribute::loadFromEnumeratedSingleValue(_enumIndices,
                                             getGenerationHolder(),
                                             attrReader,
                                             loader.get_enum_indexes(),
                                             loader.get_enum_value_remapping(),
                                             attribute::SaveEnumHist(loader.get_enums_histogram()));
    loader.free_enum_value_remapping();
    loader.set_ref_counts();
    loader.build_dictionary();
    loader.free_unused_values();
}

template <typename B>
void
SingleValueEnumAttribute<B>::reclaim_memory(generation_t oldest_used_gen)
{
    this->_enumStore.reclaim_memory(oldest_used_gen);
    getGenerationHolder().reclaim(oldest_used_gen);
}

template <typename B>
void
SingleValueEnumAttribute<B>::before_inc_generation(generation_t current_gen)
{
    /*
     * Freeze tree before generation is increased in attribute vector
     * but after generation is increased in tree. This ensures that
     * unlocked readers accessing a frozen tree will access a
     * sufficiently new frozen tree.
     */
    freezeEnumDictionary();
    getGenerationHolder().assign_generation(current_gen);
    this->_enumStore.assign_generation(current_gen);
}


template <typename B>
void
SingleValueEnumAttribute<B>::clearDocs(DocId lidLow, DocId lidLimit, bool)
{
    EnumHandle e(0);
    bool findDefaultEnumRes(this->findEnum(this->getDefaultEnumTypeValue(), e));
    if (!findDefaultEnumRes) {
        e = EnumHandle();
    }
    assert(lidLow <= lidLimit);
    assert(lidLimit <= this->getNumDocs());
    for (DocId lid = lidLow; lid < lidLimit; ++lid) {
        if (_enumIndices[lid].load_relaxed() != vespalib::datastore::EntryRef(e)) {
            this->clearDoc(lid);
        }
    }
}


template <typename B>
void
SingleValueEnumAttribute<B>::onShrinkLidSpace()
{
    EnumHandle e(0);
    bool findDefaultEnumRes(this->findEnum(this->getDefaultEnumTypeValue(), e));
    assert(findDefaultEnumRes);
    uint32_t committedDocIdLimit = this->getCommittedDocIdLimit();
    assert(_enumIndices.size() >= committedDocIdLimit);
    attribute::IPostingListAttributeBase *pab = this->getIPostingListAttributeBase();
    if (pab != nullptr) {
        pab->clearPostings(e, committedDocIdLimit, _enumIndices.size());
    }
    uint32_t shrink_docs = _enumIndices.size() - committedDocIdLimit;
    if (shrink_docs > 0u) {
        vespalib::datastore::EntryRef default_value_ref(e);
        assert(default_value_ref.valid());
        uint32_t default_value_ref_count = this->_enumStore.get_ref_count(default_value_ref);
        assert(default_value_ref_count >= shrink_docs);
        this->_enumStore.set_ref_count(default_value_ref, default_value_ref_count - shrink_docs);
        IEnumStore::IndexList possibly_unused;
        possibly_unused.push_back(default_value_ref);
        this->_enumStore.free_unused_values(std::move(possibly_unused));
    }
    _enumIndices.shrink(committedDocIdLimit);
    this->setNumDocs(committedDocIdLimit);
}

template <typename B>
std::unique_ptr<AttributeSaver>
SingleValueEnumAttribute<B>::onInitSave(vespalib::stringref fileName)
{
    auto guard = this->getGenerationHandler().takeGuard();
    return std::make_unique<SingleValueEnumAttributeSaver>
        (std::move(guard),
         this->createAttributeHeader(fileName),
         getIndicesCopy(this->getCommittedDocIdLimit()),
         this->_enumStore);
}

} // namespace search
