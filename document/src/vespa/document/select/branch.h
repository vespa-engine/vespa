// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * @class document::select::Branch
 * @ingroup select
 *
 * @brief Base class for branch nodes in the document selection tree.
 *
 * @author H�kon Humberset
 * @date 2005-06-07
 * @version $Id$
 */

#pragma once

#include <memory>
#include <list>
#include "node.h"

namespace document {
namespace select {

class Branch : public Node
{
public:
    Branch(const vespalib::stringref & name) : Node(name) {}

    virtual bool isLeafNode() const { return false; }
};

class And : public Branch
{
    std::unique_ptr<Node> _left;
    std::unique_ptr<Node> _right;
public:
    And(std::unique_ptr<Node> left, std::unique_ptr<Node> right,
        const char* name = 0);

    virtual ResultList contains(const Context& context) const
        { return (_left->contains(context) && _right->contains(context)); }
    virtual ResultList trace(const Context&, std::ostream& trace) const;
    virtual void visit(Visitor &v) const;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    const Node& getLeft() const { return *_left; }
    const Node& getRight() const { return *_right; }

    Node::UP clone() const {
        return wrapParens(new And(_left->clone(),
                                  _right->clone(),
                                  _name.c_str()));
    }
};

class Or : public Branch
{
    std::unique_ptr<Node> _left;
    std::unique_ptr<Node> _right;
public:
    Or(std::unique_ptr<Node> left, std::unique_ptr<Node> right,
        const char* name = 0);

    virtual ResultList contains(const Context& context) const
        { return (_left->contains(context) || _right->contains(context)); }
    virtual ResultList trace(const Context&, std::ostream& trace) const;
    virtual void visit(Visitor &v) const;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    const Node& getLeft() const { return *_left; }
    const Node& getRight() const { return *_right; }

    Node::UP clone() const {
        return wrapParens(new Or(_left->clone(),
                                 _right->clone(),
                                 _name.c_str()));
    }
};

class Not : public Branch
{
    std::unique_ptr<Node> _child;
public:
    Not(std::unique_ptr<Node> child, const char* name = 0);

    virtual ResultList contains(const Context& context) const
        { return !_child->contains(context); }
    virtual ResultList trace(const Context&, std::ostream& trace) const;
    virtual void visit(Visitor &v) const;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    const Node& getChild() const { return *_child; }

    Node::UP clone() const {
        return wrapParens(new Not(_child->clone(), _name.c_str()));
    }
};

} // select
} // document

