// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "btree.h"

namespace search {
namespace btree {

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


} // namespace search::btree
} // namespace search

