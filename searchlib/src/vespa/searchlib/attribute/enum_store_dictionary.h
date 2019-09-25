// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store_dictionary.h"
#include <vespa/vespalib/btree/btree.h>

namespace search {

class IEnumStore;

/**
 * Concrete dictionary for an enum store that extends the functionality of a unique store dictionary.
 */
template <typename DictionaryT>
class EnumStoreDictionary : public datastore::UniqueStoreDictionary<DictionaryT, IEnumStoreDictionary> {
private:
    using EnumVector = IEnumStoreDictionary::EnumVector;
    using Index = IEnumStoreDictionary::Index;
    using IndexSet = IEnumStoreDictionary::IndexSet;
    using IndexVector = IEnumStoreDictionary::IndexVector;
    using ParentUniqueStoreDictionary = datastore::UniqueStoreDictionary<DictionaryT, IEnumStoreDictionary>;
    using generation_t = IEnumStoreDictionary::generation_t;

    IEnumStore& _enumStore;

    void remove_unused_values(const IndexSet& unused,
                              const datastore::EntryComparator& cmp);

public:
    EnumStoreDictionary(IEnumStore& enumStore);

    ~EnumStoreDictionary() override;

    const DictionaryT& get_raw_dictionary() const { return this->_dict; }

    void set_ref_counts(const EnumVector& hist) override;

    void free_unused_values(const datastore::EntryComparator& cmp) override;

    void free_unused_values(const IndexSet& to_remove,
                            const datastore::EntryComparator& cmp) override;

    void remove(const datastore::EntryComparator& comp, datastore::EntryRef ref) override;
    bool find_index(const datastore::EntryComparator& cmp, Index& idx) const override;
    bool find_frozen_index(const datastore::EntryComparator& cmp, Index& idx) const override;
    std::vector<attribute::IAttributeVector::EnumHandle>
    find_matching_enums(const datastore::EntryComparator& cmp) const override;

    EnumPostingTree& get_posting_dictionary() override;
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
    std::unique_ptr<datastore::EntryComparator> _folded_compare;

public:
    EnumStoreFoldedDictionary(IEnumStore& enumStore, std::unique_ptr<datastore::EntryComparator> folded_compare);
    ~EnumStoreFoldedDictionary() override;
    datastore::UniqueStoreAddResult add(const datastore::EntryComparator& comp, std::function<datastore::EntryRef(void)> insertEntry) override;
    void remove(const datastore::EntryComparator& comp, datastore::EntryRef ref) override;
};

extern template
class btree::BTreeNodeT<IEnumStore::Index, EnumTreeTraits::INTERNAL_SLOTS>;

extern template
class btree::BTreeNodeTT<IEnumStore::Index, uint32_t, btree::NoAggregated, EnumTreeTraits::INTERNAL_SLOTS>;

extern template
class btree::BTreeNodeTT<IEnumStore::Index, btree::BTreeNoLeafData, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeInternalNode<IEnumStore::Index, btree::NoAggregated, EnumTreeTraits::INTERNAL_SLOTS>;

extern template
class btree::BTreeLeafNode<IEnumStore::Index, btree::BTreeNoLeafData, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeLeafNode<IEnumStore::Index, uint32_t, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeLeafNodeTemp<IEnumStore::Index, btree::BTreeNoLeafData, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeLeafNodeTemp<IEnumStore::Index, uint32_t, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeNodeStore<IEnumStore::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                            EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeNodeStore<IEnumStore::Index, uint32_t, btree::NoAggregated,
                            EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeRoot<IEnumStore::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                       const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTreeRoot<IEnumStore::Index, uint32_t, btree::NoAggregated,
                       const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTreeRootT<IEnumStore::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                        const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTreeRootT<IEnumStore::Index, uint32_t, btree::NoAggregated,
                        const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTreeRootBase<IEnumStore::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                           EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeRootBase<IEnumStore::Index, uint32_t, btree::NoAggregated,
                           EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeNodeAllocator<IEnumStore::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                                EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeNodeAllocator<IEnumStore::Index, uint32_t, btree::NoAggregated,
                                EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;


extern template
class btree::BTreeIteratorBase<IEnumStore::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                               EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS, EnumTreeTraits::PATH_SIZE>;
extern template
class btree::BTreeIteratorBase<IEnumStore::Index, uint32_t, btree::NoAggregated,
                               EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS, EnumTreeTraits::PATH_SIZE>;

extern template class btree::BTreeConstIterator<IEnumStore::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                                                const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template class btree::BTreeConstIterator<IEnumStore::Index, uint32_t, btree::NoAggregated,
                                                const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTreeIterator<IEnumStore::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                           const datastore::EntryComparatorWrapper, EnumTreeTraits>;
extern template
class btree::BTreeIterator<IEnumStore::Index, uint32_t, btree::NoAggregated,
                           const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTree<IEnumStore::Index, btree::BTreeNoLeafData, btree::NoAggregated,
                   const datastore::EntryComparatorWrapper, EnumTreeTraits>;
extern template
class btree::BTree<IEnumStore::Index, uint32_t, btree::NoAggregated,
                   const datastore::EntryComparatorWrapper, EnumTreeTraits>;


}
