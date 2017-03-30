// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "unaryfunctionnode.h"

namespace search {
    namespace attribute { class IAttributeVector; }

namespace expression {

class InterpolatedLookup : public UnaryFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(InterpolatedLookup);
    DECLARE_NBO_SERIALIZE;

    InterpolatedLookup();
    ~InterpolatedLookup();

    InterpolatedLookup(const vespalib::string &attribute,
                       ExpressionNode::UP arg);

    InterpolatedLookup(const search::attribute::IAttributeVector &attr,
                       ExpressionNode::UP lookupArg);

    InterpolatedLookup(const InterpolatedLookup &rhs);

    InterpolatedLookup & operator= (const InterpolatedLookup &rhs);

    void setDocId(DocId docId) { _docId = docId; }
private:
    virtual bool onExecute() const;
    virtual void onPrepareResult();
    virtual void wireAttributes(const search::attribute::IAttributeContext &attrCtx);
    vespalib::string _attributeName;
    const search::attribute::IAttributeVector * _attribute;
    DocId _docId;
};

}
}

