// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "attributenode.h"

namespace search::attribute {
    class IAttributeVector;
    class IAttributeContext;
}

namespace search::expression {

class ArrayAtLookup : public AttributeNode
{
public:
    DECLARE_EXPRESSIONNODE(ArrayAtLookup);
    DECLARE_NBO_SERIALIZE;

    ArrayAtLookup() noexcept;
    ~ArrayAtLookup() override;
    ArrayAtLookup(const vespalib::string &attribute, ExpressionNode::UP arg);
    ArrayAtLookup(const search::attribute::IAttributeVector &attr, ExpressionNode::UP indexArg);
    ArrayAtLookup(const ArrayAtLookup &rhs);
    ArrayAtLookup & operator= (const ArrayAtLookup &rhs);
    bool onExecute() const override;
    void visitMembers(vespalib::ObjectVisitor & visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate & predicate, vespalib::ObjectOperation & operation) override;
private:
    mutable CurrentIndex _currentIndex;
    ExpressionNode::CP   _indexExpression;
};

}
