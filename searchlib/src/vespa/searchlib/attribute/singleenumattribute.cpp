// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "singleenumattribute.h"
#include "singleenumattribute.hpp"
#include "stringbase.h"
#include "integerbase.h"
#include "floatbase.h"

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
    _enumIndices.push_back(IEnumStore::Index());
    return _enumIndices.size() - 1;
}


SingleValueEnumAttributeBase::EnumIndexCopyVector
SingleValueEnumAttributeBase::getIndicesCopy(uint32_t size) const
{
    assert(size <= _enumIndices.size());
    return EnumIndexCopyVector(&_enumIndices[0], &_enumIndices[0] + size);
}

void
SingleValueEnumAttributeBase::remap_enum_store_refs(const EnumIndexRemapper& remapper, AttributeVector& v)
{
    // update _enumIndices with new EnumIndex values after enum store has been compacted.
    v.logEnumStoreEvent("reenumerate", "reserved");
    auto new_indexes = std::make_unique<vespalib::Array<EnumIndex>>();
    new_indexes->reserve(_enumIndices.capacity());
    v.logEnumStoreEvent("reenumerate", "start");
    for (uint32_t i = 0; i < _enumIndices.size(); ++i) {
        EnumIndex old_index = _enumIndices[i];
        EnumIndex new_index = remapper.remap(old_index);
        new_indexes->push_back_fast(new_index);
    }
    v.logEnumStoreEvent("compactfixup", "drain");
    {
        AttributeVector::EnumModifier enum_guard(v.getEnumModifier());
        v.logEnumStoreEvent("compactfixup", "start");
        _enumIndices.replaceVector(std::move(new_indexes));
    }
    v.logEnumStoreEvent("compactfixup", "complete");
    v.logEnumStoreEvent("reenumerate", "complete");
}

template class SingleValueEnumAttribute<EnumAttribute<StringAttribute>>;
template class SingleValueEnumAttribute<EnumAttribute<IntegerAttributeTemplate<int8_t>>>;
template class SingleValueEnumAttribute<EnumAttribute<IntegerAttributeTemplate<int16_t>>>;
template class SingleValueEnumAttribute<EnumAttribute<IntegerAttributeTemplate<int32_t>>>;
template class SingleValueEnumAttribute<EnumAttribute<IntegerAttributeTemplate<int64_t>>>;
template class SingleValueEnumAttribute<EnumAttribute<FloatingPointAttributeTemplate<float>>>;
template class SingleValueEnumAttribute<EnumAttribute<FloatingPointAttributeTemplate<double>>>;

}
