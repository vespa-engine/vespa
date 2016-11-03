// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "basic_nodes.h"
#include <vespa/vespalib/stllike/string.h>
#include <map>

namespace vespalib {
namespace eval {
namespace nodes {

class TensorSum : public Node {
private:
    Node_UP _child;
    vespalib::string _dimension;
public:
    TensorSum(Node_UP child) : _child(std::move(child)), _dimension() {}
    TensorSum(Node_UP child, const vespalib::string &dimension_in)
        : _child(std::move(child)), _dimension(dimension_in) {}
    const vespalib::string &dimension() const { return _dimension; }
    virtual vespalib::string dump(DumpContext &ctx) const {
        vespalib::string str;
        str += "sum(";
        str += _child->dump(ctx);
        if (!_dimension.empty()) {
            str += ",";
            str += _dimension;
        }
        str += ")";
        return str;
    }
    virtual void accept(NodeVisitor &visitor) const;
    virtual size_t num_children() const { return 1; }
    virtual const Node &get_child(size_t idx) const {
        assert(idx == 0);
        return *_child;
    }
    virtual void detach_children(NodeHandler &handler) {
        handler.handle(std::move(_child));
    }
};

} // namespace vespalib::eval::nodes
} // namespace vespalib::eval
} // namespace vespalib
