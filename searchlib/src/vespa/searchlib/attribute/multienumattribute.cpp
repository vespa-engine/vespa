// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multienumattribute.h"
#include "multienumattribute.hpp"
#include <stdexcept>

namespace search {

uint32_t
IWeightedIndexVector::getEnumHandles(uint32_t, const WeightedIndex * &) const {
    throw std::runtime_error("IWeightedIndexVector::getEnumHandles() not implmented");
}

}

namespace search::multienumattribute {

using EnumIndex = IEnumStore::Index;
using EnumIndexRemapper = IEnumStore::EnumIndexRemapper;
using Value = multivalue::Value<EnumIndex>;
using WeightedValue = multivalue::WeightedValue<EnumIndex>;

template <typename WeightedIndex>
void
remap_enum_store_refs(const EnumIndexRemapper& remapper, AttributeVector& v, attribute::MultiValueMapping<WeightedIndex>& multi_value_mapping)
{
    using WeightedIndexVector = std::vector<WeightedIndex>;

    // update multi_value_mapping with new EnumIndex values after enum store has been compacted.
    v.logEnumStoreEvent("compactfixup", "drain");
    {
        AttributeVector::EnumModifier enum_guard(v.getEnumModifier());
        auto& filter = remapper.get_entry_ref_filter();
        v.logEnumStoreEvent("compactfixup", "start");
        for (uint32_t doc = 0; doc < v.getNumDocs(); ++doc) {
            vespalib::ConstArrayRef<WeightedIndex> indicesRef(multi_value_mapping.get(doc));
            WeightedIndexVector indices(indicesRef.cbegin(), indicesRef.cend());
            for (uint32_t i = 0; i < indices.size(); ++i) {
                EnumIndex ref = indices[i].value();
                if (ref.valid() && filter.has(ref)) {
                    ref = remapper.remap(ref);
                }
                indices[i] = WeightedIndex(ref, indices[i].weight());
            }
            std::atomic_thread_fence(std::memory_order_release);
            multi_value_mapping.replace(doc, indices);
        }
    }
    v.logEnumStoreEvent("compactfixup", "complete");
}

template void remap_enum_store_refs(const EnumIndexRemapper&, AttributeVector&, attribute::MultiValueMapping<Value> &);
template void remap_enum_store_refs(const EnumIndexRemapper&, AttributeVector&, attribute::MultiValueMapping<WeightedValue> &);

}
