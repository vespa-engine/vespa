// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "singleenumattribute.h"
#include "singleenumattribute.hpp"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.single_enum_attribute");

namespace search {

using attribute::Config;

SingleValueEnumAttributeBase::
SingleValueEnumAttributeBase(const Config & c, GenerationHolder &genHolder)
    : _enumIndices(c.getGrowStrategy().getDocsInitialCapacity(),
                   c.getGrowStrategy().getDocsGrowPercent(),
                   c.getGrowStrategy().getDocsGrowDelta(),
                   genHolder)
{
}


SingleValueEnumAttributeBase::~SingleValueEnumAttributeBase()
{
}


AttributeVector::DocId
SingleValueEnumAttributeBase::addDoc(bool &incGeneration)
{
    incGeneration = _enumIndices.isFull();
    _enumIndices.push_back(EnumStoreBase::Index());
    return _enumIndices.size() - 1;
}


SingleValueEnumAttributeBase::EnumIndexCopyVector
SingleValueEnumAttributeBase::getIndicesCopy(uint32_t size) const
{
    assert(size <= _enumIndices.size());
    return EnumIndexCopyVector(&_enumIndices[0], &_enumIndices[0] + size);
}

}
