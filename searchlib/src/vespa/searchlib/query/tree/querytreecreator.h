// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "queryreplicator.h"
#include "stackdumpquerycreator.h"
#include <vespa/searchlib/query/proto_tree_converter.h>

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

    static Node::UP create(search::QueryStackIterator &iterator) {
        return StackDumpQueryCreator<NodeTypes>().create(iterator);
    }

    QueryTreeCreator() = default;
    ~QueryTreeCreator() = default;

    Node::UP fromIterator(search::QueryStackIterator &iterator) {
        return create(iterator);
    }

    Node::UP fromProto(const search::ProtobufQueryTree& proto_query_tree) {
        return ProtoTreeConverter<NodeTypes>::convert(proto_query_tree);
    }
};

}
