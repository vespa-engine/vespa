// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btree.h"

namespace vespalib::btree {

template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, class AggrCalcT>
BTree<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::BTree()
    : _alloc(),
      _tree()
{
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, class AggrCalcT>
BTree<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::~BTree()
{
    clear();
    _alloc.freeze();
    _alloc.clearHoldLists();
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, class AggrCalcT>
void
BTree<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::compact_worst()
{
    auto to_hold = _alloc.start_compact_worst();
    _tree.move_nodes(_alloc);
    _alloc.finishCompact(to_hold);
}

}
