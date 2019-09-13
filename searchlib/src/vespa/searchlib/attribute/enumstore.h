// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enum_store_dictionary.h"
#include "enum_store_loaders.h"
#include "enumcomparator.h"
#include "i_enum_store.h"
#include "loadedenumvalue.h"
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

/**
 * Class storing and providing access to all unique values stored in an enumerated attribute vector.
 *
 * It uses an instance of datastore::UniqueStore to store the actual values.
 * It also exposes the dictionary used for fast lookups into the set of unique values.
 *
 * @tparam EntryType The type of the entries/values stored.
 *                   It has special handling of type 'const char *' for strings.
 */
template <class EntryT>
class EnumStoreT : public IEnumStore {
public:
    using ComparatorType = std::conditional_t<std::is_same_v<EntryT, const char *>,
                                              EnumStoreStringComparator,
                                              EnumStoreComparator<EntryT>>;
    using AllocatorType = std::conditional_t<std::is_same_v<EntryT, const char *>,
                                             datastore::UniqueStoreStringAllocator<InternalIndex>,
                                             datastore::UniqueStoreAllocator<EntryT, InternalIndex>>;
    using UniqueStoreType = datastore::UniqueStore<EntryT, InternalIndex, ComparatorType, AllocatorType>;
    using FoldedComparatorType = std::conditional_t<std::is_same_v<EntryT, const char *>,
                                                    EnumStoreFoldedStringComparator,
                                                    ComparatorType>;
    using EntryType = EntryT;
    using EnumStoreType = EnumStoreT<EntryT>;
    using EntryRef = datastore::EntryRef;
    using generation_t = vespalib::GenerationHandler::generation_t;

private:
    UniqueStoreType _store;
    IEnumStoreDictionary* _dict;
    vespalib::MemoryUsage _cached_values_memory_usage;
    vespalib::AddressSpace _cached_values_address_space_usage;

    EnumStoreT(const EnumStoreT & rhs) = delete;
    EnumStoreT & operator=(const EnumStoreT & rhs) = delete;

    void free_value_if_unused(Index idx, IndexSet &unused) override;

    const datastore::UniqueStoreEntryBase& get_entry_base(Index idx) const {
        return _store.get_allocator().get_wrapped(idx);
    }

    static bool has_string_type() {
        return std::is_same_v<EntryType, const char *>;
    }

    ssize_t load_unique_values_internal(const void* src, size_t available, IndexVector& idx);
    ssize_t load_unique_value(const void* src, size_t available, Index& idx);

public:
    EnumStoreT(bool has_postings);
    virtual ~EnumStoreT();

    uint32_t getRefCount(Index idx) const { return get_entry_base(idx).get_ref_count(); }
    void incRefCount(Index idx) { return get_entry_base(idx).inc_ref_count(); }

    // Only use when reading from enumerated attribute save files
    void set_ref_count(Index idx, uint32_t ref_count) override {
        get_entry_base(idx).set_ref_count(ref_count);
    }

    uint32_t getNumUniques() const override { return _dict->getNumUniques(); }

    vespalib::MemoryUsage getValuesMemoryUsage() const override { return _store.get_allocator().get_data_store().getMemoryUsage(); }
    vespalib::MemoryUsage getDictionaryMemoryUsage() const override { return _dict->get_memory_usage(); }

    vespalib::AddressSpace getAddressSpaceUsage() const;

    void transferHoldLists(generation_t generation);
    void trimHoldLists(generation_t firstUsed);

    ssize_t load_unique_values(const void* src, size_t available, IndexVector& idx) override;

    void set_ref_counts(const EnumVector& hist) override { _dict->set_ref_counts(hist); }
    void freezeTree() { _store.freeze(); }

    IEnumStoreDictionary &getEnumStoreDict() override { return *_dict; }
    const IEnumStoreDictionary &getEnumStoreDict() const override { return *_dict; }
    EnumPostingTree &getPostingDictionary() { return _dict->getPostingDictionary(); }

    const EnumPostingTree &getPostingDictionary() const {
        return _dict->getPostingDictionary();
    }

    bool getValue(Index idx, EntryType& value) const;
    EntryType getValue(uint32_t idx) const { return getValue(Index(EntryRef(idx))); }
    EntryType getValue(Index idx) const { return _store.get(idx); }

