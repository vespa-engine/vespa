// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enum_store_dictionary.h"
#include "enumstore.h"
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/datastore/datastore.hpp>
#include <vespa/vespalib/datastore/simple_hash_map.h>
#include <vespa/vespalib/datastore/unique_store_dictionary.hpp>
#include <vespa/searchlib/util/bufferwriter.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.enum_store_dictionary");

using vespalib::datastore::EntryComparator;
using vespalib::datastore::EntryRef;
using vespalib::datastore::UniqueStoreAddResult;

namespace search {

using vespalib::btree::BTreeNode;

template <typename DictionaryT, typename UnorderedDictionaryT>
void
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::remove_unused_values(const IndexSet& unused,
                                                       const vespalib::datastore::EntryComparator& cmp)
{
    if (unused.empty()) {
        return;
    }
    for (const auto& ref : unused) {
        this->remove(cmp, ref);
    }
}

template <typename DictionaryT, typename UnorderedDictionaryT>
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::EnumStoreDictionary(IEnumStore& enumStore, std::unique_ptr<EntryComparator> compare)
    : ParentUniqueStoreDictionary(std::move(compare)),
      _enumStore(enumStore)
{
}

template <typename DictionaryT, typename UnorderedDictionaryT>
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::~EnumStoreDictionary() = default;

template <typename DictionaryT, typename UnorderedDictionaryT>
void
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::set_ref_counts(const EnumVector& hist)
{
    _enumStore.set_ref_counts(hist, this->_dict);
}

template <typename DictionaryT, typename UnorderedDictionaryT>
void
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::free_unused_values(const vespalib::datastore::EntryComparator& cmp)
{
    IndexSet unused;

    // find unused enums
    for (auto iter = this->_dict.begin(); iter.valid(); ++iter) {
        _enumStore.free_value_if_unused(iter.getKey(), unused);
    }
    remove_unused_values(unused, cmp);
}

template <typename DictionaryT, typename UnorderedDictionaryT>
void
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::free_unused_values(const IndexSet& to_remove,
                                                     const vespalib::datastore::EntryComparator& cmp)
{
    IndexSet unused;
    for (const auto& index : to_remove) {
        _enumStore.free_value_if_unused(index, unused);
    }
    remove_unused_values(unused, cmp);
}

template <typename DictionaryT, typename UnorderedDictionaryT>
void
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::remove(const EntryComparator &comp, EntryRef ref)
{
    assert(ref.valid());
    auto itr = this->_dict.lowerBound(ref, comp);
    assert(itr.valid() && itr.getKey() == ref);
    if constexpr (std::is_same_v<DictionaryT, EnumPostingTree>) {
        assert(EntryRef(itr.getData()) == EntryRef());
    }
    this->_dict.remove(itr);
}

template <typename DictionaryT, typename UnorderedDictionaryT>
bool
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::find_index(const vespalib::datastore::EntryComparator& cmp,
                                             Index& idx) const
{
    auto itr = this->_dict.find(Index(), cmp);
    if (!itr.valid()) {
        return false;
    }
    idx = itr.getKey();
    return true;
}

template <typename DictionaryT, typename UnorderedDictionaryT>
bool
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::find_frozen_index(const vespalib::datastore::EntryComparator& cmp,
                                                    Index& idx) const
{
    auto itr = this->_dict.getFrozenView().find(Index(), cmp);
    if (!itr.valid()) {
        return false;
    }
    idx = itr.getKey();
    return true;
}

template <typename DictionaryT, typename UnorderedDictionaryT>
std::vector<IEnumStore::EnumHandle>
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::find_matching_enums(const vespalib::datastore::EntryComparator& cmp) const
{
    std::vector<IEnumStore::EnumHandle> result;
    auto itr = this->_dict.getFrozenView().find(Index(), cmp);
    while (itr.valid() && !cmp.less(Index(), itr.getKey())) {
        result.push_back(itr.getKey().ref());
        ++itr;
    }
    return result;
}

template <typename DictionaryT, typename UnorderedDictionaryT>
EntryRef
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::get_frozen_root() const
{
    return this->_dict.getFrozenView().getRoot();
}

template <>
std::pair<IEnumStore::Index, EntryRef>
EnumStoreDictionary<EnumTree>::find_posting_list(const vespalib::datastore::EntryComparator&, EntryRef) const
{
    LOG_ABORT("should not be reached");
}

template <typename DictionaryT, typename UnorderedDictionaryT>
std::pair<IEnumStore::Index, EntryRef>
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::find_posting_list(const vespalib::datastore::EntryComparator& cmp, EntryRef root) const
{
    typename DictionaryType::ConstIterator itr(vespalib::btree::BTreeNode::Ref(), this->_dict.getAllocator());
    itr.lower_bound(root, Index(), cmp);
    if (itr.valid() && !cmp.less(Index(), itr.getKey())) {
        return std::make_pair(itr.getKey(), EntryRef(itr.getData()));
    }
    return std::make_pair(Index(), EntryRef());
}

template <typename DictionaryT, typename UnorderedDictionaryT>
void
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::collect_folded(Index idx, EntryRef, const std::function<void(vespalib::datastore::EntryRef)>& callback) const
{
    callback(idx);
}

template <typename DictionaryT, typename UnorderedDictionaryT>
IEnumStore::Index
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::remap_index(Index idx)
{
    return idx;
}

template <>
void
EnumStoreDictionary<EnumTree>::clear_all_posting_lists(std::function<void(EntryRef)>)
{
    LOG_ABORT("should not be reached");
}

template <typename DictionaryT, typename UnorderedDictionaryT>
void
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::clear_all_posting_lists(std::function<void(EntryRef)> clearer)
{
    auto& dict = this->_dict;
    auto itr = dict.begin();
    EntryRef prev;
    while (itr.valid()) {
        EntryRef ref(itr.getData());
        if (ref.ref() != prev.ref()) {
            if (ref.valid()) {
                clearer(ref);
            }
            prev = ref;
        }
        itr.writeData(EntryRef().ref());
        ++itr;
    }
}

template <>
void
EnumStoreDictionary<EnumTree>::update_posting_list(Index, const vespalib::datastore::EntryComparator&, std::function<EntryRef(EntryRef)>)
{
    LOG_ABORT("should not be reached");
}

template <typename DictionaryT, typename UnorderedDictionaryT>
void
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::update_posting_list(Index idx, const vespalib::datastore::EntryComparator& cmp, std::function<EntryRef(EntryRef)> updater)
{
    auto& dict = this->_dict;
    auto itr = dict.lowerBound(idx, cmp);
    assert(itr.valid() && itr.getKey() == idx);
    EntryRef old_posting_idx(itr.getData());
    EntryRef new_posting_idx = updater(old_posting_idx);
    dict.thaw(itr);
    itr.writeData(new_posting_idx.ref());
}

template <>
EnumPostingTree &
EnumStoreDictionary<EnumTree>::get_posting_dictionary()
{
    LOG_ABORT("should not be reached");
}

template <typename DictionaryT, typename UnorderedDictionaryT>
EnumPostingTree &
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::get_posting_dictionary()
{
    return this->_dict;
}

template <>
const EnumPostingTree &
EnumStoreDictionary<EnumTree>::get_posting_dictionary() const
{
    LOG_ABORT("should not be reached");
}

template <typename DictionaryT, typename UnorderedDictionaryT>
const EnumPostingTree &
EnumStoreDictionary<DictionaryT, UnorderedDictionaryT>::get_posting_dictionary() const
{
    return this->_dict;
}

EnumStoreFoldedDictionary::EnumStoreFoldedDictionary(IEnumStore& enumStore, std::unique_ptr<vespalib::datastore::EntryComparator> compare, std::unique_ptr<EntryComparator> folded_compare)
    : EnumStoreDictionary<EnumPostingTree>(enumStore, std::move(compare)),
      _folded_compare(std::move(folded_compare))
{
}

EnumStoreFoldedDictionary::~EnumStoreFoldedDictionary() = default;

UniqueStoreAddResult
EnumStoreFoldedDictionary::add(const EntryComparator& comp, std::function<EntryRef(void)> insertEntry)
{
    auto it = _dict.lowerBound(EntryRef(), comp);
    if (it.valid() && !comp.less(EntryRef(), it.getKey())) {
        // Entry already exists
        return UniqueStoreAddResult(it.getKey(), false);
    }
    EntryRef newRef = insertEntry();
    _dict.insert(it, newRef, EntryRef().ref());
    // Maybe move posting list reference from next entry
    ++it;
    if (it.valid() && EntryRef(it.getData()).valid() && !_folded_compare->less(newRef, it.getKey())) {
        EntryRef posting_list_ref(it.getData());
        _dict.thaw(it);
        it.writeData(EntryRef().ref());
        --it;
        assert(it.valid() && it.getKey() == newRef);
        it.writeData(posting_list_ref.ref());
    }
    return UniqueStoreAddResult(newRef, true);
}

void
EnumStoreFoldedDictionary::remove(const EntryComparator& comp, EntryRef ref)
{
    assert(ref.valid());
    auto it = _dict.lowerBound(ref, comp);
    assert(it.valid() && it.getKey() == ref);
    EntryRef posting_list_ref(it.getData());
    _dict.remove(it);
    // Maybe copy posting list reference to next entry
    if (posting_list_ref.valid()) {
        if (it.valid() && !EntryRef(it.getData()).valid() && !_folded_compare->less(ref, it.getKey())) {
            this->_dict.thaw(it);
            it.writeData(posting_list_ref.ref());
        } else {
            LOG_ABORT("Posting list not cleared for removed unique value");
        }
    }
}

void
EnumStoreFoldedDictionary::collect_folded(Index idx, EntryRef root, const std::function<void(vespalib::datastore::EntryRef)>& callback) const
{
    DictionaryType::ConstIterator itr(vespalib::btree::BTreeNode::Ref(), _dict.getAllocator());
    itr.lower_bound(root, idx, *_folded_compare);
    while (itr.valid() && !_folded_compare->less(idx, itr.getKey())) {
        callback(itr.getKey());
        ++itr;
    }
}

IEnumStore::Index
EnumStoreFoldedDictionary::remap_index(Index idx)
{
    auto itr = _dict.find(idx, *_folded_compare);
    assert(itr.valid());
    return itr.getKey();
}

template class EnumStoreDictionary<EnumTree>;

template class EnumStoreDictionary<EnumPostingTree>;

template class EnumStoreDictionary<EnumPostingTree, vespalib::datastore::SimpleHashMap>;

}

