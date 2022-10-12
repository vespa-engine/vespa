// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store.h"
#include "loadedenumvalue.h"
#include "multivalueattribute.h"
#include "no_loaded_vector.h"
#include <vespa/searchcommon/attribute/multivalue.h>

namespace search {

class ReaderBase;

/**
 * Implementation of multi value enum attribute that uses an underlying enum store
 * to store unique values and a multi value mapping to store enum indices for each document.
 *
 * B: EnumAttribute<BaseClass>
 * M: MultiValueType
 */
template <typename B, typename M>
class MultiValueEnumAttribute : public MultiValueAttribute<B, M>
{
protected:
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using Change = typename B::BaseClass::Change;
    using DocId = typename B::BaseClass::DocId;
    using EnumHandle = typename B::BaseClass::EnumHandle;
    using LoadedVector = typename B::BaseClass::LoadedVector;
    using ValueModifier = typename B::BaseClass::ValueModifier;
    using WeightedEnum = typename B::BaseClass::WeightedEnum;
    using generation_t = typename B::BaseClass::generation_t;

    using DocIndices = typename MultiValueAttribute<B, M>::DocumentValues;
    using EnumIndex = IEnumStore::Index;
    using EnumStoreBatchUpdater = typename B::EnumStoreBatchUpdater;
    using EnumVector = IEnumStore::EnumVector;
    using WeightedIndex = typename MultiValueAttribute<B, M>::MultiValueType;
    using WeightedIndexArrayRef = typename MultiValueAttribute<B, M>::MultiValueArrayRef;
    using WeightedIndexVector = typename MultiValueAttribute<B, M>::ValueVector;

    // from MultiValueAttribute
    bool extractChangeData(const Change & c, EnumIndex & idx) override; // EnumIndex is ValueType. Use EnumStore

    // from EnumAttribute
    void considerAttributeChange(const Change & c, EnumStoreBatchUpdater & inserter) override; // same for both string and numeric

    virtual void applyValueChanges(const DocIndices& docIndices, EnumStoreBatchUpdater& updater);

    virtual void freezeEnumDictionary() {
        this->getEnumStore().freeze_dictionary();
    }

    void fillValues(LoadedVector & loaded) override;
    void load_enumerated_data(ReaderBase& attrReader, enumstore::EnumeratedPostingsLoader& loader, size_t num_values) override;
    void load_enumerated_data(ReaderBase& attrReader, enumstore::EnumeratedLoader& loader) override;
    virtual void mergeMemoryStats(vespalib::MemoryUsage & total) { (void) total; }

public:
    MultiValueEnumAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & cfg);

    void onCommit() override;
    void onUpdateStat() override;

    void reclaim_memory(generation_t oldest_used_gen) override;
    void before_inc_generation(generation_t current_gen) override;

    //-----------------------------------------------------------------------------------------------------------------
    // Attribute read API
    //-----------------------------------------------------------------------------------------------------------------
    EnumHandle getEnum(DocId doc) const override {
        WeightedIndexArrayRef indices(this->_mvMapping.get(doc));
        if (indices.size() == 0) {
            return std::numeric_limits<uint32_t>::max();
        } else {
            return multivalue::get_value_ref(indices[0]).load_acquire().ref();
        }
    }

    uint32_t get(DocId doc, EnumHandle * e, uint32_t sz) const override {
        WeightedIndexArrayRef indices(this->_mvMapping.get(doc));
        uint32_t valueCount = indices.size();
        for (uint32_t i = 0, m = std::min(sz, valueCount); i < m; ++i) {
            e[i] = multivalue::get_value_ref(indices[i]).load_acquire().ref();
        }
        return valueCount;
    }
     uint32_t get(DocId doc, WeightedEnum * e, uint32_t sz) const override {
        WeightedIndexArrayRef indices(this->_mvMapping.get(doc));
        uint32_t valueCount = indices.size();
        for (uint32_t i = 0, m = std::min(sz, valueCount); i < m; ++i) {
            e[i] = WeightedEnum(multivalue::get_value_ref(indices[i]).load_acquire().ref(), multivalue::get_weight(indices[i]));
        }
        return valueCount;
    }

    std::unique_ptr<AttributeSaver> onInitSave(vespalib::stringref fileName) override;
};

} // namespace search

