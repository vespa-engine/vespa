// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btreerootbase.h"
#include <cassert>

namespace vespalib::btree {

template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
BTreeRootBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::BTreeRootBase()
    : _root(BTreeNode::Ref()),
      _frozenRoot(BTreeNode::Ref().ref())
{
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
BTreeRootBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
BTreeRootBase(const BTreeRootBase &rhs)
    : _root(rhs._root),
      _frozenRoot(rhs._frozenRoot.load())
{
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
BTreeRootBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::~BTreeRootBase()
{
    assert(!_root.valid());
#if 0
    assert(!_frozenRoot.valid());
#endif
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
BTreeRootBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS> &
BTreeRootBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
operator=(const BTreeRootBase &rhs)
{
    _root = rhs._root;
    _frozenRoot.store(rhs._frozenRoot.load(), std::memory_order_release);
    return *this;
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
void
BTreeRootBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
freeze(NodeAllocatorType &allocator)
{
    if (NodeAllocatorType::isValidRef(_root)) {
        if (allocator.isLeafRef(_root))
            assert(allocator.mapLeafRef(_root)->getFrozen());
        else
            assert(allocator.mapInternalRef(_root)->getFrozen());
    }
    _frozenRoot.store(_root.ref(), std::memory_order_release);
}


template <typename KeyT, typename DataT, typename AggrT,
          size_t INTERNAL_SLOTS, size_t LEAF_SLOTS>
void
BTreeRootBase<KeyT, DataT, AggrT, INTERNAL_SLOTS, LEAF_SLOTS>::
recursiveDelete(BTreeNode::Ref node, NodeAllocatorType &allocator)
{
    assert(allocator.isValidRef(node));
    if (!allocator.isLeafRef(node)) {
        InternalNodeType * inode = allocator.mapInternalRef(node);
        for (size_t i = 0; i < inode->validSlots(); ++i) {
            recursiveDelete(inode->getChild(i), allocator);
        }
        allocator.holdNode(node, inode);
    } else {
        allocator.holdNode(node, allocator.mapLeafRef(node));
    }
}

}
