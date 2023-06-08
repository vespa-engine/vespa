// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreeiterator.h"
#include "btreenode.h"
#include "btreenodeallocator.h"
#include "btreerootbase.h"
#include "noaggrcalc.h"
#include "minmaxaggrcalc.h"

namespace vespalib::btree {

template <typename, typename, typename, size_t, size_t>
class BTreeNodeAllocator;
template <typename, typename, typename, size_t, size_t, class>
class BTreeBuilder;
template <typename, typename, typename, size_t, size_t, class>
class BTreeAggregator;

template <typename KeyT,
          typename DataT,
          typename AggrT = NoAggregated,
          typename CompareT = std::less<KeyT>,
          typename TraitsT = BTreeDefaultTraits>
class BTreeRootT : public BTreeRootBase<KeyT, DataT, AggrT,
                                       TraitsT::INTERNAL_SLOTS,
                                       TraitsT::LEAF_SLOTS>
{
public:
    using ParentType = BTreeRootBase<KeyT, DataT, AggrT,
                                     TraitsT::INTERNAL_SLOTS, TraitsT::LEAF_SLOTS>;
    using NodeAllocatorType = typename ParentType::NodeAllocatorType;
    using KeyDataType = BTreeKeyData<KeyT, DataT>;
    using InternalNodeType = typename ParentType::InternalNodeType;
    using LeafNodeType = typename ParentType::LeafNodeType;
    using LeafNodeTempType = BTreeLeafNodeTemp<KeyT, DataT, AggrT, TraitsT::LEAF_SLOTS>;
    using Iterator = BTreeIterator<KeyT, DataT, AggrT, CompareT, TraitsT>;
    using ConstIterator = BTreeConstIterator<KeyT, DataT, AggrT, CompareT, TraitsT>;
    using KeyType = typename ParentType::KeyType;
    using DataType = typename ParentType::DataType;
protected:
    using BTreeRootBaseType = typename ParentType::BTreeRootBaseType;
    using BTreeRootTType = BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>;
    using InternalNodeTypeRefPair = typename InternalNodeType::RefPair;
    using LeafNodeTypeRefPair = typename LeafNodeType::RefPair;
    using ParentType::_root;
    using ParentType::getFrozenRoot;
    using ParentType::getFrozenRootRelaxed;
    using ParentType::isFrozen;

    vespalib::string toString(BTreeNode::Ref node, const NodeAllocatorType &allocator) const;
public:
    /**
     * Read view of the frozen version of the tree.
     * Should be used by reader threads.
     **/
    class FrozenView {
    private:
        BTreeNode::Ref _frozenRoot;
        const NodeAllocatorType *const _allocator;
    public:
        using Iterator = ConstIterator;
        FrozenView() : _frozenRoot(BTreeNode::Ref()),_allocator(nullptr) {}
        FrozenView(BTreeNode::Ref frozenRoot, const NodeAllocatorType & allocator)
            : _frozenRoot(frozenRoot),
              _allocator(&allocator)
        {}
        ConstIterator find(const KeyType& key, CompareT comp = CompareT()) const;
        ConstIterator lowerBound(const KeyType &key, CompareT comp = CompareT()) const;
        ConstIterator upperBound(const KeyType &key, CompareT comp = CompareT()) const;
        ConstIterator begin() const {
            return ConstIterator(_frozenRoot, *_allocator);
        }
        void begin(std::vector<ConstIterator> &where) const {
            where.emplace_back(_frozenRoot, *_allocator);
        }

        BTreeNode::Ref getRoot() const { return _frozenRoot; }
        size_t size() const {
            if (NodeAllocatorType::isValidRef(_frozenRoot)) {
                return _allocator->validLeaves(_frozenRoot);
            }
            return 0u;
        }
        const NodeAllocatorType &getAllocator() const { return *_allocator; }

        const AggrT &getAggregated() const {
            return _allocator->getAggregated(_frozenRoot);
        }

        bool empty() const { return !_frozenRoot.valid(); }

        template <typename FunctionType>
        void foreach_key(FunctionType func) const {
            _allocator->getNodeStore().foreach_key(_frozenRoot, func);
        }

        template <typename FunctionType>
        void foreach(FunctionType func) const {
            _allocator->getNodeStore().foreach(_frozenRoot, func);
        }
    };

private:

    static Iterator findHelper(BTreeNode::Ref root, const KeyType & key,
                               const NodeAllocatorType & allocator, CompareT comp = CompareT());

    static Iterator lowerBoundHelper(BTreeNode::Ref root, const KeyType & key,
                                     const NodeAllocatorType & allocator, CompareT comp = CompareT());

    static Iterator upperBoundHelper(BTreeNode::Ref root, const KeyType & key,
                                     const NodeAllocatorType & allocator, CompareT comp = CompareT());

public:
    BTreeRootT();
    ~BTreeRootT();

    void clear(NodeAllocatorType &allocator);

    Iterator find(const KeyType & key, const NodeAllocatorType &allocator, CompareT comp = CompareT()) const;

    Iterator lowerBound(const KeyType & key, const NodeAllocatorType & allocator, CompareT comp = CompareT()) const;
    Iterator upperBound(const KeyType & key, const NodeAllocatorType & allocator, CompareT comp = CompareT()) const;

    Iterator begin(const NodeAllocatorType &allocator) const {
        return Iterator(_root, allocator);
    }

    FrozenView getFrozenView(const NodeAllocatorType & allocator) const {
        return FrozenView(getFrozenRoot(), allocator);
    }

    size_t size(const NodeAllocatorType &allocator) const;
    size_t frozenSize(const NodeAllocatorType &allocator) const;
    vespalib::string toString(const NodeAllocatorType &allocator) const;
    size_t bitSize(const NodeAllocatorType &allocator) const;
    size_t bitSize(BTreeNode::Ref node, const NodeAllocatorType &allocator) const;
    void thaw(Iterator &itr);
};


template <typename KeyT,
          typename DataT,
          typename AggrT = NoAggregated,
          typename CompareT = std::less<KeyT>,
          typename TraitsT = BTreeDefaultTraits,
          class AggrCalcT = NoAggrCalc>
class BTreeRoot : public BTreeRootT<KeyT, DataT, AggrT,
                                    CompareT, TraitsT>
{
public:
    using ParentType = BTreeRootT<KeyT, DataT, AggrT, CompareT, TraitsT>;
    using Parent2Type = typename ParentType::ParentType;
    using NodeAllocatorType = typename ParentType::NodeAllocatorType;
    using KeyType = typename ParentType::KeyType;
    using DataType = typename ParentType::DataType;
    using LeafNodeType = typename ParentType::LeafNodeType;
    using InternalNodeType = typename ParentType::InternalNodeType;
    using LeafNodeTypeRefPair = typename ParentType::LeafNodeTypeRefPair;
    using InternalNodeTypeRefPair = typename ParentType::InternalNodeTypeRefPair;
    using Iterator = typename ParentType::Iterator;
    using Builder = BTreeBuilder<KeyT, DataT, AggrT,
                                 TraitsT::INTERNAL_SLOTS, TraitsT::LEAF_SLOTS,
                                 AggrCalcT>;
    using Aggregator = BTreeAggregator<KeyT, DataT, AggrT,
                                       TraitsT::INTERNAL_SLOTS,
                                       TraitsT::LEAF_SLOTS,
                                       AggrCalcT>;
    using AggrCalcType = AggrCalcT;
    using Parent2Type::_root;
    using Parent2Type::getFrozenRoot;
    using Parent2Type::getFrozenRootRelaxed;
    using Parent2Type::isFrozen;

protected:
    bool isValid(BTreeNode::Ref node, bool ignoreMinSlots, uint32_t level,
                 const NodeAllocatorType &allocator, CompareT comp, AggrCalcT aggrCalc) const;

public:
    /**
     * Create a tree from a tree builder.  This is a destructive
     * assignment, old content of tree is destroyed and tree
     * builder is emptied when tree grabs ownership of nodes.
     */
    void
    assign(Builder &rhs, NodeAllocatorType &allocator);

    bool
    insert(const KeyType & key, const DataType & data,
           NodeAllocatorType &allocator, CompareT comp = CompareT(),
           const AggrCalcT &aggrCalc = AggrCalcT());

    void
    insert(Iterator &itr,
           const KeyType &key, const DataType &data,
           const AggrCalcT &aggrCalc = AggrCalcT());

    bool
    remove(const KeyType & key,
           NodeAllocatorType &allocator, CompareT comp = CompareT(),
           const AggrCalcT &aggrCalc = AggrCalcT());

    void
    remove(Iterator &itr,
           const AggrCalcT &aggrCalc = AggrCalcT());

    bool isValid(const NodeAllocatorType &allocator, CompareT comp = CompareT()) const;

    bool isValidFrozen(const NodeAllocatorType &allocator, CompareT comp = CompareT()) const;

    void move_nodes(NodeAllocatorType &allocator);
};



extern template class BTreeRootT<uint32_t, uint32_t, NoAggregated>;
extern template class BTreeRootT<uint32_t, BTreeNoLeafData, NoAggregated>;
extern template class BTreeRootT<uint32_t, int32_t, MinMaxAggregated>;
extern template class BTreeRoot<uint32_t, uint32_t, NoAggregated>;
extern template class BTreeRoot<uint32_t, BTreeNoLeafData, NoAggregated>;
extern template class BTreeRoot<uint32_t, int32_t, MinMaxAggregated,
                                std::less<uint32_t>, BTreeDefaultTraits, MinMaxAggrCalc>;

}
