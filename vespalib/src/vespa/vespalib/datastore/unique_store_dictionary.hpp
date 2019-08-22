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

namespace search::datastore {

template <typename DictionaryType>
UniqueStoreDictionary<DictionaryType>::UniqueStoreDictionary()
    : UniqueStoreDictionaryBase(),
      _dict()
{
}

template <typename DictionaryType>
UniqueStoreDictionary<DictionaryType>::~UniqueStoreDictionary() = default;

template <typename DictionaryType>
void
UniqueStoreDictionary<DictionaryType>::freeze()
{
    _dict.getAllocator().freeze();
}

template <typename DictionaryType>
void
UniqueStoreDictionary<DictionaryType>::transfer_hold_lists(generation_t generation)
{
    _dict.getAllocator().transferHoldLists(generation);
}

template <typename DictionaryType>
void
UniqueStoreDictionary<DictionaryType>::trim_hold_lists(generation_t firstUsed)
{
    _dict.getAllocator().trimHoldLists(firstUsed);
}

template <typename DictionaryType>
UniqueStoreAddResult
UniqueStoreDictionary<DictionaryType>::add(const EntryComparator &comp,
                                           std::function<EntryRef(void)> insertEntry)
{
    auto itr = _dict.lowerBound(EntryRef(), comp);
    if (itr.valid() && !comp(EntryRef(), itr.getKey())) {
        return UniqueStoreAddResult(itr.getKey(), false);

    } else {
        EntryRef newRef = insertEntry();
        _dict.insert(itr, newRef, DataType());
        return UniqueStoreAddResult(newRef, true);
    }
}

template <typename DictionaryType>
EntryRef
UniqueStoreDictionary<DictionaryType>::find(const EntryComparator &comp)
{
    auto itr = _dict.lowerBound(EntryRef(), comp);
    if (itr.valid() && !comp(EntryRef(), itr.getKey())) {
        return itr.getKey();
    } else {
        return EntryRef();
    }
}

template <typename DictionaryType>
void
UniqueStoreDictionary<DictionaryType>::remove(const EntryComparator &comp, EntryRef ref)
{
    assert(ref.valid());
    auto itr = _dict.lowerBound(ref, comp);
    assert(itr.valid() && itr.getKey() == ref);
    _dict.remove(itr);
}

template <typename DictionaryType>
void
UniqueStoreDictionary<DictionaryType>::move_entries(ICompactable &compactable)
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

template <typename DictionaryType>
uint32_t
UniqueStoreDictionary<DictionaryType>::get_num_uniques() const
{
    return _dict.getFrozenView().size();
}

template <typename DictionaryType>
vespalib::MemoryUsage
UniqueStoreDictionary<DictionaryType>::get_memory_usage() const
{
    return _dict.getMemoryUsage();
}

template <typename DictionaryType>
void
UniqueStoreDictionary<DictionaryType>::build(const std::vector<EntryRef> &refs,
                                             const std::vector<uint32_t> &ref_counts,
                                             std::function<void(EntryRef)> hold)
{
    assert(refs.size() == ref_counts.size());
    assert(!refs.empty());
    typename Dictionary::Builder builder(_dict.getAllocator());
    for (size_t i = 1; i < refs.size(); ++i) {
        if (ref_counts[i] != 0u) {
            builder.insert(refs[i], DataType());
        } else {
            hold(refs[i]);
        }
    }
    _dict.assign(builder);
}

template <typename DictionaryType>
EntryRef
UniqueStoreDictionary<DictionaryType>::get_frozen_root() const
{
    return _dict.getFrozenView().getRoot();
}

template <typename DictionaryType>
void
UniqueStoreDictionary<DictionaryType>::foreach_key(EntryRef root, std::function<void(EntryRef)> callback) const
{
    
    _dict.getAllocator().getNodeStore().foreach_key(root, callback);
}

}
