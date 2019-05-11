// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multivalueattribute.h"
#include "enumstorebase.h"
#include "loadedenumvalue.h"
#include "multivalue.h"

namespace search {

class IWeightedIndexVector {
public:
    virtual ~IWeightedIndexVector() = default;
    using WeightedIndex = multivalue::WeightedValue<EnumStoreBase::Index>;
    /**
     * Provides a reference to the underlying enum/weight pairs.
     * This method should only be invoked if @ref getCollectionType(docId) returns CollectionType::WEIGHTED_SET.
     *
     * @param doc document identifier
     * @param values Reference to values and weights
     * @return the number of values for this document
     **/
    virtual uint32_t getEnumHandles(uint32_t doc, const WeightedIndex * & values) const;
};

class ReaderBase;

/*
 * Implementation of multi value enum attribute that uses an underlying enum store
 * to store unique values and a multi value mapping to store enum indices for each document.
 *
 * B: EnumAttribute<BaseClass>
 * M: MultiValueType
 */
template <typename B, typename M>
class MultiValueEnumAttribute : public MultiValueAttribute<B, M>,
                                public IWeightedIndexVector
{
protected:
    typedef typename B::UniqueSet UniqueSet;

    typedef typename B::BaseClass::Change        Change;
    typedef typename B::BaseClass::DocId         DocId;
    typedef typename B::BaseClass::EnumHandle    EnumHandle;
    typedef typename B::BaseClass::EnumModifier  EnumModifier;
    typedef typename B::BaseClass::generation_t  generation_t;
    typedef typename B::BaseClass::LoadedVector  LoadedVector;
    typedef typename B::BaseClass::ValueModifier ValueModifier;
    typedef typename B::BaseClass::WeightedEnum  WeightedEnum;

    typedef typename EnumStoreBase::Index        EnumIndex;
    typedef typename EnumStoreBase::IndexVector  EnumIndexVector;
    typedef typename EnumStoreBase::EnumVector   EnumVector;
    typedef typename MultiValueAttribute<B, M>::MultiValueType WeightedIndex;
    typedef typename MultiValueAttribute<B, M>::ValueVector    WeightedIndexVector;
    using WeightedIndexArrayRef = typename MultiValueAttribute<B, M>::MultiValueArrayRef;
    typedef typename MultiValueAttribute<B, M>::DocumentValues DocIndices;
    typedef attribute::LoadedEnumAttributeVector  LoadedEnumAttributeVector;
    typedef attribute::LoadedEnumAttribute        LoadedEnumAttribute;
    using EnumIndexMap = EnumStoreBase::EnumIndexMap;

    // from MultiValueAttribute
    bool extractChangeData(const Change & c, EnumIndex & idx) override; // EnumIndex is ValueType. Use EnumStore

    // from EnumAttribute
    void considerAttributeChange(const Change & c, UniqueSet & newUniques) override; // same for both string and numeric
    void reEnumerate(const EnumIndexMap &) override; // same for both string and numeric

    virtual void applyValueChanges(const DocIndices & docIndices, EnumStoreBase::IndexVector & unused);

    void incRefCount(const WeightedIndex & idx) { this->_enumStore.incRefCount(idx); }
    void decRefCount(const WeightedIndex & idx) { this->_enumStore.decRefCount(idx); }

    virtual void freezeEnumDictionary() {
        this->getEnumStore().freezeTree();
    }

    void fillValues(LoadedVector & loaded) override;
    void fillEnumIdx(ReaderBase &attrReader, const EnumIndexVector &eidxs, LoadedEnumAttributeVector &loaded) override;
    void fillEnumIdx(ReaderBase &attrReader, const EnumIndexVector &eidxs, EnumVector &enumHist) override;
    virtual void mergeMemoryStats(MemoryUsage & total) { (void) total; }

public:
    MultiValueEnumAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & cfg);

    uint32_t getEnumHandles(DocId doc, const IWeightedIndexVector::WeightedIndex * & values) const override final;

    void onCommit() override;
    void onUpdateStat() override;

    void removeOldGenerations(generation_t firstUsed) override;
    void onGenerationChange(generation_t generation) override;

    //-----------------------------------------------------------------------------------------------------------------
    // Attribute read API
    //-----------------------------------------------------------------------------------------------------------------
    EnumHandle getEnum(DocId doc) const override {
        WeightedIndexArrayRef indices(this->_mvMapping.get(doc));
        if (indices.size() == 0) {
            return std::numeric_limits<uint32_t>::max();
        } else {
            return indices[0].value().ref();
        }
    }

    uint32_t get(DocId doc, EnumHandle * e, uint32_t sz) const override {
        WeightedIndexArrayRef indices(this->_mvMapping.get(doc));
        uint32_t valueCount = indices.size();
        for (uint32_t i = 0, m = std::min(sz, valueCount); i < m; ++i) {
            e[i] = indices[i].value().ref();
        }
        return valueCount;
    }
     uint32_t get(DocId doc, WeightedEnum * e, uint32_t sz) const override {
        WeightedIndexArrayRef indices(this->_mvMapping.get(doc));
        uint32_t valueCount = indices.size();
        for (uint32_t i = 0, m = std::min(sz, valueCount); i < m; ++i) {
            e[i] = WeightedEnum(indices[i].value().ref(), indices[i].weight());
        }
        return valueCount;
    }

    std::unique_ptr<AttributeSaver> onInitSave(vespalib::stringref fileName) override;
};

} // namespace search

