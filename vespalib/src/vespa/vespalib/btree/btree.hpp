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
    _alloc.reclaim_all_memory();
}

template <typename KeyT, typename DataT, typename AggrT, typename CompareT,
          typename TraitsT, class AggrCalcT>
void
BTree<KeyT, DataT, AggrT, CompareT, TraitsT, AggrCalcT>::compact_worst(const datastore::CompactionStrategy& compaction_strategy)
{
    auto compacting_buffers = _alloc.start_compact_worst(compaction_strategy);
    _tree.move_nodes(_alloc);
    compacting_buffers->finish();
}

}
