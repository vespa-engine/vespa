// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributeresult.h"
#include <vespa/searchlib/attribute/singleenumattribute.h>

namespace search::expression {

class EnumAttributeResult : public AttributeResult
{
public:
    DECLARE_RESULTNODE(EnumAttributeResult);
    EnumAttributeResult(const attribute::IAttributeVector * attribute, DocId docId) :
        AttributeResult(attribute, docId),
        _enumAttr(dynamic_cast<const SingleValueEnumAttributeBase *>(attribute))
    {
    }
private:
    EnumAttributeResult() :
        AttributeResult(),
        _enumAttr(NULL)
    { }
    int64_t onGetEnum(size_t index) const override { (void) index; return (static_cast<int64_t>(_enumAttr->getE(getDocId()))); }
    const SingleValueEnumAttributeBase * _enumAttr;
};

}
