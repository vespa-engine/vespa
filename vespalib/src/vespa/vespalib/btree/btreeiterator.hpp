// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreeiterator.h"
#include "btreeaggregator.h"
#include "btreenode.hpp"
#include <vespa/vespalib/util/hdr_abort.h>

namespace vespalib::btree {

template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::
BTreeIteratorBase(const BTreeIteratorBase &other)
    : _leaf(other._leaf),
      _pathSize(other._pathSize),
      _allocator(other._allocator),
      _leafRoot(other._leafRoot),
      _compatLeafNode()
{
    for (size_t i = 0; i < _pathSize; ++i) {
        _path[i] = other._path[i];
    }
    if (other._compatLeafNode) {
        _compatLeafNode = std::make_unique<LeafNodeTempType>(*other._compatLeafNode);
    }
    if (other._leaf.getNode() == other._compatLeafNode.get()) {
        _leaf.setNode(_compatLeafNode.get());
    }
    if (other._leafRoot == other._compatLeafNode.get()) {
        _leafRoot = _compatLeafNode.get();
    }
}

template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
void
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::
swap(BTreeIteratorBase & other)
{
    std::swap(_leaf, other._leaf);
    std::swap(_pathSize, other._pathSize);
    std::swap(_path, other._path);
    std::swap(_allocator, other._allocator);
    std::swap(_leafRoot, other._leafRoot);
    std::swap(_compatLeafNode, other._compatLeafNode);
}


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
void
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::
clearPath(uint32_t pathSize) noexcept
{
    uint32_t level = _pathSize;
    while (level > pathSize) {
        --level;
        _path[level].invalidate();
    }
    _pathSize = pathSize;
}


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE> &
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::
operator=(const BTreeIteratorBase &other)
{
    if (&other == this) {
        return *this;
    }
    BTreeIteratorBase tmp(other);
    swap(tmp);
    return *this;
}

template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::~BTreeIteratorBase() = default;

template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
void
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::end() noexcept
{
    if (_pathSize == 0) {
        if (_leafRoot == nullptr)
            return;
        _leaf.invalidate();
        return;
    }
    uint32_t level = _pathSize - 1;
    PathElement &pe = _path[level];
    const InternalNodeType *inode = pe.getNode();
    uint32_t idx = inode->validSlots();
    pe.setIdx(idx);
    BTreeNode::Ref childRef = inode->getChild(idx - 1);
    while (level > 0) {
        --level;
        assert(!_allocator->isLeafRef(childRef));
        inode = _allocator->mapInternalRef(childRef);
        idx = inode->validSlots();
        _path[level].setNodeAndIdx(inode, idx);
        childRef = inode->getChild(idx - 1);
        assert(childRef.valid());
    }
    assert(_allocator->isLeafRef(childRef));
    _leaf.invalidate();
}


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
void
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::end(BTreeNode::Ref rootRef) noexcept
{
    if (!rootRef.valid()) {
        setupEmpty();
        return;
    }
    if (_allocator->isLeafRef(rootRef)) {
        clearPath(0u);
        const LeafNodeType *lnode = _allocator->mapLeafRef(rootRef);
        _leafRoot = lnode;
        _leaf.invalidate();
        return;
    }
    _leafRoot = nullptr;
    const InternalNodeType *inode = _allocator->mapInternalRef(rootRef);
    uint32_t idx = inode->validSlots();
    uint32_t pidx = inode->getLevel();
    clearPath(pidx);
    --pidx;
    assert(pidx < PATH_SIZE);
    _path[pidx].setNodeAndIdx(inode, idx);
    BTreeNode::Ref childRef = inode->getChild(idx - 1);
    assert(childRef.valid());
    while (pidx != 0) {
        --pidx;
        inode = _allocator->mapInternalRef(childRef);
        idx = inode->validSlots();
        assert(idx > 0u);
        _path[pidx].setNodeAndIdx(inode, idx);
        childRef = inode->getChild(idx - 1);
        assert(childRef.valid());
    }
    _leaf.invalidate();
}


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
void
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::
findNextLeafNode() noexcept
{
    uint32_t pidx;
    for (pidx = 0; pidx < _pathSize; ++pidx) {
        PathElement & elem = _path[pidx];
        const InternalNodeType * inode = elem.getNode();
        elem.incIdx(); // advance to the next child
        if (elem.getIdx() < inode->validSlots()) {
            BTreeNode::Ref node = inode->getChild(elem.getIdx());
            while (pidx > 0) {
                // find the first leaf node under this child and update path
                inode = _allocator->mapInternalRef(node);
                pidx--;
                _path[pidx].setNodeAndIdx(inode, 0u);
                node = inode->getChild(0);
            }
            _leaf.setNodeAndIdx(_allocator->mapLeafRef(node), 0u);
            return;
        }
    }
    _leaf.invalidate();
}


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
void
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::
findPrevLeafNode() noexcept
{
    uint32_t pidx;
    for (pidx = 0; pidx < _pathSize; ++pidx) {
        PathElement & elem = _path[pidx];
        const InternalNodeType * inode = elem.getNode();
        if (elem.getIdx() > 0u) {
            elem.decIdx(); // advance to the previous child
            BTreeNode::Ref node = inode->getChild(elem.getIdx());
            while (pidx > 0) {
                // find the last leaf node under this child and update path
                inode = _allocator->mapInternalRef(node);
                uint16_t slot = inode->validSlots() - 1;
                pidx--;
                _path[pidx].setNodeAndIdx(inode, slot);
                node = inode->getChild(slot);
            }
            const LeafNodeType *lnode(_allocator->mapLeafRef(node));
            _leaf.setNodeAndIdx(lnode, lnode->validSlots() - 1);
            return;
        }
    }
    // XXX: position wraps around for now, to end of list.
    end();
}


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
void
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::begin() noexcept
{
    uint32_t pidx = _pathSize;
    if (pidx > 0u) {
        --pidx;
        PathElement &elem = _path[pidx];
        elem.setIdx(0);
        BTreeNode::Ref node = elem.getNode()->getChild(0);
        while (pidx > 0) {
            // find the first leaf node under this child and update path
            const InternalNodeType * inode = _allocator->mapInternalRef(node);
            pidx--;
            _path[pidx].setNodeAndIdx(inode, 0u);
            node = inode->getChild(0);
        }
        _leaf.setNodeAndIdx(_allocator->mapLeafRef(node), 0u);
    } else {
        _leaf.setNodeAndIdx(_leafRoot, 0u);
    }
}


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
void
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::begin(BTreeNode::Ref rootRef) noexcept
{
    if (!rootRef.valid()) {
        setupEmpty();
        return;
    }
    if (_allocator->isLeafRef(rootRef)) {
        clearPath(0u);
        const LeafNodeType *lnode = _allocator->mapLeafRef(rootRef);
        _leafRoot = lnode;
        _leaf.setNodeAndIdx(lnode, 0u);
        return;
    }
    _leafRoot = nullptr;
    const InternalNodeType *inode = _allocator->mapInternalRef(rootRef);
    uint32_t pidx = inode->getLevel();
    clearPath(pidx);
    --pidx;
    assert(pidx < PATH_SIZE);
    _path[pidx].setNodeAndIdx(inode, 0);
    BTreeNode::Ref childRef = inode->getChild(0);
    assert(childRef.valid());
    while (pidx != 0) {
        --pidx;
        inode = _allocator->mapInternalRef(childRef);
        _path[pidx].setNodeAndIdx(inode, 0);
        childRef = inode->getChild(0);
        assert(childRef.valid());
    }
    _leaf.setNodeAndIdx(_allocator->mapLeafRef(childRef), 0u);
}


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
void
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::rbegin() noexcept
{
    uint32_t pidx = _pathSize;
    if (pidx > 0u) {
        --pidx;
        PathElement &elem = _path[pidx];
        const InternalNodeType * inode = elem.getNode();
        uint16_t slot = inode->validSlots() - 1;
        elem.setIdx(slot);
        BTreeNode::Ref node = inode->getChild(slot);
        while (pidx > 0) {
            // find the last leaf node under this child and update path
            inode = _allocator->mapInternalRef(node);
            slot = inode->validSlots() - 1;
            pidx--;
            _path[pidx].setNodeAndIdx(inode, slot);
            node = inode->getChild(slot);
        }
        const LeafNodeType *lnode(_allocator->mapLeafRef(node));
        _leaf.setNodeAndIdx(lnode, lnode->validSlots() - 1);
    } else {
        _leaf.setNodeAndIdx(_leafRoot, (_leafRoot != nullptr) ? _leafRoot->validSlots() - 1 : 0u);
    }
}


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
const AggrT &
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::
getAggregated() const noexcept
{
    // XXX: Undefined behavior if tree is empty.
    uint32_t pidx = _pathSize;
    if (pidx > 0u) {
        return _path[pidx - 1].getNode()->getAggregated();
    } else if (_leafRoot != nullptr) {
        return _leafRoot->getAggregated();
    } else {
        return LeafNodeType::getEmptyAggregated();
    }
}


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
size_t
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::
position(uint32_t levels) const noexcept
{
    assert(_pathSize >= levels);
    if (_leaf.getNode() == nullptr)
        return size();
    size_t res = _leaf.getIdx();
    if (levels == 0)
        return res;
    {
        const PathElement & elem = _path[0];
        const InternalNodeType * inode = elem.getNode();
        uint32_t slots = inode->validSlots();
        if (elem.getIdx() * 2 > slots) {
            res += inode->validLeaves();
            for (uint32_t c = elem.getIdx(); c < slots; ++c) {
                BTreeNode::Ref node = inode->getChild(c);
                const LeafNodeType *lnode = _allocator->mapLeafRef(node);
                res -= lnode->validSlots();
            }
        } else {
            for (uint32_t c = 0; c < elem.getIdx(); ++c) {
                BTreeNode::Ref node = inode->getChild(c);
                const LeafNodeType *lnode = _allocator->mapLeafRef(node);
                res += lnode->validSlots();
            }
        }
    }
    for (uint32_t pidx = 1; pidx < levels; ++pidx) {
        const PathElement & elem = _path[pidx];
        const InternalNodeType * inode = elem.getNode();
        uint32_t slots = inode->validSlots();
        if (elem.getIdx() * 2 > slots) {
            res += inode->validLeaves();
            for (uint32_t c = elem.getIdx(); c < slots; ++c) {
                BTreeNode::Ref node = inode->getChild(c);
                const InternalNodeType *jnode = _allocator->mapInternalRef(node);
                res -= jnode->validLeaves();
            }
        } else {
            for (uint32_t c = 0; c < elem.getIdx(); ++c) {
                BTreeNode::Ref node = inode->getChild(c);
                const InternalNodeType *jnode = _allocator->mapInternalRef(node);
                res += jnode->validLeaves();
            }
        }
    }
    return res;
}


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::
BTreeIteratorBase(BTreeNode::Ref root, const NodeAllocatorType &allocator) noexcept
    : _leaf(nullptr, 0u),
      _path(),
      _pathSize(0),
      _allocator(&allocator),
      _leafRoot(nullptr),
      _compatLeafNode()
{
    begin(root);
}


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
template <class AggrCalcT>
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::
BTreeIteratorBase(const KeyDataType *shortArray,
                  uint32_t arraySize,
                  const NodeAllocatorType &allocator,
                  [[maybe_unused]] const AggrCalcT &aggrCalc)
    : _leaf(nullptr, 0u),
      _path(),
      _pathSize(0),
      _allocator(&allocator),
      _leafRoot(nullptr),
      _compatLeafNode()
{
    if (arraySize > 0) {
        _compatLeafNode = std::make_unique<LeafNodeTempType>(shortArray, arraySize);
        _leaf.setNode(_compatLeafNode.get());
        _leafRoot = _leaf.getNode();
        if constexpr (AggrCalcT::hasAggregated()) {
            using Aggregator = BTreeAggregator<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, AggrCalcT>;
            Aggregator::recalc(const_cast<LeafNodeType &>(*_leaf.getNode()), aggrCalc);
        }
    }
}


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::
BTreeIteratorBase() noexcept
    : _leaf(nullptr, 0u),
      _path(),
      _pathSize(0),
      _allocator(nullptr),
      _leafRoot(nullptr),
      _compatLeafNode()
{
}


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
size_t
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::
size() const noexcept
{
    if (_pathSize > 0) {
        return _path[_pathSize - 1].getNode()->validLeaves();
    }
    if (_leafRoot != nullptr) {
        return _leafRoot->validSlots();
    }
    return 0u;
}


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
ssize_t
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::
operator-(const BTreeIteratorBase &rhs) const noexcept
{
    if (_leaf.getNode() == nullptr) {
        if (rhs._leaf.getNode() == nullptr)
            return 0;
        // *this might not be normalized (i.e. default constructor)
        return rhs.size() - rhs.position(rhs._pathSize);
    } else if (rhs._leaf.getNode() == nullptr) {
        // rhs might not be normalized (i.e. default constructor)
        return position(_pathSize) - size();
    }
    assert(_pathSize == rhs._pathSize);
    if (_pathSize != 0) {
        uint32_t pidx = _pathSize;
        while (pidx > 0) {
            if (_path[pidx - 1].getIdx() != rhs._path[pidx - 1].getIdx())
                break;
            --pidx;
        }
        return position(pidx) - rhs.position(pidx);
    } else {
        return position(0) - rhs.position(0);
    }
}


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
bool
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::
identical(const BTreeIteratorBase &rhs) const noexcept
{
    if (_pathSize != rhs._pathSize || _leaf != rhs._leaf) {
        HDR_ABORT("should not be reached");
    }
    for (uint32_t level = 0; level < _pathSize; ++level) {
        if (_path[level] != rhs._path[level]) {
            HDR_ABORT("should not be reached");
        }
    }
    if (_leafRoot != rhs._leafRoot) {
        HDR_ABORT("should not be reached");
    }
    return true;
}


template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
void
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::
set_subtree_position(const InternalNodeType* node, uint32_t level, uint32_t idx, size_t position) noexcept
{
    /*
     * Walk down subtree adjusting iterator for new partial position.
     */
    _path[level].setIdx(idx);
    size_t remaining_steps = position;
    while (level > 0) {
        --level;
        node = _allocator->mapInternalRef(node->getChild(idx));
        assert(remaining_steps < node->validLeaves());
        idx = 0;
        while (idx < node->validSlots()) {
            auto valid_leaves = _allocator->validLeaves(node->getChild(idx));
            if (remaining_steps < valid_leaves) {
                break;
            }
            remaining_steps -= valid_leaves;
            ++idx;
        }
        assert(idx < node->validSlots());
        _path[level].setNodeAndIdx(node, idx);
    }
    auto lnode = _allocator->mapLeafRef(node->getChild(idx));
    assert(remaining_steps < lnode->validSlots());
    _leaf.setNodeAndIdx(lnode, remaining_steps);
}

template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
void
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::
step_forward(size_t steps) noexcept
{
    auto lnode = _leaf.getNode();
    if (lnode == nullptr) {
        return;
    }
    auto idx = _leaf.getIdx();
    if (idx + steps < lnode->validSlots()) {
        _leaf.setIdx(idx + steps);
        return;
    }
    if (_pathSize == 0) {
        _leaf.invalidate();
        return;
    }
    size_t remaining_steps = steps - (lnode->validSlots() - idx);
    uint32_t level = 0;
    uint32_t levels = _pathSize;
    const InternalNodeType* node;
    /*
     * Find intermediate node representing subtree containing old and new
     * position.
     */
    for (;;) {
        node = _path[level].getNode();
        idx = _path[level].getIdx() + 1;
        while (idx < node->validSlots()) {
            auto valid_leaves = _allocator->validLeaves(node->getChild(idx));
            if (remaining_steps < valid_leaves) {
                break;
            }
            remaining_steps -= valid_leaves;
            ++idx;
        }
        if (idx < node->validSlots()) {
            break;
        } else {
            ++level;
            if (level == levels) {
                end();
                return;
            }
        }
    }
    set_subtree_position(node, level, idx, remaining_steps);
}

template <typename KeyT, typename DataT, typename AggrT,
          uint32_t INTERNAL_SLOTS, uint32_t LEAF_SLOTS, uint32_t PATH_SIZE>
void
BTreeIteratorBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS, PATH_SIZE>::step_backward(size_t steps) noexcept
{
    int64_t remaining_steps = steps;
    if (remaining_steps == 0) {
        return;
    }
    if (_leaf.getNode() == nullptr) {
        rbegin();
        if (_leaf.getNode() == nullptr) {
            return;
        }
        --remaining_steps;
    }
    auto idx = _leaf.getIdx();
    if (idx >= remaining_steps) {
        _leaf.setIdx(idx - remaining_steps);
        return;
    }
    if (_pathSize == 0) {
        _leaf.setIdx(0);
        return;
    }
    remaining_steps -= idx;
    uint32_t level = 0;
    uint32_t levels = _pathSize;
    const InternalNodeType* node;
    /*
     * Find intermediate node representing subtree containing old and new
     * position.
     */
    for (;;) {
        node = _path[level].getNode();
        idx = _path[level].getIdx();
        while (idx > 0 && remaining_steps > 0) {
            --idx;
            remaining_steps -= _allocator->validLeaves(node->getChild(idx));
        }
        if (remaining_steps <= 0) {
            break;
        } else {
            ++level;
            if (level == levels) {
                begin();
                return;
            }
        }
    }
    set_subtree_position(node, level, idx, -remaining_steps);
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
void
BTreeConstIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::
lower_bound(const KeyType & key, CompareT comp) noexcept
{
    if (_pathSize == 0) {
        if (_leafRoot == nullptr)
            return;
        uint32_t idx = _leafRoot->template lower_bound<CompareT>(key, comp);
        if (idx >= _leafRoot->validSlots()) {
            _leaf.invalidate();
        } else {
            _leaf.setNodeAndIdx(_leafRoot, idx);
        }
        return;
    }
    uint32_t level = _pathSize - 1;
    PathElement &pe = _path[level];
    const InternalNodeType *inode = pe.getNode();
    uint32_t idx = inode->template lower_bound<CompareT>(key, comp);
    if (__builtin_expect(idx >= inode->validSlots(), false)) {
        end();
        return;
    }
    pe.setIdx(idx);
    BTreeNode::Ref childRef = inode->getChild(idx);
    while (level > 0) {
        --level;
        assert(!_allocator->isLeafRef(childRef));
        inode = _allocator->mapInternalRef(childRef);
        idx = inode->template lower_bound<CompareT>(key, comp);
        assert(idx < inode->validSlots());
        _path[level].setNodeAndIdx(inode, idx);
        childRef = inode->getChild(idx);
        assert(childRef.valid());
    }
    assert(_allocator->isLeafRef(childRef));
    const LeafNodeType *lnode = _allocator->mapLeafRef(childRef);
    idx = lnode->template lower_bound<CompareT>(key, comp);
    assert(idx < lnode->validSlots());
    _leaf.setNodeAndIdx(lnode, idx);
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
void
BTreeConstIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::
lower_bound(BTreeNode::Ref rootRef, const KeyType & key, CompareT comp) noexcept
{
    if (!rootRef.valid()) {
        setupEmpty();
        return;
    }
    if (_allocator->isLeafRef(rootRef)) {
        clearPath(0u);
        const LeafNodeType *lnode = _allocator->mapLeafRef(rootRef);
        _leafRoot = lnode;
        uint32_t idx = lnode->template lower_bound<CompareT>(key, comp);
        if (idx >= lnode->validSlots()) {
            _leaf.invalidate();
        } else {
            _leaf.setNodeAndIdx(lnode, idx);
        }
        return;
    }
    _leafRoot = nullptr;
    const InternalNodeType *inode = _allocator->mapInternalRef(rootRef);
    uint32_t idx = inode->template lower_bound<CompareT>(key, comp);
    if (idx >= inode->validSlots()) {
        end(rootRef);
        return;
    }
    uint32_t pidx = inode->getLevel();
    clearPath(pidx);
    --pidx;
    assert(pidx < TraitsT::PATH_SIZE);
    _path[pidx].setNodeAndIdx(inode, idx);
    BTreeNode::Ref childRef = inode->getChild(idx);
    assert(childRef.valid());
    while (pidx != 0) {
        --pidx;
        inode = _allocator->mapInternalRef(childRef);
        idx = inode->template lower_bound<CompareT>(key, comp);
        assert(idx < inode->validSlots());
        _path[pidx].setNodeAndIdx(inode, idx);
        childRef = inode->getChild(idx);
        assert(childRef.valid());
    }
    const LeafNodeType *lnode = _allocator->mapLeafRef(childRef);
    idx = lnode->template lower_bound<CompareT>(key, comp);
    assert(idx < lnode->validSlots());
    _leaf.setNodeAndIdx(lnode, idx);
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
void
BTreeConstIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::
seek(const KeyType & key, CompareT comp) noexcept
{
    if (TraitsT::BINARY_SEEK) {
        binarySeek(key, comp);
    } else {
        linearSeek(key, comp);
    }
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
void
BTreeConstIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::
binarySeek(const KeyType & key, CompareT comp) noexcept
{
    const LeafNodeType *lnode = _leaf.getNode();
    uint32_t lidx = _leaf.getIdx() + 1;
    if (lidx < lnode->validSlots()) {
        if (!comp(lnode->getKey(lidx), key)) {
            _leaf.setIdx(lidx);
            return;
        } else {
            ++lidx;
        }
    }
    if (comp(lnode->getLastKey(), key)) {
        uint32_t level = 0;
        uint32_t levels = _pathSize;
        while (level < levels &&
               comp(_path[level].getNode()->getLastKey(), key))
            ++level;
        if (__builtin_expect(level >= levels, false)) {
            end();
            return;
        } else {
            const InternalNodeType *node  = _path[level].getNode();
            uint32_t idx = _path[level].getIdx();
            idx = node->template lower_bound<CompareT>(idx + 1, key, comp);
            _path[level].setIdx(idx);
            while (level > 0) {
                --level;
                node = _allocator->mapInternalRef(node->getChild(idx));
                idx = node->template lower_bound<CompareT>(0, key, comp);
                _path[level].setNodeAndIdx(node, idx);
            }
            lnode = _allocator->mapLeafRef(node->getChild(idx));
            _leaf.setNode(lnode);
            lidx = 0;
        }
    }
    lidx = lnode->template lower_bound<CompareT>(lidx, key, comp);
    _leaf.setIdx(lidx);
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
void
BTreeConstIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::
linearSeek(const KeyType & key, CompareT comp) noexcept
{
    const LeafNodeType *lnode = _leaf.getNode();
    uint32_t lidx = _leaf.getIdx() + 1;
    if (lidx < lnode->validSlots()) {
        if (!comp(lnode->getKey(lidx), key)) {
            _leaf.setIdx(lidx);
            return;
        } else {
            ++lidx;
        }
    }
    if (comp(lnode->getLastKey(), key)) {
        uint32_t level = 0;
        uint32_t levels = _pathSize;
        while (level < levels &&
               comp(_path[level].getNode()->getLastKey(), key))
            ++level;
        if (__builtin_expect(level >= levels, false)) {
            end();
            return;
        } else {
            const InternalNodeType *node  = _path[level].getNode();
            uint32_t idx = _path[level].getIdx();
            do {
                ++idx;
            } while (comp(node->getKey(idx), key));
            _path[level].setIdx(idx);
            while (level > 0) {
                --level;
                node = _allocator->mapInternalRef(node->getChild(idx));
                idx = 0;
                while (comp(node->getKey(idx), key)) {
                    ++idx;
                }
                _path[level].setNodeAndIdx(node, idx);
            }
            lnode = _allocator->mapLeafRef(node->getChild(idx));
            _leaf.setNode(lnode);
            lidx = 0;
        }
    }
    while (comp(lnode->getKey(lidx), key)) {
        ++lidx;
    }
    _leaf.setIdx(lidx);
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
void
BTreeConstIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::
seekPast(const KeyType & key, CompareT comp) noexcept
{
    if (TraitsT::BINARY_SEEK) {
        binarySeekPast(key, comp);
    } else {
        linearSeekPast(key, comp);
    }
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
void
BTreeConstIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::
binarySeekPast(const KeyType & key, CompareT comp) noexcept
{
    const LeafNodeType *lnode = _leaf.getNode();
    uint32_t lidx = _leaf.getIdx() + 1;
    if (lidx < lnode->validSlots()) {
        if (comp(key, lnode->getKey(lidx))) {
            _leaf.setIdx(lidx);
            return;
        } else {
            ++lidx;
        }
    }
    if (!comp(key, lnode->getLastKey())) {
        uint32_t level = 0;
        uint32_t levels = _pathSize;
        while (level < levels &&
               !comp(key, _path[level].getNode()->getLastKey()))
            ++level;
        if (__builtin_expect(level >= levels, false)) {
            end();
            return;
        } else {
            const InternalNodeType *node  = _path[level].getNode();
            uint32_t idx = _path[level].getIdx();
            idx = node->template upper_bound<CompareT>(idx + 1, key, comp);
            _path[level].setIdx(idx);
            while (level > 0) {
                --level;
                node = _allocator->mapInternalRef(node->getChild(idx));
                idx = node->template upper_bound<CompareT>(0, key, comp);
                _path[level].setNodeAndIdx(node, idx);
            }
            lnode = _allocator->mapLeafRef(node->getChild(idx));
            _leaf.setNode(lnode);
            lidx = 0;
        }
    }
    lidx = lnode->template upper_bound<CompareT>(lidx, key, comp);
    _leaf.setIdx(lidx);
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
void
BTreeConstIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::
linearSeekPast(const KeyType & key, CompareT comp) noexcept
{
    const LeafNodeType *lnode = _leaf.getNode();
    uint32_t lidx = _leaf.getIdx() + 1;

    if (lidx < lnode->validSlots()) {
        if (comp(key, lnode->getKey(lidx))) {
            _leaf.setIdx(lidx);
            return;
        } else {
            ++lidx;
        }
    }
    if (!comp(key, lnode->getLastKey())) {
        uint32_t level = 0;
        uint32_t levels = _pathSize;
        while (level < levels &&
               !comp(key, _path[level].getNode()->getLastKey()))
            ++level;
        if (__builtin_expect(level >= levels, false)) {
            end();
            return;
        } else {
            const InternalNodeType *node  = _path[level].getNode();
            uint32_t idx = _path[level].getIdx();
            do {
                ++idx;
            } while (!comp(key, node->getKey(idx)));
            _path[level].setIdx(idx);
            while (level > 0) {
                --level;
                node = _allocator->mapInternalRef(node->getChild(idx));
                idx = 0;
                while (!comp(key, node->getKey(idx))) {
                    ++idx;
                }
                _path[level].setNodeAndIdx(node, idx);
            }
            lnode = _allocator->mapLeafRef(node->getChild(idx));
            _leaf.setNode(lnode);
            lidx = 0;
        }
    }
    while (!comp(key, lnode->getKey(lidx))) {
        ++lidx;
    }
    _leaf.setIdx(lidx);
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
void
BTreeConstIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::
validate(BTreeNode::Ref rootRef, CompareT comp) noexcept
{
    bool frozen = false;
    if (!rootRef.valid()) {
        assert(_pathSize == 0u);
        assert(_leafRoot == nullptr);
        assert(_leaf.getNode() == nullptr);
        return;
    }
    uint32_t level = _pathSize;
    BTreeNode::Ref nodeRef = rootRef;
    const KeyT *parentKey = nullptr;
    const KeyT *leafKey = nullptr;
    if (_leaf.getNode() != nullptr) {
        leafKey = &_leaf.getNode()->getKey(_leaf.getIdx());
    }
    while (level > 0) {
        --level;
        assert(!_allocator->isLeafRef(nodeRef));
        const PathElement &pe = _path[level];
        assert(pe.getNode() == _allocator->mapInternalRef(nodeRef));
        uint32_t idx = pe.getIdx();
        if (leafKey == nullptr) {
            assert(idx == 0 ||
                   idx == pe.getNode()->validSlots());
            if (idx == pe.getNode()->validSlots())
                --idx;
        }
        assert(idx < pe.getNode()->validSlots());
        assert(!frozen || pe.getNode()->getFrozen());
        (void) frozen;
        frozen = pe.getNode()->getFrozen();
        if (parentKey != nullptr) {
            assert(idx + 1 == pe.getNode()->validSlots() ||
                   comp(pe.getNode()->getKey(idx), *parentKey));
            assert(!comp(*parentKey, pe.getNode()->getKey(idx)));
            (void) comp;
        }
        if (leafKey != nullptr) {
            assert(idx == 0 ||
                   comp(pe.getNode()->getKey(idx - 1), *leafKey));
            assert(idx + 1 == pe.getNode()->validSlots() ||
                   comp(*leafKey,  pe.getNode()->getKey(idx + 1)));
            assert(!comp(pe.getNode()->getKey(idx), *leafKey));
            (void) comp;
        }
        parentKey = &pe.getNode()->getKey(idx);
        nodeRef = pe.getNode()->getChild(idx);
        assert(nodeRef.valid());
    }
    assert(_allocator->isLeafRef(nodeRef));
    if (_pathSize == 0) {
        assert(_leafRoot == _allocator->mapLeafRef(nodeRef));
        assert(_leaf.getNode() == nullptr || _leaf.getNode() == _leafRoot);
    } else {
        assert(_leafRoot == nullptr);
        assert(_leaf.getNode() == _allocator->mapLeafRef(nodeRef) ||
               _leaf.getNode() == nullptr);
    }
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
BTreeNode::Ref
BTreeIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::
moveFirstLeafNode(BTreeNode::Ref rootRef)
{
    if (!NodeAllocatorType::isValidRef(rootRef)) {
        assert(_pathSize == 0);
        assert(_leaf.getNode() == nullptr);
        return rootRef;
    }

    assert(_leaf.getNode() != nullptr);
    NodeAllocatorType &allocator = getAllocator();

    if (_pathSize == 0) {
        BTreeNode::Ref newRootRef = rootRef;
        assert(_leaf.getNode() == allocator.mapLeafRef(rootRef));
        if (allocator.getCompacting(rootRef)) {
            auto lPair = allocator.moveLeafNode(_leaf.getNode());
            _leaf.setNode(lPair.data);
            newRootRef = lPair.ref;
        }
        _leaf.setIdx(_leaf.getNode()->validSlots() - 1);
        return newRootRef;
    }

    uint32_t level = _pathSize;
    BTreeNode::Ref newRootRef = rootRef;

    --level;
    InternalNodeType *node = _path[level].getWNode();
    assert(node == allocator.mapInternalRef(rootRef));
    if (allocator.getCompacting(rootRef)) {
        auto iPair = allocator.moveInternalNode(node);
        newRootRef = iPair.ref;
        node = iPair.data;
    }
    _path[level].setNodeAndIdx(node, 0u);
    while (level > 0) {
        --level;
        EntryRef nodeRef = node->getChild(0);
        InternalNodeType *pnode = node;
        node = allocator.mapInternalRef(nodeRef);
        if (allocator.getCompacting(nodeRef)) {
            auto iPair = allocator.moveInternalNode(node);
            nodeRef = iPair.ref;
            node = iPair.data;
            pnode->setChild(0, nodeRef);
        }
        _path[level].setNodeAndIdx(node, 0u);
    }
    EntryRef nodeRef = node->getChild(0);
    _leaf.setNode(allocator.mapLeafRef(nodeRef));
    if (allocator.getCompacting(nodeRef)) {
        auto lPair = allocator.moveLeafNode(_leaf.getNode());
        _leaf.setNode(lPair.data);
        node->setChild(0, lPair.ref);
    }
    _leaf.setIdx(_leaf.getNode()->validSlots() - 1);
    return newRootRef;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
void
BTreeIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::moveNextLeafNode()
{
    uint32_t level = 0;
    uint32_t levels = _pathSize;
    while (level < levels &&
           _path[level].getNode()->validSlots() <= _path[level].getIdx() + 1)
        ++level;
    if (__builtin_expect(level >= levels, false)) {
        end();
        return;
    } else {
        NodeAllocatorType &allocator = getAllocator();
        InternalNodeType *node  = _path[level].getWNode();
        uint32_t idx = _path[level].getIdx();
        ++idx;
        _path[level].setIdx(idx);
        while (level > 0) {
            --level;
            EntryRef nodeRef = node->getChild(idx);
            InternalNodeType *pnode = node;
            node = allocator.mapInternalRef(nodeRef);
            if (allocator.getCompacting(nodeRef)) {
                auto iPair = allocator.moveInternalNode(node);
                nodeRef = iPair.ref;
                node = iPair.data;
                pnode->setChild(idx, nodeRef);
            }
            idx = 0;
            _path[level].setNodeAndIdx(node, idx);
        }
        EntryRef nodeRef = node->getChild(idx);
        _leaf.setNode(allocator.mapLeafRef(nodeRef));
        if (allocator.getCompacting(nodeRef)) {
            auto lPair = allocator.moveLeafNode(_leaf.getNode());
            _leaf.setNode(lPair.data);
            node->setChild(idx, lPair.ref);
        }
        _leaf.setIdx(_leaf.getNode()->validSlots() - 1);
    }
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
void
BTreeIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::writeKey(const KeyType & key) noexcept
{
    LeafNodeType * lnode = getLeafNode();
    lnode->writeKey(_leaf.getIdx(), key);
    // must also update the key towards the root as long as the key is
    // the last one in the current node
    if (_leaf.getIdx() + 1 == lnode->validSlots()) {
        for (uint32_t i = 0; i < _pathSize; ++i) {
            const PathElement & pe = _path[i];
            InternalNodeType *inode = pe.getWNode();
            uint32_t childIdx = pe.getIdx();
            inode->writeKey(childIdx, key);
            if (childIdx + 1 != inode->validSlots()) {
                break;
            }
        }
    }
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
template <class AggrCalcT>
void
BTreeIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::
updateData(const DataType & data, [[maybe_unused]] const AggrCalcT &aggrCalc) noexcept
{
    LeafNodeType * lnode = getLeafNode();
    if constexpr (AggrCalcT::hasAggregated() && AggrCalcT::aggregate_over_values()) {
        AggrT oldca(lnode->getAggregated());
        using Aggregator = BTreeAggregator<KeyT, DataT, AggrT,
                                           TraitsT::INTERNAL_SLOTS,
                                           TraitsT::LEAF_SLOTS,
                                           AggrCalcT>;
        if (aggrCalc.update(lnode->getAggregated(),
                            aggrCalc.getVal(lnode->getData(_leaf.getIdx())),
                            aggrCalc.getVal(data))) {
            lnode->writeData(_leaf.getIdx(), data);
            Aggregator::recalc(*lnode, aggrCalc);
        } else {
            lnode->writeData(_leaf.getIdx(), data);
        }
        AggrT ca(lnode->getAggregated());
        // must also update aggregated values towards the root.
        for (uint32_t i = 0; i < _pathSize; ++i) {
            const PathElement & pe = _path[i];
            InternalNodeType * inode = pe.getWNode();
            AggrT oldpa(inode->getAggregated());
            if (aggrCalc.update(inode->getAggregated(), oldca, ca)) {
                Aggregator::recalc(*inode, *_allocator, aggrCalc);
            }
            AggrT pa(inode->getAggregated());
            oldca = oldpa;
            ca = pa;
        }
    } else {
        lnode->writeData(_leaf.getIdx(), data);
    }
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
BTreeNode::Ref
BTreeIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::thaw(BTreeNode::Ref rootRef)
{
    assert(_leaf.getNode() != nullptr && _compatLeafNode.get() == nullptr);
    if (!_leaf.getNode()->getFrozen())
        return rootRef;
    NodeAllocatorType &allocator = getAllocator();
    if (_pathSize == 0) {
        LeafNodeType *leafNode = allocator.mapLeafRef(rootRef);
        assert(leafNode == _leaf.getNode());
        assert(leafNode == _leafRoot);
        LeafNodeTypeRefPair thawedLeaf = allocator.thawNode(rootRef, leafNode);
        _leaf.setNode(thawedLeaf.data);
        _leafRoot = thawedLeaf.data;
        return thawedLeaf.ref;
    }
    assert(_leafRoot == nullptr);
    assert(_path[_pathSize - 1].getNode() == allocator.mapInternalRef(rootRef));
    BTreeNode::Ref childRef(_path[0].getNode()->getChild(_path[0].getIdx()));
    LeafNodeType *leafNode = allocator.mapLeafRef(childRef);
    assert(leafNode == _leaf.getNode());
    LeafNodeTypeRefPair thawedLeaf = allocator.thawNode(childRef, leafNode);
    _leaf.setNode(thawedLeaf.data);
    childRef = thawedLeaf.ref;
    uint32_t level = 0;
    uint32_t levels = _pathSize;
    while (level < levels) {
        PathElement &pe = _path[level];
        InternalNodeType *node(pe.getWNode());
        BTreeNode::Ref nodeRef = (level + 1 < levels)
                ? _path[level + 1].getNode()->getChild(_path[level + 1].getIdx())
                : rootRef;
        assert(node == allocator.mapInternalRef(nodeRef));
        if (!node->getFrozen()) {
            node->set_child_relaxed(pe.getIdx(), childRef);
            return rootRef;
        }
        InternalNodeTypeRefPair thawed = allocator.thawNode(nodeRef, node);
        node = thawed.data;
        pe.setNode(node);
        node->set_child_relaxed(pe.getIdx(), childRef);
        childRef = thawed.ref;
        ++level;
    }
    return childRef; // Root node was thawed
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
template <class AggrCalcT>
BTreeNode::Ref
BTreeIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::
insertFirst(const KeyType &key, const DataType &data,
            [[maybe_unused]] const AggrCalcT &aggrCalc)
{
    assert(_pathSize == 0);
    assert(_leafRoot == nullptr);
    NodeAllocatorType &allocator = getAllocator();
    LeafNodeTypeRefPair lnode = allocator.allocLeafNode();
    lnode.data->insert(0, key, data);
    if constexpr (AggrCalcT::hasAggregated()) {
        AggrT a;
        if constexpr (AggrCalcT::aggregate_over_values()) {
            aggrCalc.add(a, aggrCalc.getVal(data));
        } else {
            aggrCalc.add(a, aggrCalc.getVal(key));
        }
        lnode.data->getAggregated() = a;
    }
    _leafRoot = lnode.data;
    _leaf.setNodeAndIdx(lnode.data, 0u);
    return lnode.ref;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
bool
BTreeIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::
setLeafNodeIdx(uint32_t idx, const LeafNodeType *splitLeafNode) noexcept
{
    uint32_t leafSlots = _leaf.getNode()->validSlots();
    if (idx >= leafSlots) {
        _leaf.setNodeAndIdx(splitLeafNode, idx - leafSlots);
        if (_pathSize == 0) {
            _leafRoot = splitLeafNode;
        }
        return true;
    } else {
        _leaf.setIdx(idx);
        return false;
    }
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
template <class AggrCalcT>
BTreeNode::Ref
BTreeIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::
addLevel(BTreeNode::Ref rootRef, BTreeNode::Ref splitNodeRef,
         bool inRightSplit, [[maybe_unused]] const AggrCalcT &aggrCalc)
{
    using Aggregator = BTreeAggregator<KeyT, DataT, AggrT,
                                       TraitsT::INTERNAL_SLOTS,
                                       TraitsT::LEAF_SLOTS,
                                       AggrCalcT>;

    NodeAllocatorType &allocator(getAllocator());
    
    InternalNodeTypeRefPair inodePair(allocator.allocInternalNode(_pathSize + 1));
    InternalNodeType *inode = inodePair.data;
    inode->setValidLeaves(allocator.validLeaves(rootRef) +
                          allocator.validLeaves(splitNodeRef));
    inode->insert(0, allocator.getLastKey(rootRef), rootRef);
    inode->insert(1, allocator.getLastKey(splitNodeRef), splitNodeRef);
    if constexpr (AggrCalcT::hasAggregated()) {
        Aggregator::recalc(*inode, allocator, aggrCalc);
    }
    _path[_pathSize].setNodeAndIdx(inode, inRightSplit ? 1u : 0u);
    if (_pathSize == 0) {
        _leafRoot = nullptr;
    }
    ++_pathSize;
    return inodePair.ref;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
BTreeNode::Ref
BTreeIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::
removeLevel(BTreeNode::Ref rootRef, InternalNodeType *rootNode)
{
    BTreeNode::Ref newRoot = rootNode->getChild(0);
    NodeAllocatorType &allocator(getAllocator());
    allocator.holdNode(rootRef, rootNode);
    --_pathSize;
    _path[_pathSize].invalidate();
    if (_pathSize == 0) {
        _leafRoot = _leaf.getNode();
    }
    return newRoot;
}


template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
void
BTreeIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::
removeLast(BTreeNode::Ref rootRef)
{
    NodeAllocatorType &allocator(getAllocator());
    allocator.holdNode(rootRef, getLeafNode());
    _leafRoot = nullptr;
    _leaf.invalidate();
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
void
BTreeIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::adjustGivenNoEntriesToLeftLeafNode()
{
    auto &pathElem = _path[0];
    uint32_t parentIdx = pathElem.getIdx() - 1;
    BTreeNode::Ref leafRef = pathElem.getNode()->getChild(parentIdx);
    const LeafNodeType *leafNode = _allocator->mapLeafRef(leafRef);
    pathElem.setIdx(parentIdx);
    _leaf.setNodeAndIdx(leafNode, leafNode->validSlots());
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
void
BTreeIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::adjustGivenEntriesToLeftLeafNode(uint32_t given)
{
    uint32_t leafIdx = _leaf.getIdx();
    if (leafIdx >= given) {
        _leaf.setIdx(leafIdx - given);
    } else {
        auto &pathElem = _path[0];
        uint32_t parentIdx = pathElem.getIdx() - 1;
        BTreeNode::Ref leafRef = pathElem.getNode()->getChild(parentIdx);
        const LeafNodeType *leafNode = _allocator->mapLeafRef(leafRef);
        leafIdx += leafNode->validSlots();
        assert(given <= leafIdx);
        pathElem.setIdx(parentIdx);
        _leaf.setNodeAndIdx(leafNode, leafIdx - given);
    }
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT, typename TraitsT>
void
BTreeIterator<KeyT, DataT, AggrT, CompareT, TraitsT>::adjustGivenEntriesToRightLeafNode()
{
    uint32_t leafIdx = _leaf.getIdx();
    const LeafNodeType *leafNode = _leaf.getNode();
    if (leafIdx > leafNode->validSlots()) {
        auto &pathElem = _path[0];
        const InternalNodeType *parentNode = pathElem.getNode();
        uint32_t parentIdx = pathElem.getIdx() + 1;
        leafIdx -= leafNode->validSlots();
        BTreeNode::Ref leafRef = parentNode->getChild(parentIdx);
        leafNode = _allocator->mapLeafRef(leafRef);
        assert(leafIdx <= leafNode->validSlots());
        pathElem.setIdx(parentIdx);
        _leaf.setNodeAndIdx(leafNode, leafIdx);
    }
}

}
