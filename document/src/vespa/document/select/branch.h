// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * @class document::select::Branch
 * @ingroup select
 *
 * @brief Base class for branch nodes in the document selection tree.
 *
 * @author H�kon Humberset
 */

#pragma once

#include <memory>
#include <list>
#include "node.h"

namespace document::select {

class Branch : public Node
{
public:
    explicit Branch(vespalib::stringref name) : Node(name) {}
    Branch(vespalib::stringref name, uint32_t max_depth) : Node(name, max_depth) {}

    bool isLeafNode() const override { return false; }
};

class And : public Branch
{
    std::unique_ptr<Node> _left;
    std::unique_ptr<Node> _right;
public:
    And(std::unique_ptr<Node> left, std::unique_ptr<Node> right,
        const char* name = nullptr);

    ResultList contains(const Context& context) const override {
        return (_left->contains(context) && _right->contains(context));
    }
    ResultList trace(const Context&, std::ostream& trace) const override;
    void visit(Visitor &v) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    const Node& getLeft() const { return *_left; }
    const Node& getRight() const { return *_right; }

    Node::UP clone() const  override{
        return wrapParens(new And(_left->clone(), _right->clone(), _name.c_str()));
    }
};

class Or : public Branch
{
    std::unique_ptr<Node> _left;
    std::unique_ptr<Node> _right;
public:
    Or(std::unique_ptr<Node> left, std::unique_ptr<Node> right,
       const char* name = nullptr);

    ResultList contains(const Context& context) const  override {
        return (_left->contains(context) || _right->contains(context));
    }
    ResultList trace(const Context&, std::ostream& trace) const override;
    void visit(Visitor &v) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    const Node& getLeft() const { return *_left; }
    const Node& getRight() const { return *_right; }

    Node::UP clone() const override {
        return wrapParens(new Or(_left->clone(), _right->clone(), _name.c_str()));
    }
};

class Not : public Branch
{
    std::unique_ptr<Node> _child;
public:
    Not(std::unique_ptr<Node> child, const char* name = nullptr);

    ResultList contains(const Context& context) const override { return !_child->contains(context); }
    ResultList trace(const Context&, std::ostream& trace) const override;
    void visit(Visitor &v) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    const Node& getChild() const { return *_child; }

    Node::UP clone() const override {
        return wrapParens(new Not(_child->clone(), _name.c_str()));
    }
};

}
