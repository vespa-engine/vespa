// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreenode.h"
#include "btreenodeallocator.h"
#include "btreetraits.h"
#include <vespa/fastos/dynamiclibrary.h>

namespace vespalib::btree {

template <typename, typename, typename, typename, typename, class>
class BTreeInserter;
template <typename, typename, typename, size_t, size_t, class>
class BTreeRemoverBase;
template <typename, typename, typename, typename, typename, class>
class BTreeRemover;
template <typename, typename, typename, typename, typename>
class BTreeIterator;

/**
 * Helper class to provide internal or leaf node and position within node.
 */
template <class NodeT>
class NodeElement
{
    template <typename, typename, typename, typename, typename, class>
    friend class BTreeInserter;
    template <typename, typename, typename, size_t, size_t, class>
    friend class BTreeRemoverBase;
    template <typename, typename, typename, typename, typename, class>
    friend class BTreeRemover;
    template <typename, typename, typename, typename, typename>
    friend class BTreeIterator;

    typedef NodeT NodeType;
    typedef typename NodeType::KeyType KeyType;
    typedef typename NodeType::DataType DataType;
    const NodeType *_node;
    uint32_t _idx;

    NodeType *
    getWNode() const
    {
        return const_cast<NodeType *>(_node);
    }

public:
    NodeElement()
        : _node(nullptr),
          _idx(0u)
    {
    }

    NodeElement(const NodeType *node, uint32_t idx)
        : _node(node),
          _idx(idx)
    {
    }

    void
    setNode(const NodeType *node)
    {
        _node = node;
    }

    const NodeType *
    getNode() const
    {
        return _node;
    }

    void
    setIdx(uint32_t idx)
    {
        _idx = idx;
    }

    uint32_t
    getIdx() const
    {
        return _idx;
    }

    void
    incIdx()
    {
        ++_idx;
    }

    void
    decIdx()
    {
        --_idx;
    }

    void
    setNodeAndIdx(const NodeType *node, uint32_t idx)
    {
        _node = node;
        _idx = idx;
    }

    const KeyType &
    getKey() const
    {
        return _node->getKey(_idx);
    }

    const DataType &
    getData() const
    {
        return _node->getData(_idx);
    }

    bool
    valid() const
    {
        return _node != nullptr;
    }

    void
    adjustLeftVictimKilled()
    {
        assert(_idx > 0);
        --_idx;
    }

    void
    adjustSteal(uint32_t stolen)
    {
        assert(_idx + stolen < _node->validSlots());
        _idx += stolen;
    }

    void
    adjustSplit(bool inRightSplit)
    {
        if (inRightSplit)
            ++_idx;
    }

    bool
    adjustSplit(bool inRightSplit, const NodeType *splitNode)
    {
        adjustSplit(inRightSplit);
        if (_idx >= _node->validSlots()) {
            _idx -= _node->validSlots();
            _node = splitNode;
            return true;
        }
        return false;
    }

    void
    swap(NodeElement &rhs)
    {
        std::swap(_node, rhs._node);
        std::swap(_idx, rhs._idx);
    }

