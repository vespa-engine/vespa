// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enum_store_dictionary.h"
#include "i_enum_store.h"
#include <vespa/searchlib/util/foldedstringcompare.h>
#include <vespa/vespalib/btree/btreenode.h>
#include <vespa/vespalib/btree/btreenodeallocator.h>
#include <vespa/vespalib/btree/btree.h>
#include <vespa/vespalib/btree/btreebuilder.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/datastore/unique_store.h>
#include <vespa/vespalib/datastore/unique_store_string_allocator.h>
#include <vespa/vespalib/util/buffer.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cmath>

namespace search {

template <typename> class EnumStoreComparatorT;
template <typename> class EnumStoreFoldedComparatorT;

/**
 * Class representing a numeric entry type in a enum store.
 * Used as template argument for EnumStoreT.
 **/

template <typename T>
class NumericEntryType {
public:
    typedef T Type;
    static uint32_t size(Type)  { return fixedSize(); }
    static uint32_t fixedSize() { return sizeof(T); }
    static bool hasFold() { return false; }
};

/**
 * Class representing a string entry type in a enum store.
 * Used as template argument for EnumStoreT.
 **/
class StringEntryType {
public:
    typedef const char * Type;
    static uint32_t size(Type value) { return strlen(value) + fixedSize(); }
    static uint32_t fixedSize()      { return 1; }
    static bool hasFold() { return true; }
};


/**
 * Used to determine the ordering between two floating point values that can be NAN.
 **/
struct FloatingPointCompareHelper
{
    template <typename T>
    static int compare(T a, T b) {
        if (std::isnan(a) && std::isnan(b)) {
            return 0;
        } else if (std::isnan(a)) {
            return -1;
        } else if (std::isnan(b)) {
            return 1;
        } else if (a < b) {
            return -1;
        } else if (a == b) {
            return 0;
        }
        return 1;
    }
};


//-----------------------------------------------------------------------------
// EnumStoreT
//-----------------------------------------------------------------------------
template <class EntryType>
class EnumStoreT : public IEnumStore
{
    friend class EnumStoreTest;
public:
    using DataType = typename EntryType::Type;
    using ComparatorType = EnumStoreComparatorT<EntryType>;
    using AllocatorType = std::conditional_t<std::is_same_v<DataType, const char *>,
                                             datastore::UniqueStoreStringAllocator<Index>,
                                             datastore::UniqueStoreAllocator<DataType, Index>>;

    using UniqueStoreType = datastore::UniqueStore<DataType, Index, ComparatorType, AllocatorType>;
    using FoldedComparatorType = EnumStoreFoldedComparatorT<EntryType>;
    using EnumStoreType = EnumStoreT<EntryType>;
    using EntryRef = datastore::EntryRef;
    using generation_t = vespalib::GenerationHandler::generation_t;


private:
    UniqueStoreType _store;
    IEnumStoreDictionary& _dict;

    EnumStoreT(const EnumStoreT & rhs) = delete;
    EnumStoreT & operator=(const EnumStoreT & rhs) = delete;

    void freeUnusedEnum(Index idx, IndexSet& unused) override;

    const datastore::UniqueStoreEntryBase& get_entry_base(Index idx) const {
        return _store.get_allocator().get_wrapped(idx);
    }

public:
    EnumStoreT(bool hasPostings);
    virtual ~EnumStoreT();

    uint32_t getRefCount(Index idx) const { return get_entry_base(idx).get_ref_count(); }
    // TODO: Remove from public API
    void incRefCount(Index idx) { return get_entry_base(idx).inc_ref_count(); }
    void decRefCount(Index idx) { return get_entry_base(idx).dec_ref_count(); }

    // Only use when reading from enumerated attribute save files
    // TODO: Instead create an API that is used for loading/initializing.
    void fixupRefCount(Index idx, uint32_t refCount) override {
        get_entry_base(idx).set_ref_count(refCount);
    }

    uint32_t getNumUniques() const override { return _dict.getNumUniques(); }

    vespalib::MemoryUsage getValuesMemoryUsage() const override { return _store.get_allocator().get_data_store().getMemoryUsage(); }
    vespalib::MemoryUsage getDictionaryMemoryUsage() const override { return _dict.get_memory_usage(); }

    vespalib::AddressSpace getAddressSpaceUsage() const;

    void transferHoldLists(generation_t generation);
    void trimHoldLists(generation_t firstUsed);

    ssize_t deserialize0(const void *src, size_t available, IndexVector &idx) override;

    ssize_t deserialize(const void *src, size_t available, IndexVector &idx) {
        return _dict.deserialize(src, available, idx);
    }

    void fixupRefCounts(const EnumVector &hist) { _dict.fixupRefCounts(hist); }
    void freezeTree() { _store.freeze(); }

    IEnumStoreDictionary &getEnumStoreDict() override { return _dict; }
    const IEnumStoreDictionary &getEnumStoreDict() const override { return _dict; }
    EnumPostingTree &getPostingDictionary() { return _dict.getPostingDictionary(); }

