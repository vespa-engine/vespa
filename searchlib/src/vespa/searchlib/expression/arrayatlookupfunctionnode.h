// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "unaryfunctionnode.h"

namespace search {
    namespace attribute {
        class IAttributeVector;
        class IAttributeContext;
    }
namespace expression {

class ArrayAtLookup : public UnaryFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(ArrayAtLookup);
    DECLARE_NBO_SERIALIZE;

    ArrayAtLookup();
    ~ArrayAtLookup();
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

    vespalib::string _attributeName = vespalib::string();
    const search::attribute::IAttributeVector * _attribute = 0;
    DocId _docId = 0;
    BasicAttributeType _basicAttributeType = BAT_STRING;
};

}
}