    bool
    operator!=(const NodeElement &rhs) const
    {
        return _node != rhs._node ||
            _idx != rhs._idx;
    }
};


/**
 * Base class for B-tree iterators.  It defines all members needed
 * for the iterator and methods that don't depend on tree ordering.
 */
template <typename KeyT,
          typename DataT,
          typename AggrT,
          uint32_t INTERNAL_SLOTS = BTreeDefaultTraits::INTERNAL_SLOTS,
          uint32_t LEAF_SLOTS = BTreeDefaultTraits::LEAF_SLOTS,
          uint32_t PATH_SIZE = BTreeDefaultTraits::PATH_SIZE>
class BTreeIteratorBase
{
protected:
    typedef BTreeNodeAllocator<KeyT, DataT, AggrT,
                               INTERNAL_SLOTS,
                               LEAF_SLOTS> NodeAllocatorType;
    typedef BTreeInternalNode<KeyT, AggrT, INTERNAL_SLOTS> InternalNodeType;
    typedef BTreeLeafNode<KeyT, DataT, AggrT, LEAF_SLOTS>  LeafNodeType;
    typedef typename InternalNodeType::RefPair InternalNodeTypeRefPair;
    typedef typename LeafNodeType::RefPair LeafNodeTypeRefPair;
    typedef BTreeLeafNodeTemp<KeyT, DataT, AggrT, LEAF_SLOTS> LeafNodeTempType;
    typedef BTreeKeyData<KeyT, DataT> KeyDataType;
    typedef KeyT KeyType;
    typedef DataT DataType;
    template <typename, typename, typename, typename, typename, class>
    friend class BTreeInserter;
    template <typename, typename, typename, size_t, size_t, class>
    friend class BTreeRemoverBase;
    template <typename, typename, typename, typename, typename, class>
    friend class BTreeRemover;

    typedef NodeElement<LeafNodeType> LeafElement;

    /**
     * Current leaf node and current index within it.
     */
    LeafElement          _leaf;
    /**
     * Pointer to internal node and index to the child used to
     * traverse down the tree
     */
    typedef NodeElement<InternalNodeType> PathElement;
    /**
     * Path from current leaf node up to the root (path[0] is the
     * parent of the leaf node)
     */
    PathElement _path[PATH_SIZE];
    size_t      _pathSize;

    const NodeAllocatorType *_allocator;

    const LeafNodeType *_leafRoot;  // Root node for small tree/array

    // Temporary leaf node when iterating over short arrays
    std::unique_ptr<LeafNodeTempType> _compatLeafNode;

private:
    /*
     * Find the next leaf node, called by operator++() as needed.
     */
    void findNextLeafNode();

    /*
     * Find the previous leaf node, called by operator--() as needed.
     */
    VESPA_DLL_LOCAL void findPrevLeafNode();

protected:
    /*
     * Report current position in tree.
     *
     * @param pidx    Number of levels above leaf nodes to take into account.
     */
    size_t
    position(uint32_t pidx) const;

    /**
     * Create iterator pointing to first element in the tree referenced
     * by root.
     *
     * @param root       Reference to root of tree
     * @param allocator  B-tree node allocator helper class.
     */
    BTreeIteratorBase(BTreeNode::Ref root, const NodeAllocatorType &allocator);

    /**
     * Compability constructor, creating a temporary tree with only a
     * temporary leaf node owned by the iterator.
     */
    template <class AggrCalcT>
    BTreeIteratorBase(const KeyDataType *shortArray,
                      uint32_t arraySize,
                      const NodeAllocatorType &allocator,
                      const AggrCalcT &aggrCalc);

    /**
     * Default constructor.  Iterator is not associated with a tree.
     */
    BTreeIteratorBase();

    /**
     * Step iterator forwards. If at end then leave it at end.
     */
    BTreeIteratorBase &
    operator++() {
        if (_leaf.getNode() == nullptr) {
            return *this;
        }
        _leaf.incIdx();
        if (_leaf.getIdx() < _leaf.getNode()->validSlots()) {
            return *this;
        }
        findNextLeafNode();
        return *this;
    }

    /**
     * Step iterator backwards.  If at end then place it at last valid
     * position in tree (cf. rbegin())
     */
    BTreeIteratorBase &
    operator--();

    ~BTreeIteratorBase();
    BTreeIteratorBase(const BTreeIteratorBase &other);
    BTreeIteratorBase &operator=(const BTreeIteratorBase &other);

    
    /**
     * Set new tree height and clear portions of path that are now
     * beyond new tree height.  For internal use only.
     *
     * @param pathSize     New tree height (number of levels of internal nodes)
     */
    VESPA_DLL_LOCAL void clearPath(uint32_t pathSize);

