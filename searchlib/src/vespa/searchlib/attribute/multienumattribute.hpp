// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multienumattribute.h"
#include "multivalueattribute.hpp"
#include "multienumattributesaver.h"
#include "load_utils.h"
#include <vespa/vespalib/stllike/hashtable.hpp>

namespace search {

template <typename B, typename M>
bool
MultiValueEnumAttribute<B, M>::extractChangeData(const Change & c, EnumIndex & idx)
{
    if (c._enumScratchPad == Change::UNSET_ENUM) {
        return this->_enumStore.findIndex(c._data.raw(), idx);
    }
    idx = EnumIndex(datastore::EntryRef(c._enumScratchPad));
    return true;
}

template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::considerAttributeChange(const Change & c, UniqueSet & newUniques)
{
    if (c._type == ChangeBase::APPEND ||
        (this->getInternalCollectionType().createIfNonExistant() &&
         (c._type >= ChangeBase::INCREASEWEIGHT && c._type <= ChangeBase::DIVWEIGHT)))
    {
        EnumIndex idx;
        if (!this->_enumStore.findIndex(c._data.raw(), idx)) {
            newUniques.insert(c._data);
        } else {
            c._enumScratchPad = idx.ref();
        }
    }
}

template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::reEnumerate(const EnumIndexMap & old2new)
{
    // update MultiValueMapping with new EnumIndex values.
    this->logEnumStoreEvent("compactfixup", "drain");
    {
        EnumModifier enumGuard(this->getEnumModifier());
        this->logEnumStoreEvent("compactfixup", "start");
        for (DocId doc = 0; doc < this->getNumDocs(); ++doc) {
            vespalib::ConstArrayRef<WeightedIndex> indicesRef(this->_mvMapping.get(doc));
            WeightedIndexVector indices(indicesRef.cbegin(), indicesRef.cend());
            for (uint32_t i = 0; i < indices.size(); ++i) {
                EnumIndex oldIndex = indices[i].value();
                indices[i] = WeightedIndex(old2new[oldIndex], indices[i].weight());
            }
            std::atomic_thread_fence(std::memory_order_release);
            this->_mvMapping.replace(doc, indices);
        }
    }
    this->logEnumStoreEvent("compactfixup", "complete");
}

template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::applyValueChanges(const DocIndices & docIndices, EnumStoreBase::IndexVector & unused)
{
    // set new set of indices for documents with changes
    ValueModifier valueGuard(this->getValueModifier());
    for (typename DocIndices::const_iterator iter = docIndices.begin(); iter != docIndices.end(); ++iter) {
        vespalib::ConstArrayRef<WeightedIndex> oldIndices(this->_mvMapping.get(iter->first));
        uint32_t valueCount = oldIndices.size();
        this->_mvMapping.set(iter->first, iter->second);
        for (uint32_t i = 0; i < iter->second.size(); ++i) {
            incRefCount(iter->second[i]);
        }
        for (uint32_t i = 0; i < valueCount; ++i) {
            decRefCount(oldIndices[i]);
            if (this->_enumStore.getRefCount(oldIndices[i]) == 0) {
                unused.push_back(oldIndices[i].value());
            }
        }
    }
}

template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::fillValues(LoadedVector & loaded)
{
    uint32_t numDocs(this->getNumDocs());
    size_t numValues = loaded.size();
    size_t count = 0;
    WeightedIndexVector indices;
    this->_mvMapping.prepareLoadFromMultiValue();
    for (DocId doc = 0; doc < numDocs; ++doc) {
        for(const typename LoadedVector::Type * v = & loaded.read();(count < numValues) && (v->_docId == doc); count++, loaded.next(), v = & loaded.read()) {
            indices.push_back(WeightedIndex(v->getEidx(), v->getWeight()));
        }
        this->checkSetMaxValueCount(indices.size());
        this->_mvMapping.set(doc, indices);
        indices.clear();
    }
    this->_mvMapping.doneLoadFromMultiValue();
}


template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::fillEnumIdx(ReaderBase &attrReader,
                                           const EnumIndexVector &eidxs,
                                           LoadedEnumAttributeVector &loaded)
{
    uint32_t maxvc = attribute::loadFromEnumeratedMultiValue(this->_mvMapping, attrReader, vespalib::ConstArrayRef<EnumIndex>(eidxs), attribute::SaveLoadedEnum(loaded));
    this->checkSetMaxValueCount(maxvc);
}

template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::fillEnumIdx(ReaderBase &attrReader,
                                           const EnumIndexVector &eidxs,
                                           EnumVector &enumHist)
{
    uint32_t maxvc = attribute::loadFromEnumeratedMultiValue(this->_mvMapping, attrReader, vespalib::ConstArrayRef<EnumIndex>(eidxs), attribute::SaveEnumHist(enumHist));
    this->checkSetMaxValueCount(maxvc);
}

template <typename B, typename M>
MultiValueEnumAttribute<B, M>::
MultiValueEnumAttribute(const vespalib::string &baseFileName,
                        const AttributeVector::Config & cfg)
    : MultiValueAttribute<B, M>(baseFileName, cfg)
{
}

namespace {

template<typename T>
const IWeightedIndexVector::WeightedIndex *
extract(const T *) {
    throw std::runtime_error("IWeightedIndexVector::getEnumHandles not implemented");
}

template <>
inline const IWeightedIndexVector::WeightedIndex *
extract(const IWeightedIndexVector::WeightedIndex * values) {
    return values;
}

}

template <typename B, typename M>
uint32_t
MultiValueEnumAttribute<B, M>::getEnumHandles(DocId doc, const IWeightedIndexVector::WeightedIndex * & values) const  {
    WeightedIndexArrayRef indices(this->_mvMapping.get(doc));
    values = extract(&indices[0]);
    return indices.size();
}

template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::onCommit()
{
    // update enum store
    EnumStoreBase::IndexVector possiblyUnused;
    this->insertNewUniqueValues(possiblyUnused);
    DocIndices docIndices;
    this->applyAttributeChanges(docIndices);
    applyValueChanges(docIndices, possiblyUnused);
    this->_changes.clear();
    this->_enumStore.freeUnusedEnums(possiblyUnused);
    this->freezeEnumDictionary();
    this->setEnumMax(this->_enumStore.getLastEnum());
    std::atomic_thread_fence(std::memory_order_release);
    this->removeAllOldGenerations();
    if (this->_mvMapping.considerCompact(this->getConfig().getCompactionStrategy())) {
        this->incGeneration();
        this->updateStat(true);
    }
}

template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::onUpdateStat()
{
    // update statistics
    MemoryUsage total;
    total.merge(this->_enumStore.getMemoryUsage());
    total.merge(this->_enumStore.getTreeMemoryUsage());
    total.merge(this->_mvMapping.updateStat());
    total.merge(this->getChangeVectorMemoryUsage());
    mergeMemoryStats(total);
    this->updateStatistics(this->_mvMapping.getTotalValueCnt(), this->_enumStore.getNumUniques(), total.allocatedBytes(),
                     total.usedBytes(), total.deadBytes(), total.allocatedBytesOnHold());
}

template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::removeOldGenerations(generation_t firstUsed)
{
    this->_enumStore.trimHoldLists(firstUsed);
    this->_mvMapping.trimHoldLists(firstUsed);
}

template <typename B, typename M>
void
MultiValueEnumAttribute<B, M>::onGenerationChange(generation_t generation)
{
    /*
     * Freeze tree before generation is increased in attribute vector
     * but after generation is increased in tree. This ensures that
     * unlocked readers accessing a frozen tree will access a
     * sufficiently new frozen tree.
     */
    freezeEnumDictionary();
    this->_mvMapping.transferHoldLists(generation - 1);
    this->_enumStore.transferHoldLists(generation - 1);
}

template <typename B, typename M>
std::unique_ptr<AttributeSaver>
MultiValueEnumAttribute<B, M>::onInitSave(vespalib::stringref fileName)
{
    this->_enumStore.reEnumerate();
    vespalib::GenerationHandler::Guard guard(this->getGenerationHandler().takeGuard());
    return std::make_unique<MultiValueEnumAttributeSaver<WeightedIndex>>
        (std::move(guard), this->createAttributeHeader(fileName), this->_mvMapping, this->_enumStore);
}

} // namespace search

