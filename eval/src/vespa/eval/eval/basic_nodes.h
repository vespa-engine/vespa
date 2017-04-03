// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/string_hash.h>
#include <memory>
#include <map>
#include <vector>
#include <cassert>

namespace vespalib {
namespace eval {

namespace nodes { class Node; }

struct NodeTraverser;
struct NodeVisitor;

/**
 * Simple interface for handing the ownership of an AST Node from one
 * actor to another.
 **/
struct NodeHandler {
    virtual void handle(std::unique_ptr<nodes::Node> node) = 0;
    virtual ~NodeHandler() {}
};

namespace nodes {

/**
 * Context object used when dumping an AST to text to keep track of
 * the names of bound values.
 **/
struct DumpContext {
    const std::vector<vespalib::string> &param_names;
    std::vector<vespalib::string>        let_names;
    DumpContext(const std::vector<vespalib::string> &param_names_in)
        : param_names(param_names_in), let_names() {}
};

/**
 * Abstract base class of all nodes in an AST. Each node in an AST has
 * exclusive ownership of its children.
 **/
struct Node {
    virtual bool is_forest() const { return false; }
    virtual bool is_tree() const { return false; }
    virtual bool is_const() const { return false; }
    virtual bool is_param() const { return false; }
    virtual double get_const_value() const;
    void traverse(NodeTraverser &traverser) const;
    virtual vespalib::string dump(DumpContext &ctx) const = 0;
    virtual void accept(NodeVisitor &visitor) const = 0;
    virtual size_t num_children() const = 0;
    virtual const Node &get_child(size_t idx) const = 0;
    virtual void detach_children(NodeHandler &handler) = 0;
    bool is_leaf() const { return (num_children() == 0); }
    virtual ~Node() {}
};
typedef std::unique_ptr<Node> Node_UP;

/**
 * Simple typecasting utility. Intended usage:
 * <pre>
 *   auto number = as<Number>(node);
 *   if (number) {
 *      do_stuff(number->value());
 *   } 
 * </pre>
 **/
template <typename T>
const T *as(const Node &node) { return dynamic_cast<const T *>(&node); }

/**
 * AST leaf nodes should inherit from this class to easy their API
 * burden by not having to care about the concept of children.
 **/
struct Leaf : public Node {
    size_t num_children() const override { return 0; }
    const Node &get_child(size_t) const override {
        abort();
    }
    void detach_children(NodeHandler &) override {}
};

/**
 * Helper class used to insert commas on the appropriate places in
 * comma-separated textual lists.
 **/
struct CommaTracker {
    bool first;
    CommaTracker() : first(true) {}
    void maybe_comma(vespalib::string &dst) {
        if (first) {
            first = false;
        } else {
            dst.push_back(',');
        }
    }
};

class Number : public Leaf {
private:
    double _value;
public:
    Number(double value_in) : _value(value_in) {}
    virtual bool is_const() const override { return true; }
    virtual double get_const_value() const override { return value(); }
    double value() const { return _value; }
    vespalib::string dump(DumpContext &) const override {
        return make_string("%g", _value);
    }
    void accept(NodeVisitor &visitor) const override;
};

class Symbol : public Leaf {
private:
    int _id;
public:
    static const int UNDEF = std::numeric_limits<int>::max();
    explicit Symbol(int id_in) : _id(id_in) {}
    int id() const { return _id; }
    bool is_param() const override {
        return (_id >= 0);
    }
    vespalib::string dump(DumpContext &ctx) const override {
        if (_id >= 0) { // param value
            assert(size_t(_id) < ctx.param_names.size());
            return ctx.param_names[_id];
        } else { // let binding
            int let_offset = -(_id + 1);
            assert(size_t(let_offset) < ctx.let_names.size());
            return ctx.let_names[let_offset];
        }
    }
    void accept(NodeVisitor &visitor) const override;
};

class String : public Leaf {
private:
    vespalib::string _value;
public:
    String(const vespalib::string &value_in) : _value(value_in) {}
    bool is_const() const override { return true; }
    double get_const_value() const override { return hash(); }
    const vespalib::string value() const { return _value; }
    uint32_t hash() const { return hash_code(_value.data(), _value.size()); }
    vespalib::string dump(DumpContext &ctx) const override;
    void accept(NodeVisitor &visitor) const override;
};

class Array : public Node {
private:
    std::vector<Node_UP> _nodes;
    bool _is_const;
public:
    Array() : _nodes(), _is_const(false) {}
    bool is_const() const override { return _is_const; }
    size_t size() const { return _nodes.size(); }
    const Node &get(size_t i) const { return *_nodes[i]; }
    size_t num_children() const override { return size(); }
    const Node &get_child(size_t idx) const override { return get(idx); }
    void detach_children(NodeHandler &handler) override {
        for (size_t i = 0; i < _nodes.size(); ++i) {
            handler.handle(std::move(_nodes[i]));
        }
        _nodes.clear();
    }
    void add(Node_UP node) {
        if (_nodes.empty()) {
            _is_const = node->is_const();
        } else {
            _is_const = (_is_const && node->is_const());
        }
        _nodes.push_back(std::move(node));
    }
    vespalib::string dump(DumpContext &ctx) const override {
        vespalib::string str;
        str += "[";
        CommaTracker node_list;
        for (const auto &node: _nodes) {
            node_list.maybe_comma(str);
            str += node->dump(ctx);
        }
        str += "]";
        return str;
    }
    void accept(NodeVisitor &visitor) const override;
};

class Neg : public Node {
private:
    Node_UP _child;
    bool _is_const;
public:
    Neg(Node_UP child_in) : _child(std::move(child_in)), _is_const(_child->is_const()) {}
    bool is_const() const override { return _is_const; }
    const Node &child() const { return *_child; }
    size_t num_children() const override { return _child ? 1 : 0; }
    const Node &get_child(size_t idx) const override {
        (void) idx;
        assert(idx == 0);
        return child();
    }
    void detach_children(NodeHandler &handler) override {
        handler.handle(std::move(_child));
    }
    vespalib::string dump(DumpContext &ctx) const override {
        vespalib::string str;
        str += "(-";
        str += _child->dump(ctx);
        str += ")";
        return str;
    }
    void accept(NodeVisitor &visitor) const override;
};

class Not : public Node {
private:
    Node_UP _child;
    bool _is_const;
public:
    Not(Node_UP child_in) : _child(std::move(child_in)), _is_const(_child->is_const()) {}
    bool is_const() const override { return _is_const; }
    const Node &child() const { return *_child; }
    size_t num_children() const override { return _child ? 1 : 0; }
    const Node &get_child(size_t idx) const override {
        (void) idx;
        assert(idx == 0);
        return child();
    }
    void detach_children(NodeHandler &handler) override {
        handler.handle(std::move(_child));
    }
    vespalib::string dump(DumpContext &ctx) const override {
        vespalib::string str;
        str += "(!";
        str += _child->dump(ctx);
        str += ")";
        return str;
    }
    void accept(NodeVisitor &visitor) const override;
};

class If : public Node {
private:
    Node_UP _cond;
    Node_UP _true_expr;
    Node_UP _false_expr;
    double  _p_true;
    bool    _is_tree;
public:
    If(Node_UP cond_in, Node_UP true_expr_in, Node_UP false_expr_in, double p_true_in);
    const Node &cond() const { return *_cond; }
    const Node &true_expr() const { return *_true_expr; }
    const Node &false_expr() const { return *_false_expr; }
    double p_true() const { return _p_true; }
    bool is_tree() const override { return _is_tree; }
    size_t num_children() const override {
        return (_cond && _true_expr && _false_expr) ? 3 : 0;
    }
    const Node &get_child(size_t idx) const override {
        assert(idx < 3);
        if (idx == 0) {
            return cond();
        } else if (idx == 1) {
            return true_expr();
        } else {
            return false_expr();
        }
    }
    void detach_children(NodeHandler &handler) override {
        handler.handle(std::move(_cond));
        handler.handle(std::move(_true_expr));
        handler.handle(std::move(_false_expr));
    }
    vespalib::string dump(DumpContext &ctx) const override {
        vespalib::string str;
        str += "if(";
        str += _cond->dump(ctx);
        str += ",";
        str += _true_expr->dump(ctx);
        str += ",";
        str += _false_expr->dump(ctx);
        if (_p_true != 0.5) {
            str += make_string(",%g", _p_true);
        }
        str += ")";
        return str;
    }
    void accept(NodeVisitor &visitor) const override;
};

class Let : public Node {
private:
    vespalib::string _name;
    Node_UP          _value;
    Node_UP          _expr;
public:
    Let(const vespalib::string &name_in, Node_UP value_in, Node_UP expr_in)
        : _name(name_in), _value(std::move(value_in)), _expr(std::move(expr_in)) {}
    const vespalib::string &name() const { return _name; }
    const Node &value() const { return *_value; }
    const Node &expr() const { return *_expr; }
    size_t num_children() const override { return (_value && _expr) ? 2 : 0; }
    const Node &get_child(size_t idx) const override {
        assert(idx < 2);
        return (idx == 0) ? value() : expr();
    }
    void detach_children(NodeHandler &handler) override {
        handler.handle(std::move(_value));
        handler.handle(std::move(_expr));
    }
    vespalib::string dump(DumpContext &ctx) const override {
        vespalib::string str;
        str += "let(";
        str += _name;
        str += ",";
        str += _value->dump(ctx);
        str += ",";
        ctx.let_names.push_back(_name);
        str += _expr->dump(ctx);
        ctx.let_names.pop_back();
        str += ")";
        return str;
    }
    void accept(NodeVisitor &visitor) const override;
};

class Error : public Leaf {
private:
    vespalib::string _message;
public:
    Error(const vespalib::string &message_in) : _message(message_in) {}
    const vespalib::string &message() const { return _message; }
    vespalib::string dump(DumpContext &) const override { return _message; }
    void accept(NodeVisitor &visitor) const override;
};

} // namespace vespalib::eval::nodes
} // namespace vespalib::eval
} // namespace vespalib
