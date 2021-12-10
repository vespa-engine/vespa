// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::attribute {

/*
 * Class describing how to compact a posting store
 */
class PostingStoreCompactionSpec {
    bool           _btree_nodes; // btree nodes
    bool           _store;       // short arrays, b-tree roots, bitvectors
public:
    PostingStoreCompactionSpec() noexcept
        : _btree_nodes(false),
          _store(false)
    {
    }
    PostingStoreCompactionSpec(bool btree_nodes_, bool store_) noexcept
        : _btree_nodes(btree_nodes_),
          _store(store_)
    {
    }
    bool btree_nodes() const noexcept { return _btree_nodes; }
    bool store() const noexcept { return _store; }
};

}
