// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "unaryfunctionnode.h"

namespace search::attribute { class IAttributeVector; }

namespace search::expression {

class InterpolatedLookup : public UnaryFunctionNode
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
    void setDocId(DocId docId) { _docId = docId; }
private:
    bool onExecute() const override;
    void onPrepareResult() override;
    void wireAttributes(const search::attribute::IAttributeContext &attrCtx) override;
    vespalib::string _attributeName;
    const search::attribute::IAttributeVector * _attribute;
    DocId _docId;
};

}