    /**
     * Call func with leaf entry key value as argument for all leaf entries in subtree
     * from this iterator position to end of subtree.
     */
    template <typename FunctionType>
    void
    foreach_key_range_start(uint32_t level, FunctionType func) const
    {
        if (level > 0u) {
            --level;
            foreach_key_range_start(level, func);
            auto &store = _allocator->getNodeStore();
            auto node = _path[level].getNode();
            uint32_t idx = _path[level].getIdx();
            node->foreach_key_range(store, idx + 1, node->validSlots(), func);
        } else {
            _leaf.getNode()->foreach_key_range(_leaf.getIdx(), _leaf.getNode()->validSlots(), func);
        }
    }

    /**
     * Call func with leaf entry key value as argument for all leaf entries in subtree
     * from start of subtree until this iterator position is reached (i.e. entries in
     * subtree before this iterator position).
     */
    template <typename FunctionType>
    void
    foreach_key_range_end(uint32_t level, FunctionType func) const
    {
        if (level > 0u) {
            --level;
            auto &store = _allocator->getNodeStore();
            auto node = _path[level].getNode();
            uint32_t eidx = _path[level].getIdx();
            node->foreach_key_range(store, 0, eidx, func);
            foreach_key_range_end(level, func);
        } else {
            _leaf.getNode()->foreach_key_range(0, _leaf.getIdx(), func);
        }
    }
public:

    bool
    operator==(const BTreeIteratorBase & rhs) const {
        if (_leaf.getNode() != rhs._leaf.getNode() ||
            _leaf.getIdx() != rhs._leaf.getIdx()) {
            return false;
        }
        return true;
    }

    bool
    operator!=(const BTreeIteratorBase & rhs) const
    {
        return !operator==(rhs);
    }

    /**
     * Swap iterator with the other.
     *
     * @param rhs  Other iterator.
     */
    void
    swap(BTreeIteratorBase & rhs);

    /**
     * Get key at current iterator location.
     */
    const KeyType &
    getKey() const
    {
        return _leaf.getKey();
    }

    /**
     * Get data at current iterator location.
     */
    const DataType &
    getData() const
    {
        return _leaf.getData();
    }

    /**
     * Check if iterator is at a valid element, i.e. not at end.
     */
    bool
    valid() const
    {
        return _leaf.valid();
    }

    /**
     * Return the number of elements in the tree.
     */
    size_t
    size() const;

    
    /**
     * Return the current position in the tree.
     */
    size_t
    position() const
    {
        return position(_pathSize);
    }

    /**
     * Return the distance between two positions in the tree.
     */
    ssize_t
    operator-(const BTreeIteratorBase &rhs) const;

    /**
     * Return if the tree has data or not (e.g. keys and data or only keys).
     */
    static bool
    hasData()
    {
        return LeafNodeType::hasData();
    }

    /**
     * Move the iterator directly to end.  Used by findHelper method in BTree.
     */
    void
    setupEnd();

    /**
     * Setup iterator to be empty and not be associated with any tree.
     */
    VESPA_DLL_LOCAL void setupEmpty();

    /**
     * Move iterator to beyond last element in the current tree.
     */
    void
    end() __attribute__((noinline));

    /**
     * Move iterator to beyond last element in the given tree.
     *
     * @param rootRef    Reference to root of tree.
     */
    void
    end(BTreeNode::Ref rootRef);

    /**
     * Move iterator to first element in the current tree.
     */
    void
    begin();

    /**
     * Move iterator to first element in the given tree.
     *
     * @param rootRef    Reference to root of tree.
     */
    void
    begin(BTreeNode::Ref rootRef);

    /**
     * Move iterator to last element in the current tree.
     */
    void
    rbegin();

    /*
     * Get aggregated values for the current tree. 
     */
    const AggrT &
    getAggregated() const;

    bool
    identical(const BTreeIteratorBase &rhs) const;

