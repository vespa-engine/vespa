// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributevector.h"
#include "loadedenumvalue.h"
#include "enumstore.h"
#include "no_loaded_vector.h"

namespace search {

template <typename B>
class EnumAttribute : public B
{
protected:
    using BaseClass = B;
    using Change = typename B::Change;
    using ChangeVector = typename B::ChangeVector;
    using DocId = typename B::DocId;
    using EnumEntryType = typename B::EnumEntryType;  // Template argument for enum store
    using EnumHandle = typename B::EnumHandle;
    using ValueModifier = typename B::ValueModifier;

public:
    using EnumVector = typename B::EnumVector;
    using LoadedVector = typename B::LoadedVector;

protected:
    using generation_t = typename B::generation_t;
    using B::getGenerationHolder;

public:
    using EnumStore = EnumStoreT<EnumEntryType>;
    using EnumStoreBatchUpdater = typename EnumStore::BatchUpdater;

protected:
    using EnumIndex = IEnumStore::Index;

    EnumStore _enumStore;

    const IEnumStore* getEnumStoreBase() const override { return &_enumStore; }
    IEnumStore* getEnumStoreBase() override { return &_enumStore; }
    EnumEntryType getFromEnum(EnumHandle e) const override { return _enumStore.get_value(e); }

    void load_posting_lists(LoadedVector& loaded) override { (void) loaded; }
    void load_enum_store(LoadedVector& loaded) override;
    uint64_t getUniqueValueCount() const override;

    EnumEntryType getDefaultEnumTypeValue() { return B::defaultValue(); }

    /*
     * Iterate through the change vector and find new unique values.
     * Perform compaction if necessary and insert the new unique values into the EnumStore.
     */
    void insertNewUniqueValues(EnumStoreBatchUpdater& updater);
    virtual void considerAttributeChange(const Change & c, EnumStoreBatchUpdater & inserter) = 0;
    vespalib::MemoryUsage getEnumStoreValuesMemoryUsage() const override;
    void populate_address_space_usage(AddressSpaceUsage& usage) const override;
    void cache_change_data_entry_ref(const Change& c) const;
public:
    EnumAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & cfg);
    ~EnumAttribute();
    bool findEnum(EnumEntryType v, EnumHandle & e) const override { return _enumStore.find_enum(v, e); }
    const EnumStore & getEnumStore() const { return _enumStore; }
    EnumStore &       getEnumStore()       { return _enumStore; }
};

} // namespace search
