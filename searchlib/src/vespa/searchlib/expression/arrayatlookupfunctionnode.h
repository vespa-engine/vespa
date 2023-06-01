// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "unaryfunctionnode.h"

namespace search::attribute {
    class IAttributeVector;
    class IAttributeContext;
}

namespace search::expression {

class ArrayAtLookup : public UnaryFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(ArrayAtLookup);
    DECLARE_NBO_SERIALIZE;

    ArrayAtLookup() noexcept;
    ~ArrayAtLookup() override;
    ArrayAtLookup(const vespalib::string &attribute, ExpressionNode::UP arg);
    ArrayAtLookup(const search::attribute::IAttributeVector &attr, ExpressionNode::UP indexArg);
    ArrayAtLookup(const ArrayAtLookup &rhs);
    ArrayAtLookup & operator= (const ArrayAtLookup &rhs);
    void setDocId(DocId docId) { _docId = docId; }
private:
    bool onExecute() const override;
    void onPrepareResult() override;
    void wireAttributes(const search::attribute::IAttributeContext &attrCtx) override;

    enum BasicAttributeType {
        BAT_INT, BAT_FLOAT, BAT_STRING
    };

    vespalib::string                            _attributeName;
    const search::attribute::IAttributeVector * _attribute;
    DocId                                       _docId;
    BasicAttributeType                          _basicAttributeType;
};

}
