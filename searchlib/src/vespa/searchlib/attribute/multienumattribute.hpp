// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multienumattribute.h"
#include "multivalueattribute.hpp"
#include "multienumattributesaver.h"
#include "load_utils.h"
#include "enum_store_loaders.h"
#include "ipostinglistattributebase.h"
#include "valuemodifier.h"
#include <vespa/vespalib/stllike/hashtable.hpp>
#include <vespa/vespalib/datastore/unique_store_remapper.h>

namespace search {

namespace multienumattribute {

template <typename WeightedIndex>
void
remap_enum_store_refs(const IEnumStore::EnumIndexRemapper& remapper, AttributeVector& v, attribute::MultiValueMapping<WeightedIndex>& multi_value_mapping);

}

template <typename B, typename M>
bool
MultiValueEnumAttribute<B, M>::extractChangeData(const Change & c, EnumIndex & idx)
{
    if ( ! c.has_entry_ref() ) {
        return this->_enumStore.find_index(c._data.raw(), idx);
    }
    idx = EnumIndex(vespalib::datastore::EntryRef(c.get_entry_ref()));
    return true;
}

template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::considerAttributeChange(const Change & c, EnumStoreBatchUpdater & inserter)
{
    if (c._type == ChangeBase::APPEND ||
        (this->getInternalCollectionType().createIfNonExistant() &&
         (c._type >= ChangeBase::INCREASEWEIGHT && c._type <= ChangeBase::SETWEIGHT)))
    {
        EnumIndex idx;
        if (!this->_enumStore.find_index(c._data.raw(), idx)) {
            c.set_entry_ref(inserter.insert(c._data.raw()).ref());
        } else {
            c.set_entry_ref(idx.ref());
        }
    }
}

template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::applyValueChanges(const DocIndices& docIndices, EnumStoreBatchUpdater& updater)
{
    // set new set of indices for documents with changes
    ValueModifier valueGuard(this->getValueModifier());
    for (const auto& doc_values : docIndices) {
        vespalib::ConstArrayRef<WeightedIndex> oldIndices(this->_mvMapping.get(doc_values.first));
        uint32_t valueCount = oldIndices.size();
        this->_mvMapping.set(doc_values.first, doc_values.second);
        for (uint32_t i = 0; i < doc_values.second.size(); ++i) {
            updater.inc_ref_count(multivalue::get_value_ref(doc_values.second[i]).load_relaxed());
        }
        for (uint32_t i = 0; i < valueCount; ++i) {
            updater.dec_ref_count(multivalue::get_value_ref(oldIndices[i]).load_relaxed());
        }
    }
}

template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::fillValues(LoadedVector & loaded)
{
    if constexpr(!std::is_same_v<LoadedVector, NoLoadedVector>) {
        uint32_t numDocs(this->getNumDocs());
        size_t numValues = loaded.size();
        size_t count = 0;
        WeightedIndexVector indices;
        this->_mvMapping.prepareLoadFromMultiValue();
        for (DocId doc = 0; doc < numDocs; ++doc) {
            for(const auto* v = & loaded.read();(count < numValues) && (v->_docId == doc); count++, loaded.next(), v = & loaded.read()) {
                indices.push_back(multivalue::ValueBuilder<WeightedIndex>::build(AtomicEntryRef(v->getEidx()), v->getWeight()));
            }
            this->checkSetMaxValueCount(indices.size());
            this->_mvMapping.set(doc, indices);
            indices.clear();
        }
        this->_mvMapping.doneLoadFromMultiValue();
    }
}


template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::load_enumerated_data(ReaderBase& attrReader,
                                                    enumstore::EnumeratedPostingsLoader& loader,
                                                    size_t num_values)
{
    loader.reserve_loaded_enums(num_values);
    uint32_t maxvc = attribute::loadFromEnumeratedMultiValue(this->_mvMapping, attrReader,
                                                             vespalib::ConstArrayRef<EnumIndex>(loader.get_enum_indexes()),
                                                             loader.get_enum_value_remapping(),
                                                             attribute::SaveLoadedEnum(loader.get_loaded_enums()));
    loader.free_enum_value_remapping();
    loader.sort_loaded_enums();
    this->checkSetMaxValueCount(maxvc);
}

template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::load_enumerated_data(ReaderBase& attrReader,
                                                    enumstore::EnumeratedLoader& loader)
{
    loader.allocate_enums_histogram();
    uint32_t maxvc = attribute::loadFromEnumeratedMultiValue(this->_mvMapping, attrReader,
                                                             vespalib::ConstArrayRef<EnumIndex>(loader.get_enum_indexes()),
                                                             loader.get_enum_value_remapping(),
                                                             attribute::SaveEnumHist(loader.get_enums_histogram()));
    loader.free_enum_value_remapping();
    loader.set_ref_counts();
    loader.build_dictionary();
    loader.free_unused_values();
    this->checkSetMaxValueCount(maxvc);
}

template <typename B, typename M>
MultiValueEnumAttribute<B, M>::
MultiValueEnumAttribute(const vespalib::string &baseFileName,
                        const AttributeVector::Config & cfg)
    : MultiValueAttribute<B, M>(baseFileName, cfg)
{
}

template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::onCommit()
{
    // update enum store
    auto updater = this->_enumStore.make_batch_updater();
    this->insertNewUniqueValues(updater);
    DocIndices docIndices;
    this->applyAttributeChanges(docIndices);
    applyValueChanges(docIndices, updater);
    this->_changes.clear();
    updater.commit();
    this->freezeEnumDictionary();
    std::atomic_thread_fence(std::memory_order_release);
    this->reclaim_unused_memory();
    if (this->_mvMapping.considerCompact(this->getConfig().getCompactionStrategy())) {
        this->incGeneration();
        this->updateStat(true);
    }
    auto remapper = this->_enumStore.consider_compact_values(this->getConfig().getCompactionStrategy());
    if (remapper) {
        multienumattribute::remap_enum_store_refs(*remapper, *this, this->_mvMapping);
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

template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::onUpdateStat()
{
    // update statistics
    vespalib::MemoryUsage total;
    auto& compaction_strategy = this->getConfig().getCompactionStrategy();
    total.merge(this->_enumStore.update_stat(compaction_strategy));
    total.merge(this->_mvMapping.updateStat(compaction_strategy));
    total.merge(this->getChangeVectorMemoryUsage());
    mergeMemoryStats(total);
    this->updateStatistics(this->_mvMapping.getTotalValueCnt(), this->_enumStore.get_num_uniques(), total.allocatedBytes(),
                     total.usedBytes(), total.deadBytes(), total.allocatedBytesOnHold());
}

template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::reclaim_memory(generation_t oldest_used_gen)
{
    this->_enumStore.reclaim_memory(oldest_used_gen);
    this->_mvMapping.reclaim_memory(oldest_used_gen);
}

template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::before_inc_generation(generation_t current_gen)
{
    /*
     * Freeze tree before generation is increased in attribute vector
     * but after generation is increased in tree. This ensures that
     * unlocked readers accessing a frozen tree will access a
     * sufficiently new frozen tree.
     */
    freezeEnumDictionary();
    this->_mvMapping.assign_generation(current_gen);
    this->_enumStore.assign_generation(current_gen);
}

template <typename B, typename M>
std::unique_ptr<AttributeSaver>
MultiValueEnumAttribute<B, M>::onInitSave(vespalib::stringref fileName)
{
    auto guard = this->getGenerationHandler().takeGuard();
    return std::make_unique<MultiValueEnumAttributeSaver<WeightedIndex>>
        (std::move(guard), this->createAttributeHeader(fileName), this->_mvMapping, this->_enumStore);
}

} // namespace search

