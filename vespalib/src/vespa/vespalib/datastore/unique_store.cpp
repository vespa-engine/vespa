// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "unique_store.h"
#include "datastore.hpp"
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreebuilder.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>

namespace search::btree {

// Instantiate classes related to unique store dictionary btree

using EntryRef = datastore::EntryRef;
using EntryComparatorWrapper = datastore::EntryComparatorWrapper;
using DictionaryTraits = BTreeTraits<32, 32, 7, true>;

template class BTreeNodeDataWrap<uint32_t, DictionaryTraits::LEAF_SLOTS>;
template class BTreeNodeT<EntryRef, DictionaryTraits::INTERNAL_SLOTS>;
template class BTreeNodeTT<EntryRef, uint32_t, NoAggregated, DictionaryTraits::LEAF_SLOTS>;
template class BTreeNodeTT<EntryRef, EntryRef, NoAggregated, DictionaryTraits::INTERNAL_SLOTS>;
template class BTreeInternalNode<EntryRef, NoAggregated, DictionaryTraits::INTERNAL_SLOTS>;
template class BTreeLeafNode<EntryRef, uint32_t, NoAggregated, DictionaryTraits::LEAF_SLOTS>;
template class BTreeLeafNodeTemp<EntryRef, uint32_t, NoAggregated, DictionaryTraits::LEAF_SLOTS>;
template class BTreeRootBase<EntryRef, uint32_t, NoAggregated, DictionaryTraits::INTERNAL_SLOTS, DictionaryTraits::LEAF_SLOTS>;
template class BTreeRootT<EntryRef, uint32_t, NoAggregated, EntryComparatorWrapper, DictionaryTraits>;
template class BTreeRoot<EntryRef, uint32_t, NoAggregated, EntryComparatorWrapper, DictionaryTraits>;
template class BTree<EntryRef, uint32_t, NoAggregated, EntryComparatorWrapper, DictionaryTraits>;
template class BTreeBuilder<EntryRef, uint32_t, NoAggregated, DictionaryTraits::INTERNAL_SLOTS, DictionaryTraits::LEAF_SLOTS>;
template class BTreeNodeAllocator<EntryRef, uint32_t, NoAggregated, DictionaryTraits::INTERNAL_SLOTS, DictionaryTraits::LEAF_SLOTS>;
template class BTreeNodeStore<EntryRef, uint32_t, NoAggregated, DictionaryTraits::INTERNAL_SLOTS, DictionaryTraits::LEAF_SLOTS>;
template class BTreeIteratorBase<EntryRef, uint32_t, NoAggregated, DictionaryTraits::INTERNAL_SLOTS, DictionaryTraits::LEAF_SLOTS, DictionaryTraits::PATH_SIZE>;
template class BTreeConstIterator<EntryRef, uint32_t, NoAggregated, EntryComparatorWrapper, DictionaryTraits>;
template class BTreeIterator<EntryRef, uint32_t, NoAggregated, EntryComparatorWrapper, DictionaryTraits>;

}
