// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressionnode.h"
#include "serializer.h"
#include <vespa/vespalib/objects/objectoperation.h>
#include <vespa/vespalib/objects/objectpredicate.h>

namespace search {
namespace expression {

class AggregationRefNode : public ExpressionNode
{
public:
    DECLARE_NBO_SERIALIZE;
    class Configure : public vespalib::ObjectOperation, public vespalib::ObjectPredicate
    {
    public:
        Configure(ExpressionNodeArray & exprVec) : _exprVec(exprVec) { }
    private:
        virtual void execute(vespalib::Identifiable &obj) { static_cast<AggregationRefNode&>(obj).locateExpression(_exprVec); }
        virtual bool check(const vespalib::Identifiable &obj) const { return obj.inherits(AggregationRefNode::classId); }
        ExpressionNodeArray & _exprVec;
    };
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;

    DECLARE_EXPRESSIONNODE(AggregationRefNode);
    AggregationRefNode() : _index(0), _expressionNode(NULL) { }
    AggregationRefNode(uint32_t index) : _index(index), _expressionNode(NULL) { }
    AggregationRefNode(const AggregationRefNode & rhs);
    AggregationRefNode & operator = (const AggregationRefNode & exprref);

    ExpressionNode *getExpression() { return _expressionNode; }
    virtual const ResultNode & getResult() const { return _expressionNode->getResult(); }
    virtual void onPrepare(bool preserveAccurateTypes) { _expressionNode->prepare(preserveAccurateTypes); }
    virtual bool onExecute() const;

private:
    void locateExpression(ExpressionNodeArray & exprVec) const;

    uint32_t _index;
    mutable ExpressionNode *_expressionNode;
};

}
}

