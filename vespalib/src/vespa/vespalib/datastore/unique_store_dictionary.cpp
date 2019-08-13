// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "unique_store_dictionary.h"
#include "unique_store_add_result.h"
#include "entry_comparator_wrapper.h"
#include "i_compactable.h"
#include "datastore.hpp"
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreebuilder.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>

namespace search::datastore {

UniqueStoreDictionary::UniqueStoreDictionary()
    : UniqueStoreDictionaryBase(),
      _dict()
{
}

UniqueStoreDictionary::~UniqueStoreDictionary() = default;

void
UniqueStoreDictionary::freeze()
{
    _dict.getAllocator().freeze();
}

void
UniqueStoreDictionary::transfer_hold_lists(generation_t generation)
{
    _dict.getAllocator().transferHoldLists(generation);
}
    
void
UniqueStoreDictionary::trim_hold_lists(generation_t firstUsed)
{
    _dict.getAllocator().trimHoldLists(firstUsed);
}

UniqueStoreAddResult
UniqueStoreDictionary::add(const EntryComparator &comp,
                           std::function<EntryRef(void)> insertEntry)
{
    auto itr = _dict.lowerBound(EntryRef(), comp);
    if (itr.valid() && !comp(EntryRef(), itr.getKey())) {
        return UniqueStoreAddResult(itr.getKey(), false);

    } else {
        EntryRef newRef = insertEntry();
        _dict.insert(itr, newRef, btree::BTreeNoLeafData());
        return UniqueStoreAddResult(newRef, true);
    }
}

EntryRef
UniqueStoreDictionary::find(const EntryComparator &comp)
{
    auto itr = _dict.lowerBound(EntryRef(), comp);
    if (itr.valid() && !comp(EntryRef(), itr.getKey())) {
        return itr.getKey();
    } else {
        return EntryRef();
    }
}

bool
UniqueStoreDictionary::remove(const EntryComparator &comp, EntryRef ref)
{
    assert(ref.valid());
    auto itr = _dict.lowerBound(ref, comp);
    if (itr.valid() && itr.getKey() == ref) {
        _dict.remove(itr);
        return true;
    }
    return false;
}

void
UniqueStoreDictionary::move_entries(ICompactable &compactable)
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

uint32_t
UniqueStoreDictionary::get_num_uniques() const
{
    return _dict.getFrozenView().size();
}

vespalib::MemoryUsage
UniqueStoreDictionary::get_memory_usage() const
{
    return _dict.getMemoryUsage();
}

void
UniqueStoreDictionary::build(const std::vector<EntryRef> &refs, const std::vector<uint32_t> &ref_counts, std::function<void(EntryRef)> hold)
{
    assert(refs.size() == ref_counts.size());
    assert(!refs.empty());
    typename Dictionary::Builder builder(_dict.getAllocator());
    for (size_t i = 1; i < refs.size(); ++i) {
        if (ref_counts[i] != 0u) {
            builder.insert(refs[i], btree::BTreeNoLeafData());
        } else {
            hold(refs[i]);
        }
    }
    _dict.assign(builder);
}

EntryRef
UniqueStoreDictionary::get_frozen_root() const
{
    return _dict.getFrozenView().getRoot();
}

void
UniqueStoreDictionary::foreach_key(EntryRef root, std::function<void(EntryRef)> callback) const
{
    
    _dict.getAllocator().getNodeStore().foreach_key(root, callback);
}

}
