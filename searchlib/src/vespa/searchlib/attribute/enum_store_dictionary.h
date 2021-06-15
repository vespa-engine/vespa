// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store_dictionary.h"
#include <vespa/vespalib/btree/btree.h>

namespace search {

class IEnumStore;

/**
 * Concrete dictionary for an enum store that extends the functionality of a unique store dictionary.
 */
template <typename BTreeDictionaryT, typename HashDictionaryT = vespalib::datastore::NoHashDictionary>
class EnumStoreDictionary : public vespalib::datastore::UniqueStoreDictionary<BTreeDictionaryT, IEnumStoreDictionary, HashDictionaryT> {
protected:
    using EntryRef = IEnumStoreDictionary::EntryRef;
    using Index = IEnumStoreDictionary::Index;
    using BTreeDictionaryType = BTreeDictionaryT;
    using EntryComparator = IEnumStoreDictionary::EntryComparator;
private:
    using EnumVector = IEnumStoreDictionary::EnumVector;
    using IndexList = IEnumStoreDictionary::IndexList;
    using IndexVector = IEnumStoreDictionary::IndexVector;
    using ParentUniqueStoreDictionary = vespalib::datastore::UniqueStoreDictionary<BTreeDictionaryT, IEnumStoreDictionary, HashDictionaryT>;
    using generation_t = IEnumStoreDictionary::generation_t;
protected:
    using ParentUniqueStoreDictionary::has_btree_dictionary;
    using ParentUniqueStoreDictionary::has_hash_dictionary;
private:
    IEnumStore& _enumStore;

    void remove_unused_values(const IndexList& unused, const EntryComparator& cmp);

public:
    EnumStoreDictionary(IEnumStore& enumStore, std::unique_ptr<EntryComparator> compare);

    ~EnumStoreDictionary() override;

    void free_unused_values(const EntryComparator& cmp) override;
    void free_unused_values(const IndexList& to_remove, const EntryComparator& cmp) override;

    void remove(const EntryComparator& comp, EntryRef ref) override;
    bool find_index(const EntryComparator& cmp, Index& idx) const override;
    bool find_frozen_index(const EntryComparator& cmp, Index& idx) const override;
    std::vector<attribute::IAttributeVector::EnumHandle>
    find_matching_enums(const EntryComparator& cmp) const override;

    EntryRef get_frozen_root() const override;
    std::pair<Index, EntryRef> find_posting_list(const EntryComparator& cmp, EntryRef root) const override;
    void collect_folded(Index idx, EntryRef root, const std::function<void(EntryRef)>& callback) const override;
    Index remap_index(Index idx) override;
    void clear_all_posting_lists(std::function<void(EntryRef)> clearer) override;
    void update_posting_list(Index idx, const EntryComparator& cmp, std::function<EntryRef(EntryRef)> updater) override;
    bool normalize_posting_lists(std::function<EntryRef(EntryRef)> normalize) override;
    const EnumPostingTree& get_posting_dictionary() const override;
};

/**
 * Concrete dictionary for an enum store that extends the
 * functionality of a unique store dictionary.
 *
 * Special handling of value (posting list reference) is added to
 * ensure that entries with same folded key share a posting list
 * (e.g. case insensitive search) and posting list reference is found
 * for the first of these entries.
 */
class EnumStoreFoldedDictionary : public EnumStoreDictionary<EnumPostingTree>
{
private:
    std::unique_ptr<EntryComparator> _folded_compare;

public:
    EnumStoreFoldedDictionary(IEnumStore& enumStore, std::unique_ptr<EntryComparator> compare, std::unique_ptr<EntryComparator> folded_compare);
    ~EnumStoreFoldedDictionary() override;
    vespalib::datastore::UniqueStoreAddResult add(const EntryComparator& comp, std::function<EntryRef(void)> insertEntry) override;
    void remove(const EntryComparator& comp, EntryRef ref) override;
    void collect_folded(Index idx, EntryRef root, const std::function<void(EntryRef)>& callback) const override;
    Index remap_index(Index idx) override;
};

}

