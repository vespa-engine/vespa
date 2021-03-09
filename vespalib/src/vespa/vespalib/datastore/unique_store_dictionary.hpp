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

template <typename DictionaryT, typename ParentT>
UniqueStoreDictionary<DictionaryT, ParentT>::
ReadSnapshotImpl::ReadSnapshotImpl(FrozenView frozen_view)
    : _frozen_view(frozen_view)
{
}

template <typename DictionaryT, typename ParentT>
size_t
UniqueStoreDictionary<DictionaryT, ParentT>::
ReadSnapshotImpl::count(const EntryComparator& comp) const
{
    auto itr = _frozen_view.lowerBound(EntryRef(), comp);
    if (itr.valid() && !comp.less(EntryRef(), itr.getKey())) {
        return 1u;
    }
    return 0u;
}

template <typename DictionaryT, typename ParentT>
size_t
UniqueStoreDictionary<DictionaryT, ParentT>::
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

template <typename DictionaryT, typename ParentT>
void
UniqueStoreDictionary<DictionaryT, ParentT>::
ReadSnapshotImpl::foreach_key(std::function<void(EntryRef)> callback) const
{
    _frozen_view.foreach_key(callback);
}

template <typename DictionaryT, typename ParentT>
UniqueStoreDictionary<DictionaryT, ParentT>::UniqueStoreDictionary()
    : ParentT(),
      _dict()
{
}

template <typename DictionaryT, typename ParentT>
UniqueStoreDictionary<DictionaryT, ParentT>::~UniqueStoreDictionary() = default;

template <typename DictionaryT, typename ParentT>
void
UniqueStoreDictionary<DictionaryT, ParentT>::freeze()
{
    _dict.getAllocator().freeze();
}

template <typename DictionaryT, typename ParentT>
void
UniqueStoreDictionary<DictionaryT, ParentT>::transfer_hold_lists(generation_t generation)
{
    _dict.getAllocator().transferHoldLists(generation);
}

template <typename DictionaryT, typename ParentT>
void
UniqueStoreDictionary<DictionaryT, ParentT>::trim_hold_lists(generation_t firstUsed)
{
    _dict.getAllocator().trimHoldLists(firstUsed);
}

template <typename DictionaryT, typename ParentT>
UniqueStoreAddResult
UniqueStoreDictionary<DictionaryT, ParentT>::add(const EntryComparator &comp,
                                                 std::function<EntryRef(void)> insertEntry)
{
    auto itr = _dict.lowerBound(EntryRef(), comp);
    if (itr.valid() && !comp.less(EntryRef(), itr.getKey())) {
        return UniqueStoreAddResult(itr.getKey(), false);

    } else {
        EntryRef newRef = insertEntry();
        _dict.insert(itr, newRef, DataType());
        return UniqueStoreAddResult(newRef, true);
    }
}

template <typename DictionaryT, typename ParentT>
EntryRef
UniqueStoreDictionary<DictionaryT, ParentT>::find(const EntryComparator &comp)
{
    auto itr = _dict.lowerBound(EntryRef(), comp);
    if (itr.valid() && !comp.less(EntryRef(), itr.getKey())) {
        return itr.getKey();
    } else {
        return EntryRef();
    }
}

template <typename DictionaryT, typename ParentT>
void
UniqueStoreDictionary<DictionaryT, ParentT>::remove(const EntryComparator &comp, EntryRef ref)
{
    assert(ref.valid());
    auto itr = _dict.lowerBound(ref, comp);
    assert(itr.valid() && itr.getKey() == ref);
    _dict.remove(itr);
}

template <typename DictionaryT, typename ParentT>
void
UniqueStoreDictionary<DictionaryT, ParentT>::move_entries(ICompactable &compactable)
{
    auto itr = _dict.begin();
    while (itr.valid()) {
        EntryRef oldRef(itr.getKey());
        EntryRef newRef(compactable.move(oldRef));
        if (newRef != oldRef) {
            _dict.thaw(itr);
            itr.writeKey(newRef);
        }
        ++itr;
    }
}

template <typename DictionaryT, typename ParentT>
uint32_t
UniqueStoreDictionary<DictionaryT, ParentT>::get_num_uniques() const
{
    return _dict.size();
}

template <typename DictionaryT, typename ParentT>
vespalib::MemoryUsage
UniqueStoreDictionary<DictionaryT, ParentT>::get_memory_usage() const
{
    return _dict.getMemoryUsage();
}

template <typename DictionaryT, typename ParentT>
void
UniqueStoreDictionary<DictionaryT, ParentT>::build(vespalib::ConstArrayRef<EntryRef> refs,
                                                   vespalib::ConstArrayRef<uint32_t> ref_counts,
                                                   std::function<void(EntryRef)> hold)
{
    assert(refs.size() == ref_counts.size());
    assert(!refs.empty());
    typename DictionaryType::Builder builder(_dict.getAllocator());
    for (size_t i = 1; i < refs.size(); ++i) {
        if (ref_counts[i] != 0u) {
            builder.insert(refs[i], DataType());
        } else {
            hold(refs[i]);
        }
    }
    _dict.assign(builder);
}

template <typename DictionaryT, typename ParentT>
void
UniqueStoreDictionary<DictionaryT, ParentT>::build(vespalib::ConstArrayRef<EntryRef> refs)
{
    typename DictionaryType::Builder builder(_dict.getAllocator());
    for (const auto& ref : refs) {
        builder.insert(ref, DataType());
    }
    _dict.assign(builder);
}

template <typename DictionaryT, typename ParentT>
void
UniqueStoreDictionary<DictionaryT, ParentT>::build_with_payload(vespalib::ConstArrayRef<EntryRef> refs,
                                                                vespalib::ConstArrayRef<uint32_t> payloads)
{
    assert(refs.size() == payloads.size());
    typename DictionaryType::Builder builder(_dict.getAllocator());
    for (size_t i = 0; i < refs.size(); ++i) {
        if constexpr (std::is_same_v<DataType, uint32_t>) {
            builder.insert(refs[i], payloads[i]);
        } else {
            builder.insert(refs[i], DataType());
        }
    }
    _dict.assign(builder);
}

template <typename DictionaryT, typename ParentT>
std::unique_ptr<typename ParentT::ReadSnapshot>
UniqueStoreDictionary<DictionaryT, ParentT>::get_read_snapshot() const
{
    return std::make_unique<ReadSnapshotImpl>(_dict.getFrozenView());
}

}
