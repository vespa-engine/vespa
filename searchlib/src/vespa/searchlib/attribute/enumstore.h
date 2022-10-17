// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enum_store_compaction_spec.h"
#include "enum_store_dictionary.h"
#include "enumcomparator.h"
#include "i_enum_store.h"
#include "loadedenumvalue.h"
#include <vespa/searchcommon/common/dictionary_config.h>
#include <vespa/vespalib/btree/btreenode.h>
#include <vespa/vespalib/btree/btreenodeallocator.h>
#include <vespa/vespalib/btree/btree.h>
#include <vespa/vespalib/btree/btreebuilder.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/datastore/unique_store.h>
#include <vespa/vespalib/datastore/unique_store_string_allocator.h>
#include <vespa/vespalib/util/buffer.h>
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/stllike/allocator.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cmath>

namespace search {

/**
 * Class storing and providing access to all unique values stored in an enumerated attribute vector.
 *
 * It uses an instance of vespalib::datastore::UniqueStore to store the actual values.
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
                                             vespalib::datastore::UniqueStoreStringAllocator<InternalIndex>,
                                             vespalib::datastore::UniqueStoreAllocator<EntryT, InternalIndex>>;
    using UniqueStoreType = vespalib::datastore::UniqueStore<EntryT, InternalIndex, ComparatorType, AllocatorType>;

    using EntryType = EntryT;
    using EnumStoreType = EnumStoreT<EntryT>;
    using EntryRef = vespalib::datastore::EntryRef;
    using EntryComparator = vespalib::datastore::EntryComparator;
    using generation_t = vespalib::GenerationHandler::generation_t;

private:
    UniqueStoreType        _store;
    IEnumStoreDictionary*  _dict;
    bool                   _is_folded;
    ComparatorType         _comparator;
    ComparatorType         _foldedComparator;
    enumstore::EnumStoreCompactionSpec _compaction_spec;

    EnumStoreT(const EnumStoreT & rhs) = delete;
    EnumStoreT & operator=(const EnumStoreT & rhs) = delete;

    void free_value_if_unused(Index idx, IndexList &unused) override;

    const vespalib::datastore::UniqueStoreEntryBase& get_entry_base(Index idx) const {
        return _store.get_allocator().get_wrapped(idx);
    }

    static bool has_string_type() {
        return std::is_same_v<EntryType, const char *>;
    }

    ssize_t load_unique_values_internal(const void* src, size_t available, IndexVector& idx);
    ssize_t load_unique_value(const void* src, size_t available, Index& idx);

    std::unique_ptr<EntryComparator> allocate_optionally_folded_comparator(bool folded) const;
    ComparatorType make_optionally_folded_comparator(bool folded) const;
public:
    EnumStoreT(bool has_postings, const search::DictionaryConfig& dict_cfg, std::shared_ptr<vespalib::alloc::MemoryAllocator> memory_allocator);
    EnumStoreT(bool has_postings, const search::DictionaryConfig & dict_cfg);
    ~EnumStoreT() override;

    uint32_t get_ref_count(Index idx) const { return get_entry_base(idx).get_ref_count(); }
    void inc_ref_count(Index idx) { return get_entry_base(idx).inc_ref_count(); }

    // Only use when reading from enumerated attribute save files
    void set_ref_count(Index idx, uint32_t ref_count) override {
        get_entry_base(idx).set_ref_count(ref_count);
    }

    uint32_t get_num_uniques() const override { return _dict->get_num_uniques(); }
    bool is_folded() const { return _is_folded;}

    vespalib::MemoryUsage get_values_memory_usage() const override { return _store.get_allocator().get_data_store().getMemoryUsage(); }
    vespalib::MemoryUsage get_dictionary_memory_usage() const override { return _dict->get_memory_usage(); }

    vespalib::AddressSpace get_values_address_space_usage() const override;

    void assign_generation(generation_t current_gen);
    void reclaim_memory(generation_t first_used);

    ssize_t load_unique_values(const void* src, size_t available, IndexVector& idx) override;

    void freeze_dictionary() { _store.freeze(); }

    IEnumStoreDictionary& get_dictionary() override { return *_dict; }
    const IEnumStoreDictionary& get_dictionary() const override { return *_dict; }

    bool get_value(Index idx, EntryType& value) const;
    EntryType get_value(uint32_t idx) const { return get_value(Index(EntryRef(idx))); }
    EntryType get_value(Index idx) const { return _store.get(idx); }

    /**
     * Helper class used to load an enum store from non-enumerated save files.
     */
    class NonEnumeratedLoader {
    private:
        AllocatorType& _allocator;
        vespalib::datastore::IUniqueStoreDictionary& _dict;
        std::vector<EntryRef, vespalib::allocator_large<EntryRef>> _refs;
        std::vector<EntryRef, vespalib::allocator_large<EntryRef>> _payloads;