namespace vespalib::btree {

extern template
class BTreeNodeT<search::IEnumStore::Index, search::EnumTreeTraits::INTERNAL_SLOTS>;

extern template
class BTreeNodeTT<search::IEnumStore::Index, uint32_t, NoAggregated, search::EnumTreeTraits::INTERNAL_SLOTS>;

extern template
class BTreeNodeTT<search::IEnumStore::Index, BTreeNoLeafData, NoAggregated, search::EnumTreeTraits::LEAF_SLOTS>;

extern template
class BTreeInternalNode<search::IEnumStore::Index, NoAggregated, search::EnumTreeTraits::INTERNAL_SLOTS>;

extern template
class BTreeLeafNode<search::IEnumStore::Index, BTreeNoLeafData, NoAggregated, search::EnumTreeTraits::LEAF_SLOTS>;

extern template
class BTreeLeafNode<search::IEnumStore::Index, uint32_t, NoAggregated, search::EnumTreeTraits::LEAF_SLOTS>;

extern template
class BTreeLeafNodeTemp<search::IEnumStore::Index, BTreeNoLeafData, NoAggregated, search::EnumTreeTraits::LEAF_SLOTS>;

extern template
class BTreeLeafNodeTemp<search::IEnumStore::Index, uint32_t, NoAggregated, search::EnumTreeTraits::LEAF_SLOTS>;

extern template
class BTreeNodeStore<search::IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                     search::EnumTreeTraits::INTERNAL_SLOTS, search::EnumTreeTraits::LEAF_SLOTS>;

extern template
class BTreeNodeStore<search::IEnumStore::Index, uint32_t, NoAggregated,
                     search::EnumTreeTraits::INTERNAL_SLOTS, search::EnumTreeTraits::LEAF_SLOTS>;

extern template
class BTreeRoot<search::IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                const vespalib::datastore::EntryComparatorWrapper, search::EnumTreeTraits>;

extern template
class BTreeRoot<search::IEnumStore::Index, uint32_t, NoAggregated,
                const vespalib::datastore::EntryComparatorWrapper, search::EnumTreeTraits>;

extern template
class BTreeRootT<search::IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                 const vespalib::datastore::EntryComparatorWrapper, search::EnumTreeTraits>;

extern template
class BTreeRootT<search::IEnumStore::Index, uint32_t, NoAggregated,
                 const vespalib::datastore::EntryComparatorWrapper, search::EnumTreeTraits>;

extern template
class BTreeRootBase<search::IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                    search::EnumTreeTraits::INTERNAL_SLOTS, search::EnumTreeTraits::LEAF_SLOTS>;

extern template
class BTreeRootBase<search::IEnumStore::Index, uint32_t, NoAggregated,
                    search::EnumTreeTraits::INTERNAL_SLOTS, search::EnumTreeTraits::LEAF_SLOTS>;

extern template
class BTreeNodeAllocator<search::IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                         search::EnumTreeTraits::INTERNAL_SLOTS, search::EnumTreeTraits::LEAF_SLOTS>;

extern template
class BTreeNodeAllocator<search::IEnumStore::Index, uint32_t, NoAggregated,
                         search::EnumTreeTraits::INTERNAL_SLOTS, search::EnumTreeTraits::LEAF_SLOTS>;


extern template
class BTreeIteratorBase<search::IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                        search::EnumTreeTraits::INTERNAL_SLOTS, search::EnumTreeTraits::LEAF_SLOTS, search::EnumTreeTraits::PATH_SIZE>;
extern template
class BTreeIteratorBase<search::IEnumStore::Index, uint32_t, NoAggregated,
                        search::EnumTreeTraits::INTERNAL_SLOTS, search::EnumTreeTraits::LEAF_SLOTS, search::EnumTreeTraits::PATH_SIZE>;

extern template class BTreeConstIterator<search::IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                                         const vespalib::datastore::EntryComparatorWrapper, search::EnumTreeTraits>;

extern template class BTreeConstIterator<search::IEnumStore::Index, uint32_t, NoAggregated,
                                         const vespalib::datastore::EntryComparatorWrapper, search::EnumTreeTraits>;

extern template
class BTreeIterator<search::IEnumStore::Index, BTreeNoLeafData, NoAggregated,
                    const vespalib::datastore::EntryComparatorWrapper, search::EnumTreeTraits>;
extern template
class BTreeIterator<search::IEnumStore::Index, uint32_t, NoAggregated,
                    const vespalib::datastore::EntryComparatorWrapper, search::EnumTreeTraits>;

extern template
class BTree<search::IEnumStore::Index, BTreeNoLeafData, NoAggregated,
            const vespalib::datastore::EntryComparatorWrapper, search::EnumTreeTraits>;
extern template
class BTree<search::IEnumStore::Index, uint32_t, NoAggregated,
            const vespalib::datastore::EntryComparatorWrapper, search::EnumTreeTraits>;

}
