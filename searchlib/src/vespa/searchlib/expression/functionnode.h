// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "expressionnode.h"
#include "resultnode.h"

namespace search::expression {

class FunctionNode : public ExpressionNode
{
public:
    DECLARE_NBO_SERIALIZE;
    void visitMembers(vespalib::ObjectVisitor & visitor) const override;
    DECLARE_ABSTRACT_EXPRESSIONNODE(FunctionNode);
    const ResultNode * getResult() const override { return _tmpResult.get(); }
    ResultNode & updateResult() const { return *_tmpResult; }
    virtual void reset() { _tmpResult.reset(nullptr); }

    FunctionNode &setResult(const ResultNode::CP res) { _tmpResult = std::move(res); return *this; }
protected:
    void setResultType(ResultNode::UP res) { _tmpResult = std::move(res); }
    void selectMembers(const vespalib::ObjectPredicate & predicate, vespalib::ObjectOperation & operation) override;
private:
    mutable ResultNode::CP _tmpResult;
};

}
