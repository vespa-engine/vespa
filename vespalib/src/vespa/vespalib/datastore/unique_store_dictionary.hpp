// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "datastore.hpp"
#include "entry_comparator_wrapper.h"
#include "i_compactable.h"
#include "unique_store_add_result.h"
#include "unique_store_dictionary.h"
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreebuilder.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>

namespace vespalib::datastore {

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::
ReadSnapshotImpl::ReadSnapshotImpl(FrozenView frozen_view)
    : _frozen_view(frozen_view)
{
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
size_t
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::
ReadSnapshotImpl::count(const EntryComparator& comp) const
{
    auto itr = _frozen_view.lowerBound(EntryRef(), comp);
    if (itr.valid() && !comp.less(EntryRef(), itr.getKey())) {
        return 1u;
    }
    return 0u;
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
size_t
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::
ReadSnapshotImpl::count_in_range(const EntryComparator& low,
                                 const EntryComparator& high) const
{
    auto low_itr = _frozen_view.lowerBound(EntryRef(), low);
    auto high_itr = low_itr;
    if (high_itr.valid() && !high.less(EntryRef(), high_itr.getKey())) {
        high_itr.seekPast(EntryRef(), high);
    }
    return high_itr - low_itr;
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::
ReadSnapshotImpl::foreach_key(std::function<void(EntryRef)> callback) const
{
    _frozen_view.foreach_key(callback);
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::UniqueStoreDictionary(std::unique_ptr<EntryComparator> compare)
    : ParentT(),
      UniqueStoreBTreeDictionaryBase<BTreeDictionaryT>(),
      UniqueStoreHashDictionaryBase<HashDictionaryT>(std::move(compare))
{
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::~UniqueStoreDictionary() = default;

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::freeze()
{
    this->_btree_dict.getAllocator().freeze();
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::transfer_hold_lists(generation_t generation)
{
    this->_btree_dict.getAllocator().transferHoldLists(generation);
    if constexpr (has_hash_dictionary) {
        this->_hash_dict.transfer_hold_lists(generation);
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::trim_hold_lists(generation_t firstUsed)
{
    this->_btree_dict.getAllocator().trimHoldLists(firstUsed);
    if constexpr (has_hash_dictionary) {
        this->_hash_dict.trim_hold_lists(firstUsed);
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
UniqueStoreAddResult
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::add(const EntryComparator &comp,
                                                 std::function<EntryRef(void)> insertEntry)
{
    auto itr = this->_btree_dict.lowerBound(EntryRef(), comp);
    if (itr.valid() && !comp.less(EntryRef(), itr.getKey())) {
        if constexpr (has_hash_dictionary) {
            auto* result = this->_hash_dict.find(comp, EntryRef());
            assert(result != nullptr && result->first.load_relaxed() == itr.getKey());
        }
        return UniqueStoreAddResult(itr.getKey(), false);

    } else {
        EntryRef newRef = insertEntry();
        this->_btree_dict.insert(itr, newRef, DataType());
        if constexpr (has_hash_dictionary) {
            std::function<EntryRef(void)> insert_hash_entry([newRef]() noexcept -> EntryRef { return newRef; });
            auto& add_result = this->_hash_dict.add(comp, newRef, insert_hash_entry);
            assert(add_result.first.load_relaxed() == newRef);
        }
        return UniqueStoreAddResult(newRef, true);
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
EntryRef
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::find(const EntryComparator &comp)
{
    auto itr = this->_btree_dict.lowerBound(EntryRef(), comp);
    if (itr.valid() && !comp.less(EntryRef(), itr.getKey())) {
        if constexpr (has_hash_dictionary) {
            auto* result = this->_hash_dict.find(comp, EntryRef());
            assert(result != nullptr && result->first.load_relaxed() == itr.getKey());
        }
        return itr.getKey();
    } else {
        if constexpr (has_hash_dictionary) {
            auto* result = this->_hash_dict.find(comp, EntryRef());
            assert(result == nullptr);
        }
        return EntryRef();
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::remove(const EntryComparator &comp, EntryRef ref)
{
    assert(ref.valid());
    auto itr = this->_btree_dict.lowerBound(ref, comp);
    assert(itr.valid() && itr.getKey() == ref);
    this->_btree_dict.remove(itr);
    if constexpr (has_hash_dictionary) {
        auto *result = this->_hash_dict.remove(comp, ref);
        assert(result != nullptr && result->first.load_relaxed() == ref);
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::move_entries(ICompactable &compactable)
{
    auto itr = this->_btree_dict.begin();
    while (itr.valid()) {
        EntryRef oldRef(itr.getKey());
        EntryRef newRef(compactable.move(oldRef));
        if (newRef != oldRef) {
            this->_btree_dict.thaw(itr);
            itr.writeKey(newRef);
            if constexpr (has_hash_dictionary) {
                auto result = this->_hash_dict.find(this->_hash_dict.get_default_comparator(), oldRef);
                assert(result != nullptr && result->first.load_relaxed() == oldRef);
                result->first.store_release(newRef);
            }
        }
        ++itr;
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
uint32_t
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::get_num_uniques() const
{
    return this->_btree_dict.size();
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
vespalib::MemoryUsage
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::get_memory_usage() const
{
    return this->_btree_dict.getMemoryUsage();
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::build(vespalib::ConstArrayRef<EntryRef> refs,
                                                   vespalib::ConstArrayRef<uint32_t> ref_counts,
                                                   std::function<void(EntryRef)> hold)
{
    assert(refs.size() == ref_counts.size());
    assert(!refs.empty());
    typename BTreeDictionaryType::Builder builder(this->_btree_dict.getAllocator());
    for (size_t i = 1; i < refs.size(); ++i) {
        if (ref_counts[i] != 0u) {
            builder.insert(refs[i], DataType());
        } else {
            hold(refs[i]);
        }
    }
    this->_btree_dict.assign(builder);
    if constexpr (has_hash_dictionary) {
        for (size_t i = 1; i < refs.size(); ++i) {
            if (ref_counts[i] != 0u) {
                EntryRef ref = refs[i];
                std::function<EntryRef(void)> insert_hash_entry([ref]() noexcept -> EntryRef { return ref; });
                auto& add_result = this->_hash_dict.add(this->_hash_dict.get_default_comparator(), ref, insert_hash_entry);
                assert(add_result.first.load_relaxed() == ref);
            }
        }
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::build(vespalib::ConstArrayRef<EntryRef> refs)
{
    typename BTreeDictionaryType::Builder builder(this->_btree_dict.getAllocator());
    for (const auto& ref : refs) {
        builder.insert(ref, DataType());
    }
    this->_btree_dict.assign(builder);
    if constexpr (has_hash_dictionary) {
        for (const auto& ref : refs) {
            std::function<EntryRef(void)> insert_hash_entry([ref]() noexcept -> EntryRef { return ref; });
            auto& add_result = this->_hash_dict.add(this->_hash_dict.get_default_comparator(), ref, insert_hash_entry);
            assert(add_result.first.load_relaxed() == ref);
        }
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
void
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::build_with_payload(vespalib::ConstArrayRef<EntryRef> refs,
                                                                vespalib::ConstArrayRef<uint32_t> payloads)
{
    assert(refs.size() == payloads.size());
    typename BTreeDictionaryType::Builder builder(this->_btree_dict.getAllocator());
    for (size_t i = 0; i < refs.size(); ++i) {
        if constexpr (std::is_same_v<DataType, uint32_t>) {
            builder.insert(refs[i], payloads[i]);
        } else {
            builder.insert(refs[i], DataType());
        }
    }
    this->_btree_dict.assign(builder);
    if constexpr (has_hash_dictionary) {
        for (size_t i = 0; i < refs.size(); ++i) {
            EntryRef ref = refs[i];
            std::function<EntryRef(void)> insert_hash_entry([ref]() noexcept -> EntryRef { return ref; });
            auto& add_result = this->_hash_dict.add(this->_hash_dict.get_default_comparator(), ref, insert_hash_entry);
            assert(add_result.first.load_relaxed() == refs[i]);
            if constexpr (std::is_same_v<DataType, uint32_t>) {
                add_result.second.store_relaxed(EntryRef(payloads[i]));
            }
        }
    }
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
std::unique_ptr<typename ParentT::ReadSnapshot>
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::get_read_snapshot() const
{
    return std::make_unique<ReadSnapshotImpl>(this->_btree_dict.getFrozenView());
}

template <typename BTreeDictionaryT, typename ParentT, typename HashDictionaryT>
bool
UniqueStoreDictionary<BTreeDictionaryT, ParentT, HashDictionaryT>::get_has_hash_dictionary() const
{
    return has_hash_dictionary;
}

}
