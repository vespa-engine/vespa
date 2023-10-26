// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "basic_nodes.h"

namespace vespalib::eval {

/**
 * Interface used when traversing nodes. The open function is called
 * before any children are traversed and the close function is called
 * after all children are traversed. Children are traversed in the
 * order defined by the Node::get_child function. If open returns
 * false; no children of the node will be traversed and close will not
 * be called for the node.
 **/
struct NodeTraverser {

    virtual bool open(const nodes::Node &) = 0;
    virtual void close(const nodes::Node &) = 0;

    virtual ~NodeTraverser() {}
};

}
