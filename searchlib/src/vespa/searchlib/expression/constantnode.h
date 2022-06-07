// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const ResultNode * getResult() const override { return _result.get(); }
private:
    void onPrepare(bool preserveAccurateTypes) override { (void) preserveAccurateTypes; }
    bool onExecute() const override { return true; }
    ResultNode::CP _result;
};

}
}

