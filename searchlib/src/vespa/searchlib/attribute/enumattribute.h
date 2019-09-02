// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributevector.h"
#include "loadedenumvalue.h"
#include "enumstore.h"
#include "no_loaded_vector.h"
#include <set>

namespace search {

namespace attribute {

template <typename, typename, typename > class PostingSearchContext;

}

template <typename B>
class EnumAttribute : public B
{
    template <typename, typename, typename> 
    friend class attribute::PostingSearchContext; // getEnumStore()
protected:
    using BaseClass = B;
    using Change = typename B::Change;
    using ChangeDataType = typename B::Change::DataType;
    using ChangeVector = typename B::ChangeVector;
    using ChangeVectorIterator = typename B::ChangeVector::const_iterator;
    using DocId = typename B::DocId;
    using EnumEntryType = typename B::EnumEntryType;  // Template argument for enum store
    using EnumHandle = typename B::EnumHandle;
    using EnumModifier = typename B::EnumModifier;
    using EnumType = typename B::EnumEntryType::Type; // Type stored in enum store (integer, float, string)
    using ValueModifier = typename B::ValueModifier;

public:
    using EnumIndexVector = typename B::EnumIndexVector;
    using EnumVector = typename B::EnumVector;
    using LoadedValueType = typename B::LoadedValueType;
    using LoadedVector = typename B::LoadedVector;

protected:
    using generation_t = typename B::generation_t;
    using UniqueSet = std::set<ChangeDataType>;
    using LoadedEnumAttributeVector = attribute::LoadedEnumAttributeVector;
    using B::getGenerationHolder;
    using B::getStatus;

public:
    using EnumStore = EnumStoreT<EnumEntryType>;
    using EnumStoreBatchUpdater = typename EnumStore::BatchUpdater;

protected:
    using EnumIndex = IEnumStore::Index;
    using EnumIndexRemapper = IEnumStore::EnumIndexRemapper;

    EnumStore _enumStore;

    EnumStore &       getEnumStore()       { return _enumStore; }
    const EnumStore & getEnumStore() const { return _enumStore; }

    const IEnumStore* getEnumStoreBase() const override { return &_enumStore; }
    EnumType getFromEnum(EnumHandle e)        const override { return _enumStore.getValue(e); }

    void fillPostings(LoadedVector & loaded) override { (void) loaded; }
    void fillEnum(LoadedVector & loaded) override;
    void fillEnum0(const void *src, size_t srcLen, EnumIndexVector &eidxs) override;
    void fixupEnumRefCounts(const EnumVector &enumHist) override;
    uint64_t getUniqueValueCount() const override;

    static EnumType getDefaultEnumTypeValue() { return B::defaultValue(); }

    /*
     * Iterate through the change vector and find new unique values.
     * Perform compaction if necessary and insert the new unique values into the EnumStore.
     */
    void insertNewUniqueValues(EnumStoreBatchUpdater& updater);
    virtual void considerAttributeChange(const Change & c, UniqueSet & newUniques) = 0;
    vespalib::MemoryUsage getEnumStoreValuesMemoryUsage() const override;
    vespalib::AddressSpace getEnumStoreAddressSpaceUsage() const override;
public:
    EnumAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & cfg);
    ~EnumAttribute();
    bool findEnum(EnumType v, EnumHandle & e) const override { return _enumStore.findEnum(v, e); }
};

} // namespace search
