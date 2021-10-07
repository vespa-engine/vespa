// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "key_gen.h"
#include "node_visitor.h"
#include "node_traverser.h"

namespace vespalib {
namespace eval {

namespace {

struct ChildReaper : public NodeTraverser, public NodeHandler {
    virtual void handle(nodes::Node_UP) override {}
    virtual bool open(const nodes::Node &) override { return true; }
    virtual void close(const nodes::Node &node) override {
        nodes::Node &mutable_node = const_cast<nodes::Node&>(node);
        mutable_node.detach_children(*this);
    }
};

} // namespace vespalib::nodes::<unnamed>

void
delete_node(nodes::Node_UP node)
{
    if (node) {
        ChildReaper reaper;
        node->traverse(reaper);
    }
}

} // namespace vespalib::eval
} // namespace vespalib
