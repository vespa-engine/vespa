// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "basic_nodes.h"
#include "function.h"
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
        (void) idx;
        assert(idx == 0);
        return *_child;
    }
    virtual void detach_children(NodeHandler &handler) {
        handler.handle(std::move(_child));
    }
};

class TensorMap : public Node {
private:
    Node_UP  _child;
    Function _lambda;
public:
    TensorMap(Node_UP child, Function lambda)
        : _child(std::move(child)), _lambda(std::move(lambda)) {}
    const Function &lambda() const { return _lambda; }
    virtual vespalib::string dump(DumpContext &ctx) const {
        vespalib::string str;
        str += "map(";
        str += _child->dump(ctx);
        str += ",";
        str += _lambda.dump_as_lambda();
        str += ")";
        return str;
    }
    virtual void accept(NodeVisitor &visitor) const;
    virtual size_t num_children() const { return 1; }
    virtual const Node &get_child(size_t idx) const {
        (void) idx;
        assert(idx == 0);
        return *_child;
    }
    virtual void detach_children(NodeHandler &handler) {
        handler.handle(std::move(_child));
    }
};

class TensorJoin : public Node {
private:
    Node_UP  _lhs;
    Node_UP  _rhs;
    Function _lambda;
public:
    TensorJoin(Node_UP lhs, Node_UP rhs, Function lambda)
        : _lhs(std::move(lhs)), _rhs(std::move(rhs)), _lambda(std::move(lambda)) {}
    const Function &lambda() const { return _lambda; }
    virtual vespalib::string dump(DumpContext &ctx) const {
        vespalib::string str;
        str += "join(";
        str += _lhs->dump(ctx);
        str += ",";
        str += _rhs->dump(ctx);
        str += ",";
        str += _lambda.dump_as_lambda();
        str += ")";
        return str;
    }
    virtual void accept(NodeVisitor &visitor) const;
    virtual size_t num_children() const { return 2; }
    virtual const Node &get_child(size_t idx) const {
        assert(idx < 2);
        return (idx == 0) ? *_lhs : *_rhs;
    }
    virtual void detach_children(NodeHandler &handler) {
        handler.handle(std::move(_lhs));
        handler.handle(std::move(_rhs));
    }
};

} // namespace vespalib::eval::nodes
} // namespace vespalib::eval
} // namespace vespalib