    const EnumPostingTree &getPostingDictionary() const {
        return _dict.getPostingDictionary();
    }

    // TODO: Add API for getting compaction count instead.
    const datastore::DataStoreBase &get_data_store_base() const override { return _store.get_allocator().get_data_store(); }


    bool getValue(Index idx, DataType& value) const;
    DataType getValue(uint32_t idx) const { return getValue(Index(EntryRef(idx))); }
    DataType getValue(Index idx) const { return _store.get(idx); }

    // TODO: Implement helper class to populate enum store when loading from enumerated save files.

    /**
     * Used when building enum store from non-enumerated save files.
     * TODO: Find better name.
     */
    class Builder {
    private:
        AllocatorType& _allocator;
        datastore::IUniqueStoreDictionary& _dict;
        std::vector<EntryRef> _refs;
        std::vector<uint32_t> _payloads;

    public:
        Builder(AllocatorType& allocator, datastore::IUniqueStoreDictionary& dict)
            : _allocator(allocator),
              _dict(dict),
              _refs(),
              _payloads()
        {
        }
        ~Builder();
        Index insert(const DataType& value, uint32_t posting_idx = 0) {
            EntryRef new_ref = _allocator.allocate(value);
            _refs.emplace_back(new_ref);
            _payloads.emplace_back(posting_idx);
            return new_ref;
        }
        void set_ref_count_for_last_value(uint32_t ref_count) {
            assert(!_refs.empty());
            _allocator.get_wrapped(_refs.back()).set_ref_count(ref_count);
        }
        void build() {
            _dict.build_with_payload(_refs, _payloads);
        }
    };

    Builder make_builder() {
        return Builder(_store.get_allocator(), _dict);
    }

    class BatchUpdater {
    private:
        EnumStoreType& _store;
        IndexSet _possibly_unused;

    public:
        BatchUpdater(EnumStoreType& store)
            : _store(store),
              _possibly_unused()
        {}
        // TODO: Rename to insert()
        void add(DataType value) {
            Index idx;
            _store.addEnum(value, idx);
            _possibly_unused.insert(idx);
        }
        void inc_ref_count(Index idx) {
            _store.get_entry_base(idx).inc_ref_count();
        }
        void dec_ref_count(Index idx) {
            auto& entry = _store.get_entry_base(idx);
            entry.dec_ref_count();
            if (entry.get_ref_count() == 0) {
                _possibly_unused.insert(idx);
            }
        }
        void commit() {
            _store.freeUnusedEnums(_possibly_unused);
        }
    };

    BatchUpdater make_batch_updater() {
        return BatchUpdater(*this);
    }

    // TODO: Change to sending enum indexes as const array ref.
    void writeValues(BufferWriter &writer, const Index *idxs, size_t count) const override;
    ssize_t deserialize(const void *src, size_t available, size_t &initSpace);
    ssize_t deserialize(const void *src, size_t available, Index &idx);
    bool foldedChange(const Index &idx1, const Index &idx2) override;
    bool findEnum(DataType value, IEnumStore::EnumHandle &e) const;
    std::vector<IEnumStore::EnumHandle> findFoldedEnums(DataType value) const;
    void addEnum(DataType value, Index &newIdx);
    bool findIndex(DataType value, Index &idx) const;
    void freeUnusedEnums(bool movePostingidx) override;
    void freeUnusedEnums(const IndexSet& toRemove);

private:
    template <typename Dictionary>
    void addEnum(DataType value, Index& newIdx, Dictionary& dict);

};

std::unique_ptr<datastore::IUniqueStoreDictionary>
make_enum_store_dictionary(IEnumStore &store, bool has_postings);

vespalib::asciistream & operator << (vespalib::asciistream & os, const IEnumStore::Index & idx);

extern template
class datastore::DataStoreT<IEnumStore::Index>;


template <>
void
EnumStoreT<StringEntryType>::writeValues(BufferWriter& writer,
                                         const IEnumStore::Index* idxs,
                                         size_t count) const;

template <>
ssize_t
EnumStoreT<StringEntryType>::deserialize(const void* src,
                                         size_t available,
                                         Index& idx);


extern template
class btree::BTreeBuilder<IEnumStore::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                          EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;
extern template
class btree::BTreeBuilder<IEnumStore::Index, datastore::EntryRef, btree::NoAggregated,
                          EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template class EnumStoreT< StringEntryType >;
extern template class EnumStoreT<NumericEntryType<int8_t> >;
extern template class EnumStoreT<NumericEntryType<int16_t> >;
extern template class EnumStoreT<NumericEntryType<int32_t> >;
extern template class EnumStoreT<NumericEntryType<int64_t> >;
extern template class EnumStoreT<NumericEntryType<float> >;
extern template class EnumStoreT<NumericEntryType<double> >;

} // namespace search

