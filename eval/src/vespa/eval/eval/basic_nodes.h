// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"
#include "string_stuff.h"
#include <vespa/vespalib/util/hdr_abort.h>
#include <vespa/vespalib/stllike/hash_fun.h>
#include <vespa/vespalib/util/string_hash.h>
#include <memory>
#include <map>
#include <vector>
#include <cassert>
#include <limits>
#include <string>

namespace vespalib::eval {

namespace nodes { struct Node; }

struct NodeTraverser;
struct NodeVisitor;

/**
 * Simple interface for handing the ownership of an AST Node from one
 * actor to another.
 **/
struct NodeHandler {
    virtual void handle(std::unique_ptr<nodes::Node> node) = 0;
    virtual ~NodeHandler() = default;
};

namespace nodes {

/**
 * Context object used when dumping an AST to text to keep track of
 * the names of bound values.
 **/
struct DumpContext {
    const std::vector<std::string> &param_names;
    DumpContext(const std::vector<std::string> &param_names_in)
        : param_names(param_names_in) {}
};

/**
 * Abstract base class of all nodes in an AST. Each node in an AST has
 * exclusive ownership of its children.
 **/
struct Node {
    virtual bool is_forest() const { return false; }
    virtual bool is_tree() const { return false; }
    virtual bool is_const_double() const { return false; }
    virtual bool is_param() const { return false; }
    virtual double get_const_double_value() const;
    Value::UP get_const_value() const;
    void traverse(NodeTraverser &traverser) const;
    virtual std::string dump(DumpContext &ctx) const = 0;
    virtual void accept(NodeVisitor &visitor) const = 0;
    virtual size_t num_children() const = 0;
    virtual const Node &get_child(size_t idx) const = 0;
    virtual void detach_children(NodeHandler &handler) = 0;
    bool is_leaf() const { return (num_children() == 0); }
    virtual ~Node() = default;
};

using Node_UP = std::unique_ptr<Node>;

/**
 * Simple typecasting utility. Intended usage:
 * <pre>
 *   auto number = as<Number>(node);
 *   if (number) {
 *      do_stuff(number->value());
 *   }
 * </pre>
 **/
template<typename T>
const T *as(const Node &node) { return dynamic_cast<const T *>(&node); }

/**
 * AST leaf nodes should inherit from this class to easy their API
 * burden by not having to care about the concept of children.
 **/
struct Leaf : public Node {
    size_t num_children() const override { return 0; }
    const Node &get_child(size_t) const override {
        HDR_ABORT("should not be reached");
    }
    void detach_children(NodeHandler &) override {}
};

class Number : public Leaf {
private:
    double _value;
public:
    Number(double value_in) : _value(value_in) {}
    bool is_const_double() const override { return true; }
    double get_const_double_value() const override { return value(); }
    double value() const { return _value; }
    std::string dump(DumpContext &) const override;
    void accept(NodeVisitor &visitor) const override;
};

class Symbol : public Leaf {
private:
    size_t _id;
public:
    explicit Symbol(size_t id_in) : _id(id_in) {}
    bool is_param() const override { return true; }
    size_t id() const { return _id; }
    std::string dump(DumpContext &ctx) const override {
        assert(size_t(_id) < ctx.param_names.size());
        return ctx.param_names[_id];
    }
    void accept(NodeVisitor &visitor) const override;
};

class String : public Leaf {
private:
    std::string _value;
public:
    String(const std::string &value_in) : _value(value_in) {}
    bool is_const_double() const override { return true; }
    double get_const_double_value() const override { return hash2d(_value); }
    const std::string &value() const { return _value; }
    std::string dump(DumpContext &) const override {
        return as_quoted_string(_value);
    }
    void accept(NodeVisitor &visitor) const override;
};

class In : public Node {
private:
    Node_UP              _child;
    std::vector<Node_UP> _entries;
public:
    In(Node_UP child) : _child(std::move(child)), _entries() {}
    void add_entry(Node_UP entry) {
        assert(entry->is_const_double());
        _entries.push_back(std::move(entry));
    }
    size_t num_entries() const { return _entries.size(); }
    const Node &get_entry(size_t idx) const { return *_entries[idx]; }
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
    std::string dump(DumpContext &ctx) const override {
        std::string str;
        str += "(";
        str += _child->dump(ctx);
        str += " in [";
        CommaTracker node_list;
        for (const auto &node: _entries) {
            node_list.maybe_add_comma(str);
            str += node->dump(ctx);
        }
        str += "])";
        return str;
    }
    void accept(NodeVisitor &visitor) const override;
};

class Neg : public Node {
private:
    Node_UP _child;
    bool    _is_const_double;
public:
    Neg(Node_UP child_in) : _child(std::move(child_in)), _is_const_double(_child->is_const_double()) {}
    bool is_const_double() const override { return _is_const_double; }
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
    std::string dump(DumpContext &ctx) const override {
        std::string str;
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
    bool    _is_const_double;
public:
    Not(Node_UP child_in) : _child(std::move(child_in)), _is_const_double(_child->is_const_double()) {}
    bool is_const_double() const override { return _is_const_double; }
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
    std::string dump(DumpContext &ctx) const override {
        std::string str;
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
    std::string dump(DumpContext &ctx) const override;
    void accept(NodeVisitor &visitor) const override;
};

class Error : public Leaf {
private:
    std::string _message;
public:
    Error(const std::string &message_in) : _message(message_in) {}
    const std::string &message() const { return _message; }
    std::string dump(DumpContext &) const override { return _message; }
    void accept(NodeVisitor &visitor) const override;
};

} // namespace vespalib::eval::nodes
} // namespace vespalib::eval
