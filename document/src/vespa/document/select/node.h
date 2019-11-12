// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::select::Node
 * @ingroup select
 *
 * @brief Base class for all nodes in the document selection tree.
 *
 * @author Håkon Humberset
 */

#pragma once

#include "resultlist.h"
#include "context.h"

namespace document::select {

class Visitor;

class Node : public Printable
{
protected:
    vespalib::string _name;
    bool _parentheses; // Set to true if parentheses was used around this part
                       // Set such that we can recreate original query in print.
public:
    typedef std::unique_ptr<Node> UP;
    typedef std::shared_ptr<Node> SP;

    Node(vespalib::stringref name) : _name(name), _parentheses(false) {}
    ~Node() override {}

    void setParentheses() { _parentheses = true; }

    void clearParentheses() { _parentheses = false; }

    bool hadParentheses() const { return _parentheses; }

    virtual ResultList contains(const Context&) const = 0;
    virtual ResultList trace(const Context&, std::ostream& trace) const = 0;
    virtual bool isLeafNode() const { return true; }
    virtual void visit(Visitor&) const = 0;

    virtual Node::UP clone() const = 0;
protected:
    Node::UP wrapParens(Node* node) const {
        Node::UP ret(node);
        if (_parentheses) {
            ret->setParentheses();
        }
        return ret;
    }
};

}
