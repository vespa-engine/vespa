// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "basic_nodes.h"
#include <vespa/vespalib/stllike/string.h>
#include <map>

namespace vespalib {
namespace eval {
namespace nodes {

class Tensor : public Leaf {
private: 
    typedef std::map<vespalib::string, vespalib::string> Address;
    std::map<Address,double> _cells;
public:
    Tensor() : _cells() {}
    void add(const Address &address, double value) {
        _cells[address] = value;
    }
    const std::map<Address,double> &cells() const { return _cells; }
    virtual vespalib::string dump(DumpContext &) const {
        vespalib::string str;
        str += "{";
        CommaTracker cell_list;
        for (const auto &cell: _cells) {
            cell_list.maybe_comma(str);
            str += "{";
            CommaTracker dimension_list;
            for (const auto &dimension: cell.first) {
                dimension_list.maybe_comma(str);
                str += make_string("%s:%s", dimension.first.c_str(), dimension.second.c_str());
            }
            str += make_string("}:%g", cell.second);
        }
        str += "}";
        return str;
    }
    virtual void accept(NodeVisitor &visitor) const override;
};

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

class TensorMatch : public Node {
private:
    Node_UP _lhs;
    Node_UP _rhs;
public:
    TensorMatch(Node_UP lhs, Node_UP rhs) : _lhs(std::move(lhs)), _rhs(std::move(rhs)) {}
    virtual vespalib::string dump(DumpContext &ctx) const {
        vespalib::string str;
        str += "match(";
        str += _lhs->dump(ctx);
        str += ",";
        str += _rhs->dump(ctx);
        str += ")";
        return str;
    }
    virtual void accept(NodeVisitor &visitor) const;
    virtual size_t num_children() const { return 2; }
    virtual const Node &get_child(size_t idx) const {
        assert(idx < 2);
        return ((idx == 0) ? *_lhs : *_rhs);
    }
    virtual void detach_children(NodeHandler &handler) {
        handler.handle(std::move(_lhs));
        handler.handle(std::move(_rhs));
    }
};

} // namespace vespalib::eval::nodes
} // namespace vespalib::eval
} // namespace vespalib
