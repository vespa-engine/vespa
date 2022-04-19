// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::select::ValueNode
 * @ingroup select
 *
 * @brief Node representing a value in the tree
 *
 * @author Håkon Humberset
 */

#pragma once

#include "value.h"
#include "parser_limits.h"

namespace document::select {

class Context;
class Visitor;

class ValueNode : public Printable
{
public:
    using UP = std::unique_ptr<ValueNode>;

    explicit ValueNode(uint32_t max_depth)
        : _max_depth(max_depth), _parentheses(false)
    {
        throw_parse_error_if_max_depth_exceeded();
    }
    ValueNode() : _max_depth(1), _parentheses(false) {}
    ~ValueNode() override = default;

    // See comments for same function in node.h for a description on how and why
    // we track this. Since Node and ValueNode live in completely separate type
    // hierarchies, this particular bit of code duplication is unfortunate but
    // incurs the least cognitive overhead.
    [[nodiscard]] uint32_t max_depth() const noexcept { return _max_depth; }

    void setParentheses() { _parentheses = true; }
    void clearParentheses() { _parentheses = false; }
    bool hadParentheses() const { return _parentheses; }

    virtual std::unique_ptr<Value> getValue(const Context& context) const  = 0;
    virtual void visit(Visitor&) const = 0;
    virtual ValueNode::UP clone() const = 0;
    virtual std::unique_ptr<Value> traceValue(const Context &context, std::ostream &out) const;
private:
    uint32_t _max_depth;
    bool _parentheses; // Set to true if parentheses was used around this part
                       // Set such that we can recreate original query in print.

protected:
    void throw_parse_error_if_max_depth_exceeded() const {
        if (_max_depth > ParserLimits::MaxRecursionDepth) {
            throw_max_depth_exceeded_exception();
        }
    }

    std::unique_ptr<ValueNode> wrapParens(std::unique_ptr<ValueNode> node) const {
        if (_parentheses) {
            node->setParentheses();
        }
        return node;
    }

    std::unique_ptr<Value> defaultTrace(std::unique_ptr<Value> val, std::ostream& out) const;
};

}