    /**
     * Helper class used to load an enum store from non-enumerated save files.
     */
    class NonEnumeratedLoader {
    private:
        AllocatorType& _allocator;
        datastore::IUniqueStoreDictionary& _dict;
        std::vector<EntryRef> _refs;
        std::vector<uint32_t> _payloads;

    public:
        NonEnumeratedLoader(AllocatorType& allocator, datastore::IUniqueStoreDictionary& dict)
            : _allocator(allocator),
              _dict(dict),
              _refs(),
              _payloads()
        {
        }
        ~NonEnumeratedLoader();
        Index insert(const EntryType& value, uint32_t posting_idx) {
            EntryRef new_ref = _allocator.allocate(value);
            _refs.emplace_back(new_ref);
            _payloads.emplace_back(posting_idx);
            return new_ref;
        }
        void set_ref_count_for_last_value(uint32_t ref_count) {
            assert(!_refs.empty());
            _allocator.get_wrapped(_refs.back()).set_ref_count(ref_count);
        }
        void build_dictionary() {
            _dict.build_with_payload(_refs, _payloads);
        }
    };

    NonEnumeratedLoader make_non_enumerated_loader() {
        return NonEnumeratedLoader(_store.get_allocator(), *_dict);
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
        Index insert(EntryType value);
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
            _store.free_unused_values(_possibly_unused);
        }
    };

    BatchUpdater make_batch_updater() {
        return BatchUpdater(*this);
    }

    ComparatorType make_comparator() const {
        return ComparatorType(_store.get_data_store());
    }

    ComparatorType make_comparator(const EntryType& fallback_value) const {
        return ComparatorType(_store.get_data_store(), fallback_value);
    }

    FoldedComparatorType make_folded_comparator() const {
        return FoldedComparatorType(_store.get_data_store());
    }

    FoldedComparatorType make_folded_comparator(const EntryType& fallback_value, bool prefix = false) const {
        return FoldedComparatorType(_store.get_data_store(), fallback_value, prefix);
    }

    void write_value(BufferWriter& writer, Index idx) const override;
    bool foldedChange(const Index &idx1, const Index &idx2) const override;
    bool findEnum(EntryType value, IEnumStore::EnumHandle &e) const;
    std::vector<IEnumStore::EnumHandle> findFoldedEnums(EntryType value) const;
    Index insert(EntryType value);
    bool findIndex(EntryType value, Index &idx) const;
    void free_unused_values() override;
    void free_unused_values(const IndexSet& to_remove);
    vespalib::MemoryUsage update_stat() override;
    std::unique_ptr<EnumIndexRemapper> consider_compact(const CompactionStrategy& compaction_strategy) override;
    std::unique_ptr<EnumIndexRemapper> compact_worst(bool compact_memory, bool compact_address_space) override;
    uint64_t get_compaction_count() const override {
        return _store.get_data_store().get_compaction_count();
    }
    void inc_compaction_count() override {
        _store.get_allocator().get_data_store().inc_compaction_count();
    }
    std::unique_ptr<Enumerator> make_enumerator() const override;
};

std::unique_ptr<datastore::IUniqueStoreDictionary>
make_enum_store_dictionary(IEnumStore &store, bool has_postings, std::unique_ptr<datastore::EntryComparator> folded_compare);


extern template
class datastore::DataStoreT<IEnumStore::Index>;


template <>
void
EnumStoreT<const char*>::write_value(BufferWriter& writer, Index idx) const;

template <>
ssize_t
EnumStoreT<const char*>::load_unique_value(const void* src,
                                           size_t available,
                                           Index& idx);


extern template
class btree::BTreeBuilder<IEnumStore::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                          EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;
extern template
class btree::BTreeBuilder<IEnumStore::Index, datastore::EntryRef, btree::NoAggregated,
                          EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template class EnumStoreT<const char*>;
extern template class EnumStoreT<int8_t>;
extern template class EnumStoreT<int16_t>;
extern template class EnumStoreT<int32_t>;
extern template class EnumStoreT<int64_t>;
extern template class EnumStoreT<float>;
extern template class EnumStoreT<double>;

} // namespace search

