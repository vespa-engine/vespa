// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributeresult.h"

namespace search::expression {

class EnumAttributeResult final : public AttributeResult
{
public:
    using EnumRefs = attribute::IAttributeVector::EnumRefs;
    DECLARE_RESULTNODE(EnumAttributeResult);
    EnumAttributeResult(EnumRefs enumRefs, const attribute::IAttributeVector * attribute, DocId docId) :
        AttributeResult(attribute, docId),
        _enumRefs(enumRefs)
    {
    }
private:
    EnumAttributeResult()
        : AttributeResult(),
          _enumRefs()
    { }
    int64_t onGetEnum(size_t index) const override { (void) index; return (static_cast<int64_t>(_enumRefs[getDocId()].load_relaxed().ref())); }
    EnumRefs _enumRefs;
};

}
