// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributevector.h"
#include "loadedenumvalue.h"
#include "enumstore.h"
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
    typedef B                                   BaseClass;
    typedef typename B::DocId                   DocId;
    typedef typename B::EnumHandle              EnumHandle;
    typedef typename B::EnumEntryType           EnumEntryType; // Template argument for enum store
    typedef typename B::EnumEntryType::Type     EnumType;      // Type stored in enum store (integer, float, string)
    typedef typename B::Change                  Change;
    typedef typename B::Change::DataType        ChangeDataType;
    typedef typename B::ChangeVector            ChangeVector;
    typedef typename B::ChangeVector::const_iterator  ChangeVectorIterator;
    typedef typename B::EnumModifier            EnumModifier;
    typedef typename B::ValueModifier           ValueModifier;
public:
    typedef typename B::LoadedVector            LoadedVector;
    typedef typename B::EnumIndexVector         EnumIndexVector;
    typedef typename B::EnumVector              EnumVector;
    typedef typename B::LoadedValueType         LoadedValueType;
protected:
    typedef typename B::generation_t            generation_t;
    typedef std::set<ChangeDataType>            UniqueSet;
    typedef attribute::LoadedEnumAttributeVector
    LoadedEnumAttributeVector;
    using B::getGenerationHolder;
    using B::getStatus;

public:
    typedef EnumStoreT<EnumEntryType>                  EnumStore;
protected:
    typedef EnumStoreBase::Index                     EnumIndex;

    EnumStore _enumStore;

    EnumStore &       getEnumStore()       { return _enumStore; }
    const EnumStore & getEnumStore() const { return _enumStore; }

    const EnumStoreBase * getEnumStoreBase() const override { return &_enumStore; }
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
    void insertNewUniqueValues(EnumStoreBase::IndexVector & newIndexes);
    virtual void considerAttributeChange(const Change & c, UniqueSet & newUniques) = 0;
    virtual void reEnumerate() = 0;
    AddressSpace getEnumStoreAddressSpaceUsage() const override;
public:
    EnumAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & cfg);
    ~EnumAttribute();
    bool findEnum(EnumType v, EnumHandle & e) const override { return _enumStore.findEnum(v, e); }
};

} // namespace search
