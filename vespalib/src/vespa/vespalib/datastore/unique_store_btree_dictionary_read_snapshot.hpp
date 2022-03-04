// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store_btree_dictionary_read_snapshot.h"

namespace vespalib::datastore {

template <typename BTreeDictionaryT>
UniqueStoreBTreeDictionaryReadSnapshot<BTreeDictionaryT>::UniqueStoreBTreeDictionaryReadSnapshot(FrozenView frozen_view)
    : _frozen_view(frozen_view)
{
}

template <typename BTreeDictionaryT>
void
UniqueStoreBTreeDictionaryReadSnapshot<BTreeDictionaryT>::fill()
{
}

template <typename BTreeDictionaryT>
void
UniqueStoreBTreeDictionaryReadSnapshot<BTreeDictionaryT>::sort()
{
}

template <typename BTreeDictionaryT>
size_t
UniqueStoreBTreeDictionaryReadSnapshot<BTreeDictionaryT>::count(const EntryComparator& comp) const
{
    auto itr = _frozen_view.lowerBound(AtomicEntryRef(), comp);
    if (itr.valid() && !comp.less(EntryRef(), itr.getKey().load_acquire())) {
        return 1u;
    }
    return 0u;
}

template <typename BTreeDictionaryT>
size_t
UniqueStoreBTreeDictionaryReadSnapshot<BTreeDictionaryT>::count_in_range(const EntryComparator& low, const EntryComparator& high) const
{
    auto low_itr = _frozen_view.lowerBound(AtomicEntryRef(), low);
    auto high_itr = low_itr;
    if (high_itr.valid() && !high.less(EntryRef(), high_itr.getKey().load_acquire())) {
        high_itr.seekPast(AtomicEntryRef(), high);
    }
    return high_itr - low_itr;
}

template <typename BTreeDictionaryT>
void
UniqueStoreBTreeDictionaryReadSnapshot<BTreeDictionaryT>::foreach_key(std::function<void(const AtomicEntryRef&)> callback) const
{
    _frozen_view.foreach_key(callback);
}

}
