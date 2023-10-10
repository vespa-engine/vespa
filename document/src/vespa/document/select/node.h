// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
#include "parser_limits.h"

namespace document::select {

class Visitor;

class Node : public Printable
{
protected:
    vespalib::string _name;
    uint32_t _max_depth;
    bool _parentheses; // Set to true if parentheses was used around this part
                       // Set such that we can recreate original query in print.
public:
    using UP = std::unique_ptr<Node>;
    using SP = std::shared_ptr<Node>;

    Node(vespalib::stringref name, uint32_t max_depth)
        : _name(name), _max_depth(max_depth), _parentheses(false)
    {
        throw_parse_error_if_max_depth_exceeded();
    }

    explicit Node(vespalib::stringref name)
        : _name(name), _max_depth(1), _parentheses(false)
    {}
    ~Node() override = default;

    // Depth is explicitly tracked to limit recursion to a sane maximum when building and
    // processing ASTs, as the Bison framework does not have anything useful for us there.
    // The AST is built from the leaves up towards the root, so we can cheaply track depth
    // of subtrees in O(1) time per node by computing a node's own depth based on immediate
    // children at node construction time.
    [[nodiscard]] uint32_t max_depth() const noexcept { return _max_depth; }

    void setParentheses() { _parentheses = true; }
    void clearParentheses() { _parentheses = false; }
    bool hadParentheses() const { return _parentheses; }

    virtual ResultList contains(const Context&) const = 0;
    virtual ResultList trace(const Context&, std::ostream& trace) const = 0;
    virtual bool isLeafNode() const { return true; }
    virtual void visit(Visitor&) const = 0;

    virtual Node::UP clone() const = 0;
protected:
    void throw_parse_error_if_max_depth_exceeded() const {
        if (_max_depth > ParserLimits::MaxRecursionDepth) {
            throw_max_depth_exceeded_exception();
        }
    }

    Node::UP wrapParens(Node* node) const {
        Node::UP ret(node);
        if (_parentheses) {
            ret->setParentheses();
        }
        return ret;
    }
};

}
