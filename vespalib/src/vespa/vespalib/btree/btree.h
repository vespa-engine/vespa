// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreeroot.h"
#include "noaggrcalc.h"
#include <vespa/vespalib/util/generationhandler.h>

namespace vespalib::datastore { class CompactionStrategy; }

namespace vespalib::btree {

/**
 * Class that wraps a btree root and an allocator and that provides the same API as
 * a standalone btree root without needing to pass the allocator to all functions.
 **/
template <typename KeyT,
          typename DataT,
          typename AggrT = NoAggregated,
          typename CompareT = std::less<KeyT>,
          typename TraitsT = BTreeDefaultTraits,
          class AggrCalcT = NoAggrCalc>
class BTree
{
public:
    using TreeType = BTreeRoot<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>;
    using NodeAllocatorType = BTreeNodeAllocator<KeyT, DataT, AggrT,
                                                 TraitsT::INTERNAL_SLOTS,
                                                 TraitsT::LEAF_SLOTS>;
    using Builder = BTreeBuilder<KeyT, DataT, AggrT,
                                 TraitsT::INTERNAL_SLOTS,
                                 TraitsT::LEAF_SLOTS,
                                 AggrCalcT>;
    using InternalNodeType = typename TreeType::InternalNodeType;
    using LeafNodeType = typename TreeType::LeafNodeType;
    using KeyType = typename TreeType::KeyType;
    using DataType = typename TreeType::DataType;
    using Iterator = typename TreeType::Iterator;
    using ConstIterator = typename TreeType::ConstIterator;
    using FrozenView = typename TreeType::FrozenView;
    using AggrCalcType = typename TreeType::AggrCalcType;
private:
    NodeAllocatorType   _alloc;
    TreeType            _tree;

    BTree(const BTree &rhs);

    BTree &
    operator=(BTree &rhs);

public:
    BTree();
    ~BTree();

    const NodeAllocatorType &getAllocator() const { return _alloc; }
    NodeAllocatorType &getAllocator() { return _alloc; }

    void
    disableFreeLists() {
        _alloc.disableFreeLists();
    }

    void
    disable_entry_hold_list()
    {
        _alloc.disable_entry_hold_list();
    }

    // Inherit doc from BTreeRoot
    void clear() {
        _tree.clear(_alloc);
    }
    void assign(Builder & rhs) {
        _tree.assign(rhs, _alloc);
    }
    bool insert(const KeyType & key, const DataType & data, CompareT comp = CompareT()) {
        return _tree.insert(key, data, _alloc, comp);
    }

    void
    insert(Iterator &itr,
           const KeyType &key, const DataType &data)
    {
        _tree.insert(itr, key, data);
    }

    Iterator find(const KeyType & key, CompareT comp = CompareT()) const {
        return _tree.find(key, _alloc, comp);
    }
    Iterator lowerBound(const KeyType & key, CompareT comp = CompareT()) const {
        return _tree.lowerBound(key, _alloc, comp);
    }
    Iterator upperBound(const KeyType & key, CompareT comp = CompareT()) const {
        return _tree.upperBound(key, _alloc, comp);
    }
    bool remove(const KeyType & key, CompareT comp = CompareT()) {
        return _tree.remove(key, _alloc, comp);
    }

    void
    remove(Iterator &itr)
    {
        _tree.remove(itr);
    }

    Iterator begin() const {
        return _tree.begin(_alloc);
    }
    FrozenView getFrozenView() const {
        return _tree.getFrozenView(_alloc);
    }
    size_t size() const {
        return _tree.size(_alloc);
    }
    vespalib::string toString() const {
        return _tree.toString(_alloc);
    }
    bool isValid(CompareT comp = CompareT()) const {
        return _tree.isValid(_alloc, comp);
    }
    bool isValidFrozen(CompareT comp = CompareT()) const {
        return _tree.isValidFrozen(_alloc, comp);
    }
    size_t bitSize() const {
        return _tree.bitSize(_alloc);
    }
    size_t bitSize(BTreeNode::Ref node) const {
        return _tree.bitSize(node, _alloc);
    }
    void setRoot(BTreeNode::Ref newRoot) {
        _tree.setRoot(newRoot, _alloc);
    }
    BTreeNode::Ref getRoot() const {
        return _tree.getRoot();
    }
    vespalib::MemoryUsage getMemoryUsage() const {
        return _alloc.getMemoryUsage();
    }

    const AggrT &
    getAggregated() const
    {
        return _tree.getAggregated(_alloc);
    }

    void
    thaw(Iterator &itr)
    {
        assert(&itr.getAllocator() == &getAllocator());
        _tree.thaw(itr);
    }

    void compact_worst(const datastore::CompactionStrategy& compaction_strategy);

    template <typename FunctionType>
    void
    foreach_key(FunctionType func) const
    {
        _alloc.getNodeStore().foreach_key(_tree.getRoot(), func);
    }

    template <typename FunctionType>
    void
    foreach(FunctionType func) const
    {
        _alloc.getNodeStore().foreach(_tree.getRoot(), func);
    }
};

}
