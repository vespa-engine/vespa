// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enum_store_folded_dictionary.h"

#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/datastore/unique_store_dictionary.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.enum_store_folded_dictionary");

using vespalib::datastore::AtomicEntryRef;
using vespalib::datastore::EntryRef;
using vespalib::datastore::UniqueStoreAddResult;

namespace search {

EnumStoreFoldedDictionary::EnumStoreFoldedDictionary(IEnumStore& enumStore, std::unique_ptr<EntryComparator> compare,
                                                     std::unique_ptr<EntryComparator> folded_compare)
    : EnumStoreDictionary<EnumPostingTree>(enumStore, std::move(compare)),
      _folded_compare(std::move(folded_compare)) {
}

EnumStoreFoldedDictionary::~EnumStoreFoldedDictionary() = default;

UniqueStoreAddResult EnumStoreFoldedDictionary::add(const EntryComparator&    comp,
                                                    std::function<EntryRef()> insertEntry) {
    static_assert(!has_hash_dictionary, "Folded Dictionary does not support hash dictionary");
    auto it = _btree_dict.lowerBound(AtomicEntryRef(), comp);
    if (it.valid() && !comp.less(EntryRef(), it.getKey().load_relaxed())) {
        // Entry already exists
        return UniqueStoreAddResult(it.getKey().load_relaxed(), false);
    }
    EntryRef newRef = insertEntry();
    _btree_dict.insert(it, AtomicEntryRef(newRef), AtomicEntryRef());
    // Maybe move posting list reference from next entry
    ++it;
    if (it.valid() && it.getData().load_relaxed().valid() &&
        !_folded_compare->less(newRef, it.getKey().load_relaxed()))
    {
        EntryRef posting_list_ref(it.getData().load_relaxed());
        _btree_dict.thaw(it);
        it.writeData(AtomicEntryRef());
        --it;
        assert(it.valid() && it.getKey().load_relaxed() == newRef);
        it.writeData(AtomicEntryRef(posting_list_ref));
    }
    return UniqueStoreAddResult(newRef, true);
}

void EnumStoreFoldedDictionary::remove(const EntryComparator& comp, EntryRef ref) {
    static_assert(!has_hash_dictionary, "Folded Dictionary does not support hash dictionary");
    assert(ref.valid());
    auto it = _btree_dict.lowerBound(AtomicEntryRef(ref), comp);
    assert(it.valid() && it.getKey().load_relaxed() == ref);
    EntryRef posting_list_ref(it.getData().load_relaxed());
    _btree_dict.remove(it);
    // Maybe copy posting list reference to next entry
    if (posting_list_ref.valid()) {
        if (it.valid() && !it.getData().load_relaxed().valid() &&
            !_folded_compare->less(ref, it.getKey().load_relaxed()))
        {
            this->_btree_dict.thaw(it);
            it.writeData(AtomicEntryRef(posting_list_ref));
        } else {
            LOG_ABORT("Posting list not cleared for removed unique value");
        }
    }
}

void EnumStoreFoldedDictionary::collect_folded(Index idx, EntryRef root,
                                               const std::function<void(EntryRef)>& callback) const {
    BTreeDictionaryType::ConstIterator itr(vespalib::btree::BTreeNode::Ref(), _btree_dict.getAllocator());
    itr.lower_bound(root, AtomicEntryRef(idx), *_folded_compare);
    while (itr.valid() && !_folded_compare->less(idx, itr.getKey().load_acquire())) {
        callback(itr.getKey().load_acquire());
        ++itr;
    }
}

IEnumStore::Index EnumStoreFoldedDictionary::remap_index(Index idx) {
    auto itr = _btree_dict.find(AtomicEntryRef(idx), *_folded_compare);
    assert(itr.valid());
    return itr.getKey().load_acquire();
}

} // namespace search
