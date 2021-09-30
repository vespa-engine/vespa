// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enum_store_dictionary.h"
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/datastore/sharded_hash_map.h>
#include <vespa/vespalib/datastore/unique_store_dictionary.hpp>
#include <vespa/searchlib/util/bufferwriter.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.enum_store_dictionary");

using vespalib::datastore::EntryRef;
using vespalib::datastore::UniqueStoreAddResult;

namespace search {

using vespalib::btree::BTreeNode;

template <typename BTreeDictionaryT, typename HashDictionaryT>
void
EnumStoreDictionary<BTreeDictionaryT, HashDictionaryT>::remove_unused_values(const IndexList & unused,const EntryComparator& cmp)
{
    for (const auto& ref : unused) {
        this->remove(cmp, ref);
    }
}

template <typename BTreeDictionaryT, typename HashDictionaryT>
EnumStoreDictionary<BTreeDictionaryT, HashDictionaryT>::EnumStoreDictionary(IEnumStore& enumStore, std::unique_ptr<EntryComparator> compare)
    : ParentUniqueStoreDictionary(std::move(compare)),
      _enumStore(enumStore)
{
}

template <typename BTreeDictionaryT, typename HashDictionaryT>
EnumStoreDictionary<BTreeDictionaryT, HashDictionaryT>::~EnumStoreDictionary() = default;

template <typename BTreeDictionaryT, typename HashDictionaryT>
void
EnumStoreDictionary<BTreeDictionaryT, HashDictionaryT>::free_unused_values(const EntryComparator& cmp)
{
    IndexList unused;

    // find unused enums
    if constexpr (has_btree_dictionary) {
        for (auto iter = this->_btree_dict.begin(); iter.valid(); ++iter) {
            _enumStore.free_value_if_unused(iter.getKey(), unused);
        }
    } else {
        this->_hash_dict.foreach_key([this, &unused](EntryRef ref) {
            _enumStore.free_value_if_unused(ref, unused);
        });
    }
    remove_unused_values(unused, cmp);
}

template <typename BTreeDictionaryT, typename HashDictionaryT>
void
EnumStoreDictionary<BTreeDictionaryT, HashDictionaryT>::free_unused_values(const IndexList& to_remove, const EntryComparator& cmp)
{
    IndexList unused;

    EntryRef prev;
    for (const auto& index : to_remove) {
        assert(prev <= index);
        if (index != prev) {
            _enumStore.free_value_if_unused(index, unused);
            prev = index;
        }
    }
    remove_unused_values(unused, cmp);
}

template <typename BTreeDictionaryT, typename HashDictionaryT>
void
EnumStoreDictionary<BTreeDictionaryT, HashDictionaryT>::remove(const EntryComparator &comp, EntryRef ref)
{
    assert(ref.valid());
    if constexpr (has_btree_dictionary) {
        auto itr = this->_btree_dict.lowerBound(ref, comp);
        assert(itr.valid() && itr.getKey() == ref);
        if constexpr (std::is_same_v<BTreeDictionaryT, EnumPostingTree>) {
            assert(EntryRef(itr.getData()) == EntryRef());
        }
        this->_btree_dict.remove(itr);
    }
    if constexpr (has_hash_dictionary) {
        auto *result = this->_hash_dict.remove(comp, ref);
        assert(result != nullptr && result->first.load_relaxed() == ref);
    }
}

template <typename BTreeDictionaryT, typename HashDictionaryT>
bool
EnumStoreDictionary<BTreeDictionaryT, HashDictionaryT>::find_index(const EntryComparator& cmp, Index& idx) const
{
    if constexpr (has_hash_dictionary) {
        auto find_result = this->_hash_dict.find(cmp, EntryRef());
        if (find_result != nullptr) {
            idx = find_result->first.load_acquire();
            return true;
        }
        return false;
    } else {
        auto itr = this->_btree_dict.find(Index(), cmp);
        if (!itr.valid()) {
            return false;
        }
        idx = itr.getKey();
        return true;
    }
}

template <typename BTreeDictionaryT, typename HashDictionaryT>
bool
EnumStoreDictionary<BTreeDictionaryT, HashDictionaryT>::find_frozen_index(const EntryComparator& cmp, Index& idx) const
{
    if constexpr (has_hash_dictionary) {
        auto find_result = this->_hash_dict.find(cmp, EntryRef());
        if (find_result != nullptr) {
            idx = find_result->first.load_acquire();
            return true;
        }
        return false;
    } else {
        auto itr = this->_btree_dict.getFrozenView().find(Index(), cmp);
        if (!itr.valid()) {
            return false;
        }
        idx = itr.getKey();
        return true;
    }
}

template <typename BTreeDictionaryT, typename HashDictionaryT>
std::vector<IEnumStore::EnumHandle>
EnumStoreDictionary<BTreeDictionaryT, HashDictionaryT>::find_matching_enums(const EntryComparator& cmp) const
{
    std::vector<IEnumStore::EnumHandle> result;
    if constexpr (has_btree_dictionary) {
        auto itr = this->_btree_dict.getFrozenView().find(Index(), cmp);
        while (itr.valid() && !cmp.less(Index(), itr.getKey())) {
            result.push_back(itr.getKey().ref());
            ++itr;
        }
    } else {
        auto find_result = this->_hash_dict.find(cmp, EntryRef());
        if (find_result != nullptr) {
            result.push_back(find_result->first.load_acquire().ref());
        }
    }
    return result;
}

template <typename BTreeDictionaryT, typename HashDictionaryT>
EntryRef
EnumStoreDictionary<BTreeDictionaryT, HashDictionaryT>::get_frozen_root() const
{
    if constexpr (has_btree_dictionary) {
        return this->_btree_dict.getFrozenView().getRoot();
    } else {
        return EntryRef();
    }
}

template <>
std::pair<IEnumStore::Index, EntryRef>
EnumStoreDictionary<EnumTree>::find_posting_list(const EntryComparator&, EntryRef) const
{
    LOG_ABORT("should not be reached");
}

template <typename BTreeDictionaryT, typename HashDictionaryT>
std::pair<IEnumStore::Index, EntryRef>
EnumStoreDictionary<BTreeDictionaryT, HashDictionaryT>::find_posting_list(const EntryComparator& cmp, EntryRef root) const
{
    if constexpr (has_hash_dictionary) {
        (void) root;
        auto find_result = this->_hash_dict.find(cmp, EntryRef());
        if (find_result != nullptr) {
            return std::make_pair(find_result->first.load_acquire(), find_result->second.load_acquire());
        }
        return std::make_pair(Index(), EntryRef());
    } else {
        typename BTreeDictionaryType::ConstIterator itr(vespalib::btree::BTreeNode::Ref(), this->_btree_dict.getAllocator());
        itr.lower_bound(root, Index(), cmp);
        if (itr.valid() && !cmp.less(Index(), itr.getKey())) {
            return std::make_pair(itr.getKey(), EntryRef(itr.getData()));
        }
        return std::make_pair(Index(), EntryRef());
    }
}

template <typename BTreeDictionaryT, typename HashDictionaryT>
void
EnumStoreDictionary<BTreeDictionaryT, HashDictionaryT>::collect_folded(Index idx, EntryRef, const std::function<void(EntryRef)>& callback) const
{
    callback(idx);
}

template <typename BTreeDictionaryT, typename HashDictionaryT>
IEnumStore::Index
EnumStoreDictionary<BTreeDictionaryT, HashDictionaryT>::remap_index(Index idx)
{
    return idx;
}

template <>
void
EnumStoreDictionary<EnumTree>::clear_all_posting_lists(std::function<void(EntryRef)>)
{
    LOG_ABORT("should not be reached");
}

template <typename BTreeDictionaryT, typename HashDictionaryT>
void
EnumStoreDictionary<BTreeDictionaryT, HashDictionaryT>::clear_all_posting_lists(std::function<void(EntryRef)> clearer)
{
    if constexpr (has_btree_dictionary) {
        auto& dict = this->_btree_dict;
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
    } else {
        this->_hash_dict.normalize_values([&clearer](EntryRef ref) { clearer(ref); return EntryRef(); });
    }
}

template <>
void
EnumStoreDictionary<EnumTree>::update_posting_list(Index, const EntryComparator&, std::function<EntryRef(EntryRef)>)
{
    LOG_ABORT("should not be reached");
}

template <typename BTreeDictionaryT, typename HashDictionaryT>
void
EnumStoreDictionary<BTreeDictionaryT, HashDictionaryT>::update_posting_list(Index idx, const EntryComparator& cmp, std::function<EntryRef(EntryRef)> updater)
{
    if constexpr (has_btree_dictionary) {
        auto& dict = this->_btree_dict;
        auto itr = dict.lowerBound(idx, cmp);
        assert(itr.valid() && itr.getKey() == idx);
        EntryRef old_posting_idx(itr.getData());
        EntryRef new_posting_idx = updater(old_posting_idx);
        // Note: Needs review when porting to other platforms
        // Assumes that other CPUs observes stores from this CPU in order
        std::atomic_thread_fence(std::memory_order_release);
        itr.writeData(new_posting_idx.ref());
        if constexpr (has_hash_dictionary) {
            auto find_result = this->_hash_dict.find(this->_hash_dict.get_default_comparator(), idx);
            assert(find_result != nullptr && find_result->first.load_relaxed() == idx);
            assert(find_result->second.load_relaxed() == old_posting_idx);
            find_result->second.store_release(new_posting_idx);
        }
    } else {
        auto find_result = this->_hash_dict.find(this->_hash_dict.get_default_comparator(), idx);
        assert(find_result != nullptr && find_result->first.load_relaxed() == idx);
        EntryRef old_posting_idx = find_result->second.load_relaxed();
        EntryRef new_posting_idx = updater(old_posting_idx);
        find_result->second.store_release(new_posting_idx);
    }
}

template <>
bool
EnumStoreDictionary<EnumTree>::normalize_posting_lists(std::function<EntryRef(EntryRef)>)
{
    LOG_ABORT("should not be reached");
}

template <typename BTreeDictionaryT, typename HashDictionaryT>
bool
EnumStoreDictionary<BTreeDictionaryT, HashDictionaryT>::normalize_posting_lists(std::function<EntryRef(EntryRef)> normalize)
{
    if constexpr (has_btree_dictionary) {
        bool changed = false;
        auto& dict = this->_btree_dict;
        for (auto itr = dict.begin(); itr.valid(); ++itr) {
            EntryRef old_posting_idx(itr.getData());
            EntryRef new_posting_idx = normalize(old_posting_idx);
            if (new_posting_idx != old_posting_idx) {
                changed = true;
                // Note: Needs review when porting to other platforms
                // Assumes that other CPUs observes stores from this CPU in order
                std::atomic_thread_fence(std::memory_order_release);
                itr.writeData(new_posting_idx.ref());
                if constexpr (has_hash_dictionary) {
                    auto find_result = this->_hash_dict.find(this->_hash_dict.get_default_comparator(), itr.getKey());
                    assert(find_result != nullptr && find_result->first.load_relaxed() == itr.getKey());
                    assert(find_result->second.load_relaxed() == old_posting_idx);
                    find_result->second.store_release(new_posting_idx);
                }
            }
        }
        return changed;
    } else {
        return this->_hash_dict.normalize_values(normalize);
    }
}

template <>
const EnumPostingTree &
EnumStoreDictionary<EnumTree>::get_posting_dictionary() const
{
    LOG_ABORT("should not be reached");
}

template <>
const EnumPostingTree &
EnumStoreDictionary<vespalib::datastore::NoBTreeDictionary, vespalib::datastore::ShardedHashMap>::get_posting_dictionary() const
{
    LOG_ABORT("should not be reached");
}

template <typename BTreeDictionaryT, typename HashDictionaryT>
const EnumPostingTree &
EnumStoreDictionary<BTreeDictionaryT, HashDictionaryT>::get_posting_dictionary() const
{
    return this->_btree_dict;
}

EnumStoreFoldedDictionary::EnumStoreFoldedDictionary(IEnumStore& enumStore, std::unique_ptr<EntryComparator> compare, std::unique_ptr<EntryComparator> folded_compare)
    : EnumStoreDictionary<EnumPostingTree>(enumStore, std::move(compare)),
      _folded_compare(std::move(folded_compare))
{
}

EnumStoreFoldedDictionary::~EnumStoreFoldedDictionary() = default;

UniqueStoreAddResult
EnumStoreFoldedDictionary::add(const EntryComparator& comp, std::function<EntryRef(void)> insertEntry)
{
    static_assert(!has_hash_dictionary, "Folded Dictionary does not support hash dictionary");
    auto it = _btree_dict.lowerBound(EntryRef(), comp);
    if (it.valid() && !comp.less(EntryRef(), it.getKey())) {
        // Entry already exists
        return UniqueStoreAddResult(it.getKey(), false);
    }
    EntryRef newRef = insertEntry();
    _btree_dict.insert(it, newRef, EntryRef().ref());
    // Maybe move posting list reference from next entry
    ++it;
    if (it.valid() && EntryRef(it.getData()).valid() && !_folded_compare->less(newRef, it.getKey())) {
        EntryRef posting_list_ref(it.getData());
        _btree_dict.thaw(it);
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
    static_assert(!has_hash_dictionary, "Folded Dictionary does not support hash dictionary");
    assert(ref.valid());
    auto it = _btree_dict.lowerBound(ref, comp);
    assert(it.valid() && it.getKey() == ref);
    EntryRef posting_list_ref(it.getData());
    _btree_dict.remove(it);
    // Maybe copy posting list reference to next entry
    if (posting_list_ref.valid()) {
        if (it.valid() && !EntryRef(it.getData()).valid() && !_folded_compare->less(ref, it.getKey())) {
            this->_btree_dict.thaw(it);
            it.writeData(posting_list_ref.ref());
        } else {
            LOG_ABORT("Posting list not cleared for removed unique value");
        }
    }
}

void
EnumStoreFoldedDictionary::collect_folded(Index idx, EntryRef root, const std::function<void(EntryRef)>& callback) const
{
    BTreeDictionaryType::ConstIterator itr(vespalib::btree::BTreeNode::Ref(), _btree_dict.getAllocator());
    itr.lower_bound(root, idx, *_folded_compare);
    while (itr.valid() && !_folded_compare->less(idx, itr.getKey())) {
        callback(itr.getKey());
        ++itr;
    }
}

IEnumStore::Index
EnumStoreFoldedDictionary::remap_index(Index idx)
{
    auto itr = _btree_dict.find(idx, *_folded_compare);
    assert(itr.valid());
    return itr.getKey();
}

template class EnumStoreDictionary<EnumTree>;

template class EnumStoreDictionary<EnumPostingTree>;

template class EnumStoreDictionary<EnumPostingTree, vespalib::datastore::ShardedHashMap>;

template class EnumStoreDictionary<vespalib::datastore::NoBTreeDictionary, vespalib::datastore::ShardedHashMap>;

}

namespace vespalib::btree {

using search::IEnumStore;
using search::EnumTreeTraits;
using datastore::EntryComparatorWrapper;

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
                const EntryComparatorWrapper, EnumTreeTraits>;

template
class BTreeRoot<IEnumStore::Index, uint32_t, NoAggregated,
                const EntryComparatorWrapper, EnumTreeTraits>;

template
class BTreeRootT<IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                 const EntryComparatorWrapper, EnumTreeTraits>;

template
class BTreeRootT<IEnumStore::Index, uint32_t, NoAggregated,
                 const EntryComparatorWrapper, EnumTreeTraits>;

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
                                  const EntryComparatorWrapper, EnumTreeTraits>;

template class BTreeConstIterator<IEnumStore::Index, uint32_t, NoAggregated,
                                  const EntryComparatorWrapper, EnumTreeTraits>;

template
class BTreeIterator<IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                    const EntryComparatorWrapper, EnumTreeTraits>;
template
class BTreeIterator<IEnumStore::Index, uint32_t, NoAggregated,
                    const EntryComparatorWrapper, EnumTreeTraits>;

template
class BTree<IEnumStore::Index, BTreeNoLeafData, NoAggregated,
            const EntryComparatorWrapper, EnumTreeTraits>;
template
class BTree<IEnumStore::Index, uint32_t, NoAggregated,
            const EntryComparatorWrapper, EnumTreeTraits>;

}