namespace vespalib::btree {

using search::IEnumStore;
using search::EnumTreeTraits;

template
class BTreeNodeT<IEnumStore::Index, EnumTreeTraits::INTERNAL_SLOTS>;

template
class BTreeNodeTT<IEnumStore::Index, uint32_t, NoAggregated, EnumTreeTraits::INTERNAL_SLOTS>;

template
class BTreeNodeTT<IEnumStore::Index, BTreeNoLeafData, NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

template
class BTreeInternalNode<IEnumStore::Index, NoAggregated, EnumTreeTraits::INTERNAL_SLOTS>;

template
class BTreeLeafNode<IEnumStore::Index, BTreeNoLeafData, NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

template
class BTreeLeafNode<IEnumStore::Index, uint32_t, NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

template
class BTreeLeafNodeTemp<IEnumStore::Index, BTreeNoLeafData, NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

template
class BTreeLeafNodeTemp<IEnumStore::Index, uint32_t, NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

template
class BTreeNodeStore<IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                     EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

template
class BTreeNodeStore<IEnumStore::Index, uint32_t, NoAggregated,
                     EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

template
class BTreeRoot<IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                const vespalib::datastore::EntryComparatorWrapper, EnumTreeTraits>;

template
class BTreeRoot<IEnumStore::Index, uint32_t, NoAggregated,
                const vespalib::datastore::EntryComparatorWrapper, EnumTreeTraits>;

template
class BTreeRootT<IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                 const vespalib::datastore::EntryComparatorWrapper, EnumTreeTraits>;

template
class BTreeRootT<IEnumStore::Index, uint32_t, NoAggregated,
                 const vespalib::datastore::EntryComparatorWrapper, EnumTreeTraits>;

template
class BTreeRootBase<IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                    EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

template
class BTreeRootBase<IEnumStore::Index, uint32_t, NoAggregated,
                    EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

template
class BTreeNodeAllocator<IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                         EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

template
class BTreeNodeAllocator<IEnumStore::Index, uint32_t, NoAggregated,
                         EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

template
class BTreeIteratorBase<IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                        EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS, EnumTreeTraits::PATH_SIZE>;
template
class BTreeIteratorBase<IEnumStore::Index, uint32_t, NoAggregated,
                        EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS, EnumTreeTraits::PATH_SIZE>;

template class BTreeConstIterator<IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                                  const vespalib::datastore::EntryComparatorWrapper, EnumTreeTraits>;

template class BTreeConstIterator<IEnumStore::Index, uint32_t, NoAggregated,
                                  const vespalib::datastore::EntryComparatorWrapper, EnumTreeTraits>;

template
class BTreeIterator<IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                    const vespalib::datastore::EntryComparatorWrapper, EnumTreeTraits>;
template
class BTreeIterator<IEnumStore::Index, uint32_t, NoAggregated,
                    const vespalib::datastore::EntryComparatorWrapper, EnumTreeTraits>;

template
class BTree<IEnumStore::Index, BTreeNoLeafData, NoAggregated,
            const vespalib::datastore::EntryComparatorWrapper, EnumTreeTraits>;
template
class BTree<IEnumStore::Index, uint32_t, NoAggregated,
            const vespalib::datastore::EntryComparatorWrapper, EnumTreeTraits>;

}
