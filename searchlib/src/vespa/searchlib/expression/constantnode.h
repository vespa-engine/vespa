// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressionnode.h"
#include "resultnode.h"

namespace search {
namespace expression {

class ConstantNode : public ExpressionNode
{
public:
    DECLARE_NBO_SERIALIZE;
    DECLARE_EXPRESSIONNODE(ConstantNode);
    ConstantNode() : ExpressionNode(), _result() { }
    ConstantNode(ResultNode::UP r) : ExpressionNode(), _result(r.release()) { }
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    virtual const ResultNode & getResult() const { return *_result; }
private:
    virtual void onPrepare(bool preserveAccurateTypes) { (void) preserveAccurateTypes; }
    virtual bool onExecute() const { return true; }
    ResultNode::CP _result;
};

}
}

