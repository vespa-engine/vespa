// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/functionnode.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace search {
namespace expression {

class ArrayOperationNode : public FunctionNode
{
public:
    typedef search::attribute::IAttributeVector IAttributeVector;
    typedef search::attribute::IAttributeContext IAttributeContext;

    DECLARE_NBO_SERIALIZE;
    DECLARE_ABSTRACT_EXPRESSIONNODE(ArrayOperationNode);

    ArrayOperationNode();
    ArrayOperationNode(const ArrayOperationNode& rhs);
    // for unit testing
    ArrayOperationNode(IAttributeVector &attr);

    ArrayOperationNode& operator= (const ArrayOperationNode& rhs);

    void setDocId(DocId newDocId) { _docId = newDocId; }

    virtual void wireAttributes(const IAttributeContext &attrCtx);

protected:
    DocId docId() const { return _docId; }

    const IAttributeVector& attribute() const {
        return *_attribute;
    }

private:
    vespalib::string _attributeName;
    const search::attribute::IAttributeVector * _attribute;
    DocId _docId;
};

}
}