    template <typename FunctionType>
    void
    foreach_key(FunctionType func) const
    {
        if (_pathSize > 0) {
            _path[_pathSize - 1].getNode()->
                foreach_key(_allocator->getNodeStore(), func);
        } else if (_leafRoot != nullptr) {
            _leafRoot->foreach_key(func);
        }
    }

    /**
     * Call func with leaf entry key value as argument for all leaf entries in tree from
     * this iterator position until end_itr position is reached (i.e. entries in
     * range [this iterator, end_itr)).
     */
    template <typename FunctionType>
    void
    foreach_key_range(const BTreeIteratorBase &end_itr, FunctionType func) const
    {
        if (!valid()) {
            return;
        }
        if (!end_itr.valid()) {
            foreach_key_range_start(_pathSize, func);
            return;
        }
        assert(_pathSize == end_itr._pathSize);
        assert(_allocator == end_itr._allocator);
        uint32_t level = _pathSize;
        if (level > 0u) {
            /**
             * Tree has intermediate nodes. Detect lowest shared tree node for this
             * iterator and end_itr.
             */
            uint32_t idx;
            uint32_t eidx;
            do {
                --level;
                assert(_path[level].getNode() == end_itr._path[level].getNode());
                idx = _path[level].getIdx();
                eidx = end_itr._path[level].getIdx();
                if (idx > eidx) {
                    return;
                }
                if (idx != eidx) {
                    ++level;
                    break;
                }
            } while (level != 0);
            if (level > 0u) {
                // Lowest shared node is an intermediate node.
                // Left subtree for child [idx], from this iterator position to end of subtree.
                foreach_key_range_start(level - 1, func);
                auto &store = _allocator->getNodeStore();
                auto node = _path[level - 1].getNode();
                // Any intermediate subtrees for children [idx + 1, eidx).
                node->foreach_key_range(store, idx + 1, eidx, func);
                // Right subtree for child [eidx], from start of subtree to end_itr position.
                end_itr.foreach_key_range_end(level - 1, func);
                return;
            } else {
                // Lowest shared node is a leaf node.
                assert(_leaf.getNode() == end_itr._leaf.getNode());
            }
        }
        uint32_t idx = _leaf.getIdx();
        uint32_t eidx = end_itr._leaf.getIdx();
        if (idx < eidx) {
            _leaf.getNode()->foreach_key_range(idx, eidx, func);
        }
    }
};


/**
 * Iterator class for read access to B-trees.  It defines methods to
 * navigate in the tree, useable for implementing search iterators and
 * for positioning in preparation for tree changes (cf. BTreeInserter and
 * BTreeRemover).
 */
template <typename KeyT,
          typename DataT,
          typename AggrT = NoAggregated,
          typename CompareT = std::less<KeyT>,
          typename TraitsT = BTreeDefaultTraits>
class BTreeConstIterator : public BTreeIteratorBase<KeyT, DataT, AggrT,
                                                    TraitsT::INTERNAL_SLOTS,
                                                    TraitsT::LEAF_SLOTS,
                                                    TraitsT::PATH_SIZE>
{
protected:
    typedef BTreeIteratorBase<KeyT,
                              DataT,
                              AggrT,
                              TraitsT::INTERNAL_SLOTS,
                              TraitsT::LEAF_SLOTS,
                              TraitsT::PATH_SIZE> ParentType;
    typedef typename ParentType::NodeAllocatorType NodeAllocatorType;
    typedef typename ParentType::InternalNodeType InternalNodeType;
    typedef typename ParentType::LeafNodeType LeafNodeType;
    typedef typename ParentType::InternalNodeTypeRefPair
    InternalNodeTypeRefPair;
    typedef typename ParentType::LeafNodeTypeRefPair LeafNodeTypeRefPair;
    typedef typename ParentType::LeafNodeTempType LeafNodeTempType;
    typedef typename ParentType::KeyDataType KeyDataType;
    typedef typename ParentType::KeyType KeyType;
    typedef typename ParentType::DataType DataType;
    typedef typename ParentType::PathElement PathElement;

    using ParentType::_leaf;
    using ParentType::_path;
    using ParentType::_pathSize;
    using ParentType::_allocator;
    using ParentType::_leafRoot;
    using ParentType::_compatLeafNode;
    using ParentType::clearPath;
    using ParentType::setupEmpty;
public:
    using ParentType::end;

protected:
    /** Pointer to seek node and path index to the parent node **/
    typedef std::pair<const BTreeNode *, uint32_t> SeekNode;

public:
    /**
     * Create iterator pointing to first element in the tree referenced
     * by root.
     *
     * @param root       Reference to root of tree
     * @param allocator  B-tree node allocator helper class.
     */
    BTreeConstIterator(BTreeNode::Ref root, const NodeAllocatorType &allocator)
        : ParentType(root, allocator)
    {
    }

    /**
     * Compability constructor, creating a temporary tree with only a
     * temporary leaf node owned by the iterator.
     */
    template <class AggrCalcT>
    BTreeConstIterator(const KeyDataType *shortArray,
                       uint32_t arraySize,
                       const NodeAllocatorType &allocator,
                       const AggrCalcT &aggrCalc)
        : ParentType(shortArray, arraySize, allocator, aggrCalc)
    {
    }

    /**
     * Default constructor.  Iterator is not associated with a tree.
     */
    BTreeConstIterator()
        : ParentType()
    {
    }

    /**
     * Step iterator forwards. If at end then leave it at end.
     */
    BTreeConstIterator &
    operator++()
    {
        ParentType::operator++();
        return *this;
    }

    /**
     * Step iterator backwards.  If at end then place it at last valid
     * position in tree (cf. rbegin())
     */
    BTreeConstIterator &
    operator--()
    {
        ParentType::operator--();
        return *this;
    }

    /**
     * Position iterator at first position with a key that is greater
     * than or equal to the key argument.  The iterator must be set up
     * for the same tree before this method is called.
     *
     * @param key       Key to search for
     * @param comp      Comparator for the tree ordering.
     */
    void
    lower_bound(const KeyType & key, CompareT comp = CompareT());

    /**
     * Position iterator at first position with a key that is greater
     * than or equal to the key argument in the tree referenced by rootRef.
     *
     * @param key       Key to search for
     * @param comp      Comparator for the tree ordering.
     */
    void
    lower_bound(BTreeNode::Ref rootRef,
                const KeyType & key, CompareT comp = CompareT());

    /**
     * Step iterator forwards until it is at a position with a key
     * that is greater than or equal to the key argument.  Original
     * position must be valid with a key that is less than the key argument.
     * 
     * Tree traits determine if binary or linear search is performed within
     * each tree node.
     *
     * @param key       Key to search for
     * @param comp      Comparator for the tree ordering.
     */
    void
    seek(const KeyType &key, CompareT comp = CompareT());

    /**
     * Step iterator forwards until it is at a position with a key
     * that is greater than or equal to the key argument.  Original
     * position must be valid with a key that is less than the key argument.
     * 
     * Binary search is performed within each tree node.
     *
     * @param key       Key to search for
     * @param comp      Comparator for the tree ordering.
     */
    void
    binarySeek(const KeyType &key, CompareT comp = CompareT());

    /**
     * Step iterator forwards until it is at a position with a key
     * that is greater than or equal to the key argument.  Original
     * position must be valid with a key that is less than the key argument.
     * 
     * Linear search is performed within each tree node.
     *
     * @param key       Key to search for
     * @param comp      Comparator for the tree ordering.
     */
    void
    linearSeek(const KeyType &key, CompareT comp = CompareT());

    /**
     * Step iterator forwards until it is at a position with a key
     * that is greater than the key argument.  Original position must
     * be valid with a key that is less than or equal to the key argument.
     * 
     * Tree traits determine if binary or linear search is performed within
     * each tree node.
     *
     * @param key       Key to search for
     * @param comp      Comparator for the tree ordering.
     */
    void
    seekPast(const KeyType &key, CompareT comp = CompareT());

    /**
     * Step iterator forwards until it is at a position with a key
     * that is greater than the key argument.  Original position must
     * be valid with a key that is less than or equal to the key argument.
     * 
     * Binary search is performed within each tree node.
     *
     * @param key       Key to search for
     * @param comp      Comparator for the tree ordering.
     */
    void
    binarySeekPast(const KeyType &key, CompareT comp = CompareT());

    /**
     * Step iterator forwards until it is at a position with a key
     * that is greater than the key argument.  Original position must
     * be valid with a key that is less than or equal to the key argument.
     * 
     * Linear search is performed within each tree node.
     *
     * @param key       Key to search for
     * @param comp      Comparator for the tree ordering.
     */
    void
    linearSeekPast(const KeyType &key, CompareT comp = CompareT());

    /**
     * Validate the iterator as a valid iterator or positioned at
     * end in the tree referenced by rootRef. Validation failure
     * triggers asserts.  This method is for internal debugging use only.
     *
     * @param rootRef  Reference to root of tree to operate on
     * @param comp     Comparator for the tree ordering.
     */
    void
    validate(BTreeNode::Ref rootRef, CompareT comp = CompareT());
};


/**
 * Iterator class for write access to B-trees.  It contains some helper
 * methods used by BTreeInserter and BTreeRemover when modifying a tree.
 */
template <typename KeyT,
          typename DataT,
          typename AggrT = NoAggregated,
          typename CompareT = std::less<KeyT>,
          typename TraitsT = BTreeDefaultTraits>
class BTreeIterator : public BTreeConstIterator<KeyT, DataT, AggrT,
                                                CompareT, TraitsT>
{
public:
    typedef BTreeConstIterator<KeyT,
                               DataT,
                               AggrT,
                               CompareT,
                               TraitsT> ParentType;
    typedef typename ParentType::NodeAllocatorType NodeAllocatorType;
    typedef typename ParentType::InternalNodeType InternalNodeType;
    typedef typename ParentType::LeafNodeType LeafNodeType;
    typedef typename ParentType::InternalNodeTypeRefPair
    InternalNodeTypeRefPair;
    typedef typename ParentType::LeafNodeTypeRefPair LeafNodeTypeRefPair;
    typedef typename ParentType::LeafNodeTempType LeafNodeTempType;
    typedef typename ParentType::KeyDataType KeyDataType;
    typedef typename ParentType::KeyType KeyType;
    typedef typename ParentType::DataType DataType;
    typedef typename ParentType::PathElement PathElement;
    template <typename, typename, typename, typename, typename, class>
    friend class BTreeInserter;
    template <typename, typename, typename, size_t, size_t, class>
    friend class BTreeRemoverBase;
    template <typename, typename, typename, typename, typename, class>
    friend class BTreeRemover;

    using ParentType::_leaf;
    using ParentType::_path;
    using ParentType::_pathSize;
    using ParentType::_allocator;
    using ParentType::_leafRoot;
    using ParentType::_compatLeafNode;
    using ParentType::end;
    using EntryRef = datastore::EntryRef;

    BTreeIterator(BTreeNode::Ref root, const NodeAllocatorType &allocator)
        : ParentType(root, allocator)
    {
    }

    template <class AggrCalcT>
    BTreeIterator(const KeyDataType *shortArray,
                  uint32_t arraySize,
                  const NodeAllocatorType &allocator,
                  const AggrCalcT &aggrCalc)
        : ParentType(shortArray, arraySize, allocator, aggrCalc)
    {
    }

    BTreeIterator()
        : ParentType()
    {
    }

    BTreeIterator &
    operator++()
    {
        ParentType::operator++();
        return *this;
    }

    BTreeIterator &
    operator--()
    {
        ParentType::operator--();
        return *this;
    }

    NodeAllocatorType &
    getAllocator() const
    {
        return const_cast<NodeAllocatorType &>(*_allocator);
    }
    
    BTreeNode::Ref
    moveFirstLeafNode(BTreeNode::Ref rootRef);

    void
    moveNextLeafNode();

    void
    writeData(const DataType &data)
    {
        _leaf.getWNode()->writeData(_leaf.getIdx(), data);
    }

    /**
     * Set a new key for the current iterator position.
     * The new key must have the same semantic meaning as the old key.
     * Typically used when compacting data store containing keys.
     */
    void
    writeKey(const KeyType &key);

    /**
     * Updata data at the current iterator position.  The tree should
     * have been thawed.
     *
     * @param data       New data value
     * @param aggrCalc   Calculator for updating aggregated information.
     */
    template <class AggrCalcT>
    void
    updateData(const DataType &data, const AggrCalcT &aggrCalc);

    /**
     * Thaw a path from the root node down the the current leaf node in
     * the current tree, allowing for updates to be performed without
     * disturbing the frozen version of the tree.
     */
    BTreeNode::Ref
    thaw(BTreeNode::Ref rootRef);

private:
    /* Insert into empty tree */
    template <class AggrCalcT>
    BTreeNode::Ref
    insertFirst(const KeyType &key, const DataType &data,
                const AggrCalcT &aggrCalc);

    LeafNodeType *
    getLeafNode() const
    {
        return _leaf.getWNode();
    }

    bool
    setLeafNodeIdx(uint32_t idx, const LeafNodeType *splitLeafNode);

    void
    setLeafNodeIdx(uint32_t idx)
    {
        _leaf.setIdx(idx);
    }

    uint32_t
    getLeafNodeIdx() const
    {
        return _leaf.getIdx();
    }

    uint32_t
    getPathSize() const
    {
        return _pathSize;
    }

    PathElement &
    getPath(uint32_t pidx)
    {
        return _path[pidx];
    }

    template <class AggrCalcT>
    BTreeNode::Ref
    addLevel(BTreeNode::Ref rootRef, BTreeNode::Ref splitNodeRef,
             bool inRightSplit, const AggrCalcT &aggrCalc);

    BTreeNode::Ref
    removeLevel(BTreeNode::Ref rootRef, InternalNodeType *rootNode);

    void
    removeLast(BTreeNode::Ref rootRef);

    void
    adjustSteal(uint32_t level, bool leftVictimKilled, uint32_t stolen)
    {
        assert(_pathSize > level);
        if (leftVictimKilled) {
            _path[level].adjustLeftVictimKilled();
        }
        if (stolen != 0) {
            if (level > 0) {
                _path[level - 1].adjustSteal(stolen);
            } else {
                _leaf.adjustSteal(stolen);
            }
        }
    }

    void adjustGivenNoEntriesToLeftLeafNode();
    void adjustGivenEntriesToLeftLeafNode(uint32_t given);
    void adjustGivenEntriesToRightLeafNode();
};

extern template class BTreeIteratorBase<uint32_t, uint32_t, NoAggregated>;
extern template class BTreeIteratorBase<uint32_t, BTreeNoLeafData, NoAggregated>;
extern template class BTreeIteratorBase<datastore::EntryRef, BTreeNoLeafData, NoAggregated>;
extern template class BTreeIteratorBase<uint32_t, int32_t, MinMaxAggregated>;
extern template class BTreeConstIterator<uint32_t, uint32_t, NoAggregated>;
extern template class BTreeConstIterator<uint32_t, BTreeNoLeafData, NoAggregated>;
extern template class BTreeConstIterator<uint32_t, int32_t, MinMaxAggregated>;
extern template class BTreeIterator<uint32_t, uint32_t, NoAggregated>;
extern template class BTreeIterator<uint32_t, BTreeNoLeafData, NoAggregated>;
extern template class BTreeIterator<uint32_t, int32_t, MinMaxAggregated>;

}

