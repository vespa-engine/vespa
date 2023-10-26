// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "functionnode.h"
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace search {
namespace expression {

class ArrayOperationNode : public FunctionNode
{
public:
    using IAttributeVector = attribute::IAttributeVector;
    using IAttributeContext = attribute::IAttributeContext;

    DECLARE_NBO_SERIALIZE;
    DECLARE_ABSTRACT_EXPRESSIONNODE(ArrayOperationNode);

    ArrayOperationNode();
    ArrayOperationNode(const ArrayOperationNode& rhs);
    // for unit testing
    ArrayOperationNode(IAttributeVector &attr);

    ArrayOperationNode& operator= (const ArrayOperationNode& rhs);

    void setDocId(DocId newDocId) { _docId = newDocId; }

    void wireAttributes(const IAttributeContext &attrCtx) override;

protected:
    DocId docId() const { return _docId; }

    const IAttributeVector& attribute() const {
        return *_attribute;
    }

private:
    vespalib::string _attributeName;
    const attribute::IAttributeVector * _attribute;
    DocId _docId;
};

}
}