    public:
        NonEnumeratedLoader(AllocatorType& allocator, vespalib::datastore::IUniqueStoreDictionary& dict)
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
        IndexList _possibly_unused;

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
                _possibly_unused.push_back(idx);
            }
        }
        void commit() {
            _store.free_unused_values(std::move(_possibly_unused));
        }
    };

    BatchUpdater make_batch_updater() {
        return BatchUpdater(*this);
    }

    const EntryComparator & get_comparator() const {
        return _comparator;
    }

    ComparatorType make_comparator(const EntryType& fallback_value) const {
        return ComparatorType(_store.get_data_store(), fallback_value);
    }

    const EntryComparator & get_folded_comparator() const {
        return _foldedComparator;
    }

    void write_value(BufferWriter& writer, Index idx) const override;
    bool is_folded_change(Index idx1, Index idx2) const override;
    bool find_enum(EntryType value, IEnumStore::EnumHandle& e) const;
    Index insert(EntryType value);
    bool find_index(EntryType value, Index& idx) const;
    void free_unused_values() override;
    void free_unused_values(IndexList to_remove);
    vespalib::MemoryUsage update_stat(const CompactionStrategy& compaction_strategy) override;
    std::unique_ptr<EnumIndexRemapper> consider_compact_values(const CompactionStrategy& compaction_strategy) override;
    std::unique_ptr<EnumIndexRemapper> compact_worst_values(CompactionSpec compaction_spec, const CompactionStrategy& compaction_strategy) override;
    bool consider_compact_dictionary(const CompactionStrategy& compaction_strategy) override;
    uint64_t get_compaction_count() const override {
        return _store.get_data_store().get_compaction_count();
    }
    void inc_compaction_count() override {
        _store.get_allocator().get_data_store().inc_compaction_count();
    }
    std::unique_ptr<Enumerator> make_enumerator() const override;
    std::unique_ptr<EntryComparator> allocate_comparator() const override;

    // Methods below are only relevant for strings, and are templated to only be instantiated on demand.
    template <typename Type>
    ComparatorType
    make_folded_comparator(const Type& fallback_value) const {
        return ComparatorType(_store.get_data_store(), is_folded(), fallback_value);
    }
    template<typename Type>
    ComparatorType
    make_folded_comparator_prefix(const Type& fallback_value) const {
        return ComparatorType(_store.get_data_store(), is_folded(), fallback_value, true);
    }
    template<typename Type>
    std::vector<IEnumStore::EnumHandle>
    find_folded_enums(Type value) const {
        auto cmp = make_folded_comparator(value);
        return _dict->find_matching_enums(cmp);
    }
};

template <>
void
EnumStoreT<const char*>::write_value(BufferWriter& writer, Index idx) const;

template <>
ssize_t
EnumStoreT<const char*>::load_unique_value(const void* src, size_t available, Index& idx);

}

namespace vespalib::datastore {

extern template
class DataStoreT<search::IEnumStore::Index>;

}

namespace vespalib::btree {

extern template
class BTreeBuilder<search::IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                   search::EnumTreeTraits::INTERNAL_SLOTS, search::EnumTreeTraits::LEAF_SLOTS>;
extern template
class BTreeBuilder<search::IEnumStore::Index, vespalib::datastore::EntryRef, NoAggregated,
                   search::EnumTreeTraits::INTERNAL_SLOTS, search::EnumTreeTraits::LEAF_SLOTS>;

}

namespace search {

extern template class EnumStoreT<const char*>;
extern template class EnumStoreT<int8_t>;
extern template class EnumStoreT<int16_t>;
extern template class EnumStoreT<int32_t>;
extern template class EnumStoreT<int64_t>;
extern template class EnumStoreT<float>;
extern template class EnumStoreT<double>;

} // namespace search

