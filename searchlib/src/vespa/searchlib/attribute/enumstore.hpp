// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enumstore.h"
#include "enumcomparator.h"

#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/hdr_abort.h>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodestore.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreebuilder.hpp>
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/datastore/unique_store.hpp>
#include <vespa/vespalib/datastore/unique_store_string_allocator.hpp>
#include <vespa/vespalib/util/array.hpp>
#include <vespa/searchlib/util/bufferwriter.h>
#include <vespa/vespalib/datastore/compaction_strategy.h>

namespace search {

using vespalib::datastore::CompactionStrategy;
using vespalib::datastore::EntryComparator;

std::unique_ptr<vespalib::datastore::IUniqueStoreDictionary>
make_enum_store_dictionary(IEnumStore &store, bool has_postings, const search::DictionaryConfig & dict_cfg,
                           std::unique_ptr<EntryComparator> compare,
                           std::unique_ptr<EntryComparator> folded_compare);

template <typename EntryT>
void EnumStoreT<EntryT>::free_value_if_unused(Index idx, IndexList& unused)
{
    const auto& entry = get_entry_base(idx);
    if (entry.get_ref_count() == 0) {
        unused.push_back(idx);
        _store.get_allocator().hold(idx);
    }
}

template <typename EntryT>
ssize_t
EnumStoreT<EntryT>::load_unique_values_internal(const void* src, size_t available, IndexVector& idx)
{
    size_t left = available;
    const char* p = static_cast<const char*>(src);
    Index idx1;
    while (left > 0) {
        ssize_t sz = load_unique_value(p, left, idx1);
        if (sz < 0) {
            return sz;
        }
        p += sz;
        left -= sz;
        idx.push_back(idx1);
    }
    return available - left;
}

template <class EntryT>
ssize_t
EnumStoreT<EntryT>::load_unique_value(const void* src, size_t available, Index& idx)
{
    if (available < sizeof(EntryType)) {
        return -1;
    }
    const auto* value = static_cast<const EntryType*>(src);
    idx = _store.get_allocator().allocate(*value);
    return sizeof(EntryType);
}

template <typename EntryT>
EnumStoreT<EntryT>::EnumStoreT(bool has_postings, const DictionaryConfig& dict_cfg, std::shared_ptr<vespalib::alloc::MemoryAllocator> memory_allocator)
    : _store(std::move(memory_allocator)),
      _dict(),
      _is_folded(dict_cfg.getMatch() == DictionaryConfig::Match::UNCASED),
      _comparator(_store.get_data_store()),
      _foldedComparator(make_optionally_folded_comparator(is_folded())),
      _compaction_spec()
{
    _store.set_dictionary(make_enum_store_dictionary(*this, has_postings, dict_cfg,
                                                     allocate_comparator(),
                                                     allocate_optionally_folded_comparator(is_folded())));
    _dict = static_cast<IEnumStoreDictionary*>(&_store.get_dictionary());
}

template <typename EntryT>
EnumStoreT<EntryT>::EnumStoreT(bool has_postings, const DictionaryConfig& dict_cfg)
    : EnumStoreT<EntryT>(has_postings, dict_cfg, {})
{
}

template <typename EntryT>
EnumStoreT<EntryT>::~EnumStoreT() = default;

template <typename EntryT>
vespalib::AddressSpace
EnumStoreT<EntryT>::get_values_address_space_usage() const
{
    return _store.get_values_address_space_usage();
}

template <typename EntryT>
void
EnumStoreT<EntryT>::assign_generation(generation_t current_gen)
{
    _store.assign_generation(current_gen);
}

template <typename EntryT>
void
EnumStoreT<EntryT>::reclaim_memory(generation_t oldest_used_gen)
{
    // remove generations in the range [0, firstUsed>
    _store.reclaim_memory(oldest_used_gen);
}

template <typename EntryT>
ssize_t
EnumStoreT<EntryT>::load_unique_values(const void* src, size_t available, IndexVector& idx)
{
    ssize_t sz = load_unique_values_internal(src, available, idx);
    return sz;
}

template <typename EntryT>
bool
EnumStoreT<EntryT>::get_value(Index idx, EntryT& value) const
{
    if (!idx.valid()) {
        return false;
    }
    value = _store.get(idx);
    return true;
}

template <typename EntryT>
EnumStoreT<EntryT>::NonEnumeratedLoader::~NonEnumeratedLoader() = default;

template <typename EntryT>
IEnumStore::Index
EnumStoreT<EntryT>::BatchUpdater::insert(EntryType value)
{
    auto cmp = _store.make_comparator(value);
    auto result = _store._dict->add(cmp, [this, &value]() -> EntryRef { return _store._store.get_allocator().allocate(value); });
    if (result.inserted()) {
        _possibly_unused.push_back(result.ref());
    }
    return result.ref();
}

template <class EntryT>
void
EnumStoreT<EntryT>::write_value(BufferWriter& writer, Index idx) const
{
    writer.write(&_store.get(idx), sizeof(EntryType));
}

template <class EntryT>
bool
EnumStoreT<EntryT>::is_folded_change(Index idx1, Index idx2) const
{
    const auto & cmp = get_folded_comparator();
    assert(!cmp.less(idx2, idx1));
    return cmp.less(idx1, idx2);
}

template <typename EntryT>
bool
EnumStoreT<EntryT>::find_enum(EntryType value, IEnumStore::EnumHandle& e) const
{
    auto cmp = make_comparator(value);
    Index idx;
    if (_dict->find_frozen_index(cmp, idx)) {
        e = idx.ref();
        return true;
    }
    return false;
}

template <typename EntryT>
bool
EnumStoreT<EntryT>::find_index(EntryType value, Index& idx) const
{
    auto cmp = make_comparator(value);
    return _dict->find_index(cmp, idx);
}

template <typename EntryT>
void
EnumStoreT<EntryT>::free_unused_values()
{
    _dict->free_unused_values(get_comparator());
}

template <typename EntryT>
void
EnumStoreT<EntryT>::free_unused_values(IndexList to_remove)
{
    struct CompareEnumIndex {
        bool operator()(const Index &lhs, const Index &rhs) const {
            return lhs.ref() < rhs.ref();
        }
    };
    std::sort(to_remove.begin(), to_remove.end(), CompareEnumIndex());
    _dict->free_unused_values(to_remove, get_comparator());
}

template <typename EntryT>
IEnumStore::Index
EnumStoreT<EntryT>::insert(EntryType value)
{
    return _store.add(value).ref();
}

template <typename EntryT>
vespalib::MemoryUsage
EnumStoreT<EntryT>::update_stat(const CompactionStrategy& compaction_strategy)
{
    return _compaction_spec.update_stat(*this, compaction_strategy);
}

template <typename EntryT>
std::unique_ptr<IEnumStore::EnumIndexRemapper>
EnumStoreT<EntryT>::consider_compact_values(const CompactionStrategy& compaction_strategy)
{
    if (!_store.get_data_store().has_held_buffers() && _compaction_spec.get_values().compact()) {
        return compact_worst_values(_compaction_spec.get_values(), compaction_strategy);
    }
    return std::unique_ptr<IEnumStore::EnumIndexRemapper>();
}

template <typename EntryT>
std::unique_ptr<IEnumStore::EnumIndexRemapper>
EnumStoreT<EntryT>::compact_worst_values(CompactionSpec compaction_spec, const CompactionStrategy& compaction_strategy)
{
    return _store.compact_worst(compaction_spec, compaction_strategy);
}

template <typename EntryT>
bool
EnumStoreT<EntryT>::consider_compact_dictionary(const CompactionStrategy& compaction_strategy)
{
    if (_dict->has_held_buffers()) {
        return false;
    }
    if (_compaction_spec.btree_dictionary()) {
        _dict->compact_worst(true, false, compaction_strategy);
        return true;
    }
    if (_compaction_spec.hash_dictionary()) {
        _dict->compact_worst(false, true, compaction_strategy);
        return true;
    }
    return false;
}

template <typename EntryT>
std::unique_ptr<IEnumStore::Enumerator>
EnumStoreT<EntryT>::make_enumerator() const
{
    return std::make_unique<Enumerator>(*_dict, _store.get_data_store(), false);
}

template <typename EntryT>
std::unique_ptr<EntryComparator>
EnumStoreT<EntryT>::allocate_comparator() const
{
    return std::make_unique<ComparatorType>(_store.get_data_store());
}

template <typename EntryT>
std::unique_ptr<EntryComparator>
EnumStoreT<EntryT>::allocate_optionally_folded_comparator(bool folded) const
{
    return (has_string_type() && folded)
            ? std::make_unique<ComparatorType>(_store.get_data_store(), true)
            : std::unique_ptr<EntryComparator>();
}

template <typename EntryT>
typename EnumStoreT<EntryT>::ComparatorType
EnumStoreT<EntryT>::make_optionally_folded_comparator(bool folded) const
{
    return (has_string_type() && folded)
           ? ComparatorType(_store.get_data_store(), true)
           : ComparatorType(_store.get_data_store());
}

}
