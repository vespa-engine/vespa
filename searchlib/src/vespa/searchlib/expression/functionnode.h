// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/expressionnode.h>
#include <vespa/searchlib/expression/resultnode.h>

namespace search {
namespace expression {

class FunctionNode : public ExpressionNode
{
public:
    DECLARE_NBO_SERIALIZE;
    virtual void visitMembers(vespalib::ObjectVisitor & visitor) const;
    DECLARE_ABSTRACT_EXPRESSIONNODE(FunctionNode);
    virtual const ResultNode & getResult() const { return *_tmpResult; }
    ResultNode & updateResult() const { return *_tmpResult; }
    virtual void reset() { _tmpResult.reset(NULL); }

    FunctionNode &setResult(const ResultNode::CP res) { _tmpResult = res; return *this; }
protected:
    void setResultType(ResultNode::UP res) { _tmpResult.reset(res.release()); }
    virtual void selectMembers(const vespalib::ObjectPredicate & predicate, vespalib::ObjectOperation & operation);
private:
    mutable ResultNode::CP _tmpResult;
};

}
}

