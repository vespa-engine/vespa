// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressionnode.h"

namespace search::expression {

class AggregationRefNode : public ExpressionNode
{
public:
    DECLARE_NBO_SERIALIZE;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;

    DECLARE_EXPRESSIONNODE(AggregationRefNode);
    AggregationRefNode() : _index(0), _expressionNode(nullptr) { }
    AggregationRefNode(uint32_t index) : _index(index), _expressionNode(nullptr) { }
    AggregationRefNode(const AggregationRefNode & rhs);
    AggregationRefNode & operator = (const AggregationRefNode & exprref);

    ExpressionNode *getExpression() { return _expressionNode; }
    const ResultNode * getResult() const override { return _expressionNode->getResult(); }
    void onPrepare(bool preserveAccurateTypes) override { _expressionNode->prepare(preserveAccurateTypes); }
    bool onExecute() const override;

    void locateExpression(ExpressionNodeArray & exprVec) const;
private:
    uint32_t _index;
    mutable ExpressionNode *_expressionNode;
};

}
