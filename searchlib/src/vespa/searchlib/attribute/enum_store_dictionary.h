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

public:
    EnumStoreDictionary(IEnumStore& enumStore);

    ~EnumStoreDictionary() override;

    const DictionaryT &getDictionary() const { return this->_dict; }
    DictionaryT &getDictionary() { return this->_dict; }

    uint32_t getNumUniques() const override;
    void set_ref_counts(const EnumVector &hist) override;

    void removeUnusedEnums(const IndexSet& unused,
                           const datastore::EntryComparator& cmp);

    void freeUnusedEnums(const datastore::EntryComparator& cmp) override;

    void freeUnusedEnums(const IndexSet& toRemove,
                         const datastore::EntryComparator& cmp) override;

    bool findIndex(const datastore::EntryComparator& cmp, Index& idx) const override;
    bool findFrozenIndex(const datastore::EntryComparator& cmp, Index& idx) const override;
    std::vector<attribute::IAttributeVector::EnumHandle>
    findMatchingEnums(const datastore::EntryComparator& cmp) const override;

    void onReset() override;

    EnumPostingTree & getPostingDictionary() override;
    const EnumPostingTree & getPostingDictionary() const override;

    bool hasData() const override;
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
