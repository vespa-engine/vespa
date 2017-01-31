// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "floatresultnode.h"

namespace search {
namespace expression {

class RelevanceNode : public ExpressionNode
{
public:
    DECLARE_NBO_SERIALIZE;
    DECLARE_EXPRESSIONNODE(RelevanceNode);
    RelevanceNode() : ExpressionNode(), _relevance() { }
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    virtual const ResultNode & getResult() const { return _relevance; }
    void setRelevance(double relevance) { _relevance.set(relevance); }
private:
    virtual void onPrepare(bool preserveAccurateTypes) { (void) preserveAccurateTypes; }
    virtual bool onExecute() const { return true; }
    FloatResultNode _relevance;
};

}
}

