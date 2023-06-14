// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "attributenode.h"

namespace search::attribute { class IAttributeVector; }

namespace search::expression {

class InterpolatedLookup : public AttributeNode
{
public:
    DECLARE_EXPRESSIONNODE(InterpolatedLookup);
    DECLARE_NBO_SERIALIZE;

    InterpolatedLookup() noexcept;
    ~InterpolatedLookup() override;
    InterpolatedLookup(const vespalib::string &attribute, ExpressionNode::UP arg);
    InterpolatedLookup(const search::attribute::IAttributeVector &attr, ExpressionNode::UP lookupArg);
    InterpolatedLookup(const InterpolatedLookup &rhs);
    InterpolatedLookup & operator= (const InterpolatedLookup &rhs);
    void visitMembers(vespalib::ObjectVisitor & visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate & predicate, vespalib::ObjectOperation & operation) override;
private:
    std::pair<std::unique_ptr<ResultNode>, std::unique_ptr<Handler>>
    createResultHandler(bool preserveAccurateType, const attribute::IAttributeVector & attribute) const override;
    ExpressionNode::CP  _lookupExpression;
};

}
