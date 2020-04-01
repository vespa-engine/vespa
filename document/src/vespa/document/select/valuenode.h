// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::select::ValueNode
 * @ingroup select
 *
 * @brief Node representing a value in the tree
 *
 * @author H�kon Humberset
 */

#pragma once

#include "value.h"

namespace document::select {

class Context;
class Visitor;

class ValueNode : public Printable
{
public:
    using UP = std::unique_ptr<ValueNode>;

    ValueNode() : _parentheses(false) {}
    virtual ~ValueNode() {}

    void setParentheses() { _parentheses = true; }
    void clearParentheses() { _parentheses = false; }
    bool hadParentheses() const { return _parentheses; }

    virtual std::unique_ptr<Value> getValue(const Context& context) const  = 0;
    virtual void visit(Visitor&) const = 0;
    virtual ValueNode::UP clone() const = 0;
    virtual std::unique_ptr<Value> traceValue(const Context &context, std::ostream &out) const;
private:
    bool _parentheses; // Set to true if parentheses was used around this part
                       // Set such that we can recreate original query in print.
protected:
    ValueNode::UP wrapParens(ValueNode* node) const {
        ValueNode::UP ret(node);
        if (_parentheses) {
            ret->setParentheses();
        }
        return ret;
    }

    std::unique_ptr<Value> defaultTrace(std::unique_ptr<Value> val, std::ostream& out) const;
};

}
