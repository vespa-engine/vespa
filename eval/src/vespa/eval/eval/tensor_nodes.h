// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "cell_order.h"
#include "basic_nodes.h"
#include "function.h"
#include "tensor_spec.h"
#include "aggr.h"
#include "string_stuff.h"
#include "value_type_spec.h"
#include <map>
#include <string>
#include <vector>

namespace vespalib::eval::nodes {

class TensorMap : public Node {
private:
    Node_UP _child;
    std::shared_ptr<Function const> _lambda;
public:
    TensorMap(Node_UP child, std::shared_ptr<Function const> lambda)
        : _child(std::move(child)), _lambda(std::move(lambda)) {}
    const Node &child() const { return *_child; }
    const Function &lambda() const { return *_lambda; }
    std::string dump(DumpContext &ctx) const override;
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

class TensorMapSubspaces : public Node {
private:
    Node_UP _child;
    std::shared_ptr<Function const> _lambda;
public:
    TensorMapSubspaces(Node_UP child, std::shared_ptr<Function const> lambda)
        : _child(std::move(child)), _lambda(std::move(lambda)) {}
    const Node &child() const { return *_child; }
    const Function &lambda() const { return *_lambda; }
    std::string dump(DumpContext &ctx) const override;
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

class TensorFilterSubspaces : public Node {
private:
    Node_UP _child;
    std::shared_ptr<Function const> _lambda;
public:
    TensorFilterSubspaces(Node_UP child, std::shared_ptr<Function const> lambda)
        : _child(std::move(child)), _lambda(std::move(lambda)) {}
    const Node &child() const { return *_child; }
    const Function &lambda() const { return *_lambda; }
    std::string dump(DumpContext &ctx) const override;
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
    Node_UP _lhs;
    Node_UP _rhs;
    std::shared_ptr<Function const> _lambda;
public:
    TensorJoin(Node_UP lhs, Node_UP rhs, std::shared_ptr<Function const> lambda)
        : _lhs(std::move(lhs)), _rhs(std::move(rhs)), _lambda(std::move(lambda)) {}
    const Node &lhs() const { return *_lhs; }
    const Node &rhs() const { return *_rhs; }
    const Function &lambda() const { return *_lambda; }
    std::string dump(DumpContext &ctx) const override;
    void accept(NodeVisitor &visitor) const override;
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

class TensorMerge : public Node {
private:
    Node_UP _lhs;
    Node_UP _rhs;
    std::shared_ptr<Function const> _lambda;
public:
    TensorMerge(Node_UP lhs, Node_UP rhs, std::shared_ptr<Function const> lambda)
        : _lhs(std::move(lhs)), _rhs(std::move(rhs)), _lambda(std::move(lambda)) {}
    const Node &lhs() const { return *_lhs; }
    const Node &rhs() const { return *_rhs; }
    const Function &lambda() const { return *_lambda; }
    std::string dump(DumpContext &ctx) const override;
    void accept(NodeVisitor &visitor) const override;
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
    std::vector<std::string> _dimensions;
public:
    TensorReduce(Node_UP child, Aggr aggr_in, std::vector<std::string> dimensions_in)
        : _child(std::move(child)), _aggr(aggr_in), _dimensions(std::move(dimensions_in)) {}
    const Node &child() const { return *_child; }
    Aggr aggr() const { return _aggr; }
    const std::vector<std::string> &dimensions() const { return _dimensions; }
    std::string dump(DumpContext &ctx) const override;
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
    std::vector<std::string> _from;
    std::vector<std::string> _to;
public:
    TensorRename(Node_UP child, std::vector<std::string> from_in, std::vector<std::string> to_in)
        : _child(std::move(child)), _from(std::move(from_in)), _to(std::move(to_in)) {}
    const Node &child() const { return *_child; }
    const std::vector<std::string> &from() const { return _from; }
    const std::vector<std::string> &to() const { return _to; }
    std::string dump(DumpContext &ctx) const override;
    void accept(NodeVisitor &visitor) const override;
    size_t num_children() const override { return 1; }
    const Node &get_child(size_t idx) const override {
        assert(idx == 0);
        return *_child;
    }
    void detach_children(NodeHandler &handler) override {
        handler.handle(std::move(_child));
    }
    static std::string flatten(const std::vector<std::string> &list);
};

class TensorConcat : public Node {
private:
    Node_UP          _lhs;
    Node_UP          _rhs;
    std::string _dimension;
public:
    TensorConcat(Node_UP lhs, Node_UP rhs, const std::string &dimension_in)
        : _lhs(std::move(lhs)), _rhs(std::move(rhs)), _dimension(dimension_in) {}
    const Node &lhs() const { return *_lhs; }
    const Node &rhs() const { return *_rhs; }
    const std::string &dimension() const { return _dimension; }
    std::string dump(DumpContext &ctx) const override;
    void accept(NodeVisitor &visitor) const override;
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

class TensorCellCast : public Node {
private:
    Node_UP  _child;
    CellType _cell_type;
public:
    TensorCellCast(Node_UP child, CellType cell_type)
        : _child(std::move(child)), _cell_type(cell_type) {}
    const Node &child() const { return *_child; }
    CellType cell_type() const { return _cell_type; }
    std::string dump(DumpContext &ctx) const override;
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

class TensorCellOrder : public Node {
private:
    Node_UP  _child;
    CellOrder _cell_order;
public:
    TensorCellOrder(Node_UP child, CellOrder cell_order)
        : _child(std::move(child)), _cell_order(cell_order) {}
    const Node &child() const { return *_child; }
    CellOrder cell_order() const { return _cell_order; }
    std::string dump(DumpContext &ctx) const override;
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

class TensorCreate : public Node {
public:
    using Spec = std::map<TensorSpec::Address, Node_UP>;
private:
    using Child = std::pair<TensorSpec::Address, Node_UP>;
    using ChildList = std::vector<Child>;
    ValueType _type;
    ChildList _cells;
public:
    TensorCreate(ValueType type_in, Spec spec)
        : _type(std::move(type_in)), _cells()
    {
        for (auto &cell: spec) {
            _cells.emplace_back(cell.first, std::move(cell.second));
        }
    }
    const ValueType &type() const { return _type; }
    std::string dump(DumpContext &ctx) const override;
    void accept(NodeVisitor &visitor) const override;
    size_t num_children() const override { return _cells.size(); }
    const Node &get_child(size_t idx) const override {
        assert(idx < _cells.size());
        return *_cells[idx].second;
    }
    const TensorSpec::Address &get_child_address(size_t idx) const {
        assert(idx < _cells.size());
        return _cells[idx].first;
    }
    void detach_children(NodeHandler &handler) override {
        for (Child &child: _cells) {
            handler.handle(std::move(child.second));
        }
    }
};

class TensorLambda : public Node {
private:
    ValueType _type;
    std::vector<size_t> _bindings;
    std::shared_ptr<Function const> _lambda;
public:
    TensorLambda(ValueType type_in, std::vector<size_t> bindings, std::shared_ptr<Function const> lambda)
        : _type(std::move(type_in)), _bindings(std::move(bindings)), _lambda(std::move(lambda))
    {
        assert(_type.is_dense());
        assert(_lambda->num_params() == (_type.dimensions().size() + _bindings.size()));
    }
    const ValueType &type() const { return _type; }
    const std::vector<size_t> &bindings() const { return _bindings; }
    const Function &lambda() const { return *_lambda; }
    std::string dump(DumpContext &) const override;
    void accept(NodeVisitor &visitor) const override;
    size_t num_children() const override { return 0; }
    const Node &get_child(size_t) const override { abort(); }
    void detach_children(NodeHandler &) override {}
};

class TensorPeek : public Node {
public:
    struct MyLabel {
        std::string label;
        Node_UP expr;
        MyLabel(std::string label_in)
            : label(label_in), expr() {}
        MyLabel(Node_UP node)
            : label(""), expr(std::move(node)) {}
        bool is_expr() const { return bool(expr); }
    };
    using Spec = std::map<std::string, MyLabel>;
    using Dim = std::pair<std::string, MyLabel>;
    using DimList = std::vector<Dim>;
private:
    using DimRefs = std::vector<size_t>;
    Node_UP _param;
    DimList _dim_list;
    DimRefs _expr_dims;
public:
    TensorPeek(Node_UP param, Spec spec)
        : _param(std::move(param)), _dim_list(), _expr_dims()
    {
        for (auto &dim: spec) {
            if (dim.second.is_expr()) {
                _expr_dims.push_back(_dim_list.size());
            }
            _dim_list.emplace_back(dim.first, std::move(dim.second));
        }
    }
    const Node &param() const { return *_param; }
    const DimList &dim_list() const { return _dim_list; }
    std::string dump(DumpContext &ctx) const override;
    void accept(NodeVisitor &visitor) const override;
    size_t num_children() const override { return (1 + _expr_dims.size()); }
    const Node &get_child(size_t idx) const override {
        assert(idx < num_children());
        if (idx == 0) {
            return *_param;
        } else {
            return *_dim_list[_expr_dims[idx-1]].second.expr;
        }
    }
    void detach_children(NodeHandler &handler) override {
        handler.handle(std::move(_param));
        for (size_t idx: _expr_dims) {
            handler.handle(std::move(_dim_list[idx].second.expr));
        }
    }
};

}
