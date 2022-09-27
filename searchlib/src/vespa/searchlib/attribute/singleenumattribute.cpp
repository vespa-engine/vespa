// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "singleenumattribute.hpp"
#include "stringbase.h"
#include "integerbase.h"
#include "floatbase.h"
#include "enumattribute.h"
#include "enummodifier.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.single_enum_attribute");

namespace search {

using attribute::Config;

SingleValueEnumAttributeBase::
SingleValueEnumAttributeBase(const Config & c, GenerationHolder &genHolder, const vespalib::alloc::Alloc& initial_alloc)
    : _enumIndices(c.getGrowStrategy(), genHolder, initial_alloc)
{
}

SingleValueEnumAttributeBase::~SingleValueEnumAttributeBase() = default;

AttributeVector::DocId
SingleValueEnumAttributeBase::addDoc(bool &incGeneration)
{
    incGeneration = _enumIndices.isFull();
    _enumIndices.push_back(AtomicEntryRef());
    return _enumIndices.size() - 1;
}

SingleValueEnumAttributeBase::EnumIndexCopyVector
SingleValueEnumAttributeBase::getIndicesCopy(uint32_t size) const
{
    assert(size <= _enumIndices.get_size());            // Called from writer only
    auto* enum_indices = &_enumIndices.get_elem_ref(0); // Called from writer only
    EnumIndexCopyVector result;
    result.reserve(size);
    for (uint32_t lid = 0; lid < size; ++lid) {
        result.push_back(enum_indices[lid].load_relaxed());
    }
    return result;
}

void
SingleValueEnumAttributeBase::remap_enum_store_refs(const EnumIndexRemapper& remapper, AttributeVector& v)
{
    // update _enumIndices with new EnumIndex values after enum store has been compacted.
    v.logEnumStoreEvent("reenumerate", "reserved");
    auto new_indexes = _enumIndices.create_replacement_vector();
    new_indexes.reserve(_enumIndices.capacity());
    v.logEnumStoreEvent("reenumerate", "start");
    auto& filter = remapper.get_entry_ref_filter();
    for (uint32_t i = 0; i < _enumIndices.size(); ++i) {
        EnumIndex ref = _enumIndices[i].load_relaxed();
        if (ref.valid() && filter.has(ref)) {
            ref = remapper.remap(ref);
        }
        new_indexes.push_back_fast(AtomicEntryRef(ref));
    }
    v.logEnumStoreEvent("compactfixup", "drain");
    {
        attribute::EnumModifier enum_guard(v.getEnumModifier());
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
