// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const ResultNode * getResult() const override { return &_relevance; }
    void setRelevance(double relevance) { _relevance.set(relevance); }
private:
    void onPrepare(bool preserveAccurateTypes) override { (void) preserveAccurateTypes; }
    bool onExecute() const override { return true; }
    FloatResultNode _relevance;
};

}
}

