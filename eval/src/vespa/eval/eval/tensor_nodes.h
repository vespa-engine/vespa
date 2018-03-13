// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "basic_nodes.h"
#include "function.h"
#include <vespa/vespalib/stllike/string.h>
#include <map>
#include "aggr.h"

namespace vespalib {
namespace eval {
namespace nodes {

class TensorMap : public Node {
private:
    Node_UP  _child;
    Function _lambda;
public:
    TensorMap(Node_UP child, Function lambda)
        : _child(std::move(child)), _lambda(std::move(lambda)) {}
    const Function &lambda() const { return _lambda; }
    vespalib::string dump(DumpContext &ctx) const override {
        vespalib::string str;
        str += "map(";
        str += _child->dump(ctx);
        str += ",";
        str += _lambda.dump_as_lambda();
        str += ")";
        return str;
    }
    void accept(NodeVisitor &visitor) const override;
    size_t num_children() const override { return 1; }
    const Node &get_child(size_t idx) const override {
        (void) idx;
        assert(idx == 0);
        return *_child;
    }
    void detach_children(NodeHandler &handler) override {
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
    vespalib::string dump(DumpContext &ctx) const override {
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
    void accept(NodeVisitor &visitor) const override ;
    size_t num_children() const override { return 2; }
    const Node &get_child(size_t idx) const override {
        assert(idx < 2);
        return (idx == 0) ? *_lhs : *_rhs;
    }
    void detach_children(NodeHandler &handler) override {
        handler.handle(std::move(_lhs));
        handler.handle(std::move(_rhs));
    }
};

class TensorReduce : public Node {
private:
    Node_UP _child;
    Aggr _aggr;
    std::vector<vespalib::string> _dimensions;
public:
    TensorReduce(Node_UP child, Aggr aggr_in, std::vector<vespalib::string> dimensions_in)
        : _child(std::move(child)), _aggr(aggr_in), _dimensions(std::move(dimensions_in)) {}
    const std::vector<vespalib::string> &dimensions() const { return _dimensions; }
    Aggr aggr() const { return _aggr; }
    vespalib::string dump(DumpContext &ctx) const override {
        vespalib::string str;
        str += "reduce(";
        str += _child->dump(ctx);
        str += ",";
        str += *AggrNames::name_of(_aggr);
        for (const auto &dimension: _dimensions) {
            str += ",";
            str += dimension;
        }
        str += ")";
        return str;
    }
    void accept(NodeVisitor &visitor) const override;
    size_t num_children() const override { return 1; }
    const Node &get_child(size_t idx) const override {
        assert(idx == 0);
        return *_child;
    }
    void detach_children(NodeHandler &handler) override {
        handler.handle(std::move(_child));
    }
};

class TensorRename : public Node {
private:
    Node_UP _child;
    std::vector<vespalib::string> _from;
    std::vector<vespalib::string> _to;
public:
    TensorRename(Node_UP child, std::vector<vespalib::string> from_in, std::vector<vespalib::string> to_in)
        : _child(std::move(child)), _from(std::move(from_in)), _to(std::move(to_in)) {}
    const std::vector<vespalib::string> &from() const { return _from; }
    const std::vector<vespalib::string> &to() const { return _to; }
    vespalib::string dump(DumpContext &ctx) const override {
        vespalib::string str;
        str += "rename(";
        str += _child->dump(ctx);
        str += ",";
        str += flatten(_from);
        str += ",";
        str += flatten(_to);
        str += ")";
        return str;
    }
    void accept(NodeVisitor &visitor) const override;
    size_t num_children() const override { return 1; }
    const Node &get_child(size_t idx) const override {
        assert(idx == 0);
        return *_child;
    }
    void detach_children(NodeHandler &handler) override {
        handler.handle(std::move(_child));
    }
    static vespalib::string flatten(const std::vector<vespalib::string> &list) {
        if (list.size() == 1) {
            return list[0];
        }
        vespalib::string str = "(";
        for (size_t i = 0; i < list.size(); ++i) {
            if (i > 0) {
                str += ",";
            }
            str += list[i];
        }
        str += ")";
        return str;
    }
};

class TensorLambda : public Leaf {
private:
    ValueType _type;
    Function _lambda;
public:
    TensorLambda(ValueType type_in, Function lambda)
        : _type(std::move(type_in)), _lambda(std::move(lambda)) {}
    const ValueType &type() const { return _type; }
    const Function &lambda() const { return _lambda; }
    vespalib::string dump(DumpContext &) const override {
        vespalib::string str = _type.to_spec();
        vespalib::string expr = _lambda.dump();
        if (starts_with(expr, "(")) {
            str += expr;
        } else {
            str += "(";
            str += expr;
            str += ")";
        }
        return str;
    }
    void accept(NodeVisitor &visitor) const override;
};

class TensorConcat : public Node {
private:
    Node_UP          _lhs;
    Node_UP          _rhs;
    vespalib::string _dimension;
public:
    TensorConcat(Node_UP lhs, Node_UP rhs, const vespalib::string &dimension_in)
        : _lhs(std::move(lhs)), _rhs(std::move(rhs)), _dimension(dimension_in) {}
    const vespalib::string &dimension() const { return _dimension; }
    vespalib::string dump(DumpContext &ctx) const override {
        vespalib::string str;
        str += "concat(";
        str += _lhs->dump(ctx);
        str += ",";
        str += _rhs->dump(ctx);
        str += ",";
        str += _dimension;
        str += ")";
        return str;
    }
    void accept(NodeVisitor &visitor) const override ;
    size_t num_children() const override { return 2; }
    const Node &get_child(size_t idx) const override {
        assert(idx < 2);
        return (idx == 0) ? *_lhs : *_rhs;
    }
    void detach_children(NodeHandler &handler) override {
        handler.handle(std::move(_lhs));
        handler.handle(std::move(_rhs));
    }
};

} // namespace vespalib::eval::nodes
} // namespace vespalib::eval
} // namespace vespalib
