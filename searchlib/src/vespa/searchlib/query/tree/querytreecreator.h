// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "queryreplicator.h"
#include "stackdumpquerycreator.h"

namespace search::query {

/**
 * Holds functions for creating query trees, either from a stack dump
 * or from another query tree. The traits specify the concrete
 * subclasses to be used when building the tree.
 */
template <class NodeTypes>
struct QueryTreeCreator {
    static Node::UP replicate(const Node &node) {
        return QueryReplicator<NodeTypes>().replicate(node);
    }

    static Node::UP create(search::SimpleQueryStackDumpIterator &iterator) {
        return StackDumpQueryCreator<NodeTypes>().create(iterator);
    }

private:
    QueryTreeCreator();
};

}
