// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_enum_store_dictionary.h"
#include <vespa/vespalib/btree/btree.h>

namespace search {

class EnumStoreBase;

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

    EnumStoreBase& _enumStore;

public:
    EnumStoreDictionary(EnumStoreBase& enumStore);

    ~EnumStoreDictionary() override;

    const DictionaryT &getDictionary() const { return this->_dict; }
    DictionaryT &getDictionary() { return this->_dict; }

    uint32_t getNumUniques() const override;
    void writeAllValues(BufferWriter& writer, btree::BTreeNode::Ref rootRef) const override;
    ssize_t deserialize(const void* src, size_t available, IndexVector& idx) override;
    void fixupRefCounts(const EnumVector& hist) override;

    void removeUnusedEnums(const IndexSet& unused,
                           const datastore::EntryComparator& cmp,
                           const datastore::EntryComparator* fcmp);

    void freeUnusedEnums(const datastore::EntryComparator& cmp,
                         const datastore::EntryComparator* fcmp) override;

    void freeUnusedEnums(const IndexSet& toRemove,
                         const datastore::EntryComparator& cmp,
                         const datastore::EntryComparator* fcmp) override;

    bool findIndex(const datastore::EntryComparator& cmp, Index& idx) const override;
    bool findFrozenIndex(const datastore::EntryComparator& cmp, Index& idx) const override;
    std::vector<attribute::IAttributeVector::EnumHandle>
    findMatchingEnums(const datastore::EntryComparator& cmp) const override;

    void onReset() override;
    btree::BTreeNode::Ref getFrozenRootRef() const override { return this->get_frozen_root(); }

    EnumPostingTree & getPostingDictionary() override;
    const EnumPostingTree & getPostingDictionary() const override;

    bool hasData() const override;
};

extern template
class btree::BTreeNodeT<EnumStoreIndex, EnumTreeTraits::INTERNAL_SLOTS>;

extern template
class btree::BTreeNodeTT<EnumStoreIndex, datastore::EntryRef, btree::NoAggregated, EnumTreeTraits::INTERNAL_SLOTS>;

extern template
class btree::BTreeNodeTT<EnumStoreIndex, btree::BTreeNoLeafData, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeInternalNode<EnumStoreIndex, btree::NoAggregated, EnumTreeTraits::INTERNAL_SLOTS>;

extern template
class btree::BTreeLeafNode<EnumStoreIndex, btree::BTreeNoLeafData, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeLeafNode<EnumStoreIndex, datastore::EntryRef, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeLeafNodeTemp<EnumStoreIndex, btree::BTreeNoLeafData, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeLeafNodeTemp<EnumStoreIndex, datastore::EntryRef, btree::NoAggregated, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeNodeStore<EnumStoreIndex, btree::BTreeNoLeafData, btree::NoAggregated,
                            EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeNodeStore<EnumStoreIndex, datastore::EntryRef, btree::NoAggregated,
                            EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeRoot<EnumStoreIndex, btree::BTreeNoLeafData, btree::NoAggregated,
                       const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTreeRoot<EnumStoreIndex, datastore::EntryRef, btree::NoAggregated,
                       const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTreeRootT<EnumStoreIndex, btree::BTreeNoLeafData, btree::NoAggregated,
                        const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTreeRootT<EnumStoreIndex, datastore::EntryRef, btree::NoAggregated,
                        const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTreeRootBase<EnumStoreIndex, btree::BTreeNoLeafData, btree::NoAggregated,
                           EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeRootBase<EnumStoreIndex, datastore::EntryRef, btree::NoAggregated,
                           EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeNodeAllocator<EnumStoreIndex, btree::BTreeNoLeafData, btree::NoAggregated,
                                EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;

extern template
class btree::BTreeNodeAllocator<EnumStoreIndex, datastore::EntryRef, btree::NoAggregated,
                                EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS>;


extern template
class btree::BTreeIteratorBase<EnumStoreIndex, btree::BTreeNoLeafData, btree::NoAggregated,
                               EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS, EnumTreeTraits::PATH_SIZE>;
extern template
class btree::BTreeIteratorBase<EnumStoreIndex, datastore::EntryRef, btree::NoAggregated,
                               EnumTreeTraits::INTERNAL_SLOTS, EnumTreeTraits::LEAF_SLOTS, EnumTreeTraits::PATH_SIZE>;

extern template class btree::BTreeConstIterator<EnumStoreIndex, btree::BTreeNoLeafData, btree::NoAggregated,
                                                const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template class btree::BTreeConstIterator<EnumStoreIndex, datastore::EntryRef, btree::NoAggregated,
                                                const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTreeIterator<EnumStoreIndex, btree::BTreeNoLeafData, btree::NoAggregated,
                           const datastore::EntryComparatorWrapper, EnumTreeTraits>;
extern template
class btree::BTreeIterator<EnumStoreIndex, datastore::EntryRef, btree::NoAggregated,
                           const datastore::EntryComparatorWrapper, EnumTreeTraits>;

extern template
class btree::BTree<EnumStoreIndex, btree::BTreeNoLeafData, btree::NoAggregated,
                   const datastore::EntryComparatorWrapper, EnumTreeTraits>;
extern template
class btree::BTree<EnumStoreIndex, datastore::EntryRef, btree::NoAggregated,
                   const datastore::EntryComparatorWrapper, EnumTreeTraits>;


}
