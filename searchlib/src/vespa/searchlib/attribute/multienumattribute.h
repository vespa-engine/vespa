// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/multivalueattribute.h>
#include <vespa/searchlib/attribute/enumstorebase.h>
#include <vespa/searchlib/attribute/loadedenumvalue.h>

namespace search {

/*
 * Implementation of multi value enum attribute that uses an underlying enum store
 * to store unique values and a multi value mapping to store enum indices for each document.
 *
 * B: EnumAttribute<BaseClass>
 * M: MultiValueType (MultiValueMapping template argument)
 */
template <typename B, typename M>
class MultiValueEnumAttribute : public MultiValueAttribute<B, M>
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
    typedef typename MultiValueAttribute<B, M>::Histogram      Histogram;
    typedef typename MultiValueAttribute<B, M>::DocumentValues DocIndices;
    typedef AttributeVector::ReaderBase     ReaderBase;
    typedef attribute::LoadedEnumAttributeVector  LoadedEnumAttributeVector;
    typedef attribute::LoadedEnumAttribute        LoadedEnumAttribute;

    // from MultiValueAttribute
    virtual bool extractChangeData(const Change & c, EnumIndex & idx); // EnumIndex is ValueType. Use EnumStore

    // from EnumAttribute
    virtual void considerAttributeChange(const Change & c, UniqueSet & newUniques); // same for both string and numeric
    virtual void reEnumerate(); // same for both string and numeric

    virtual void applyValueChanges(const DocIndices & docIndices, EnumStoreBase::IndexVector & unused);

    void incRefCount(const WeightedIndex & idx) { this->_enumStore.incRefCount(idx); }
    void decRefCount(const WeightedIndex & idx) { this->_enumStore.decRefCount(idx); }

    virtual void
    freezeEnumDictionary()
    {
        this->getEnumStore().freezeTree();
    }

    virtual void fillValues(LoadedVector & loaded);

    virtual void
    fillEnumIdx(ReaderBase &attrReader,
                const EnumIndexVector &eidxs,
                LoadedEnumAttributeVector &loaded);

    virtual void
    fillEnumIdx(ReaderBase &attrReader,
                const EnumIndexVector &eidxs,
                EnumVector &enumHist);

    virtual void mergeMemoryStats(MemoryUsage & total) { (void) total; }

public:
    MultiValueEnumAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & cfg);

    virtual void onCommit();
    virtual void onUpdateStat();

    virtual void removeOldGenerations(generation_t firstUsed);
    virtual void onGenerationChange(generation_t generation);

    //-----------------------------------------------------------------------------------------------------------------
    // Attribute read API
    //-----------------------------------------------------------------------------------------------------------------
    virtual EnumHandle getEnum(DocId doc) const {
        WeightedIndexArrayRef indices(this->_mvMapping.get(doc));
        if (indices.size() == 0) {
            return std::numeric_limits<uint32_t>::max();
        } else {
            return indices[0].value().ref();
        }
    }
    virtual uint32_t get(DocId doc, EnumHandle * e, uint32_t sz) const {
        WeightedIndexArrayRef indices(this->_mvMapping.get(doc));
        uint32_t valueCount = indices.size();
        for (uint32_t i = 0, m = std::min(sz, valueCount); i < m; ++i) {
            e[i] = indices[i].value().ref();
        }
        return valueCount;
    }
    virtual uint32_t get(DocId doc, WeightedEnum * e, uint32_t sz) const {
        WeightedIndexArrayRef indices(this->_mvMapping.get(doc));
        uint32_t valueCount = indices.size();
        for (uint32_t i = 0, m = std::min(sz, valueCount); i < m; ++i) {
            e[i] = WeightedEnum(indices[i].value().ref(), indices[i].weight());
        }
        return valueCount;
    }

    virtual std::unique_ptr<AttributeSaver> onInitSave() override;
};

} // namespace search

