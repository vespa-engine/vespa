// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "serialized_fast_value_attribute.h"
#include "serialized_tensor_ref.h"
#include <vespa/eval/eval/value.h>
#include <vespa/searchcommon/attribute/config.h>

#include <vespa/log/log.h>

LOG_SETUP(".searchlib.tensor.serialized_fast_value_attribute");

using namespace vespalib;
using namespace vespalib::eval;

namespace search::tensor {

SerializedFastValueAttribute::SerializedFastValueAttribute(stringref name, const Config &cfg, const NearestNeighborIndexFactory& index_factory)
    : TensorAttribute(name, cfg, _tensorBufferStore, index_factory),
      _tensorBufferStore(cfg.tensorType(), get_memory_allocator(), 400u)
{
}


SerializedFastValueAttribute::~SerializedFastValueAttribute()
{
    getGenerationHolder().reclaim_all();
    _tensorStore.reclaim_all_memory();
}

SerializedTensorRef
SerializedFastValueAttribute::get_serialized_tensor_ref(uint32_t docid) const
{
    EntryRef ref = acquire_entry_ref(docid);
    return _tensorBufferStore.get_serialized_tensor_ref(ref);
}

bool
SerializedFastValueAttribute::supports_get_serialized_tensor_ref() const
{
    return true;
}

vespalib::eval::TypedCells
SerializedFastValueAttribute::get_vector(uint32_t docid, uint32_t subspace) const
{
    EntryRef ref = acquire_entry_ref(docid);
    auto vectors = _tensorBufferStore.get_vectors(ref);
    return (subspace < vectors.subspaces()) ? vectors.cells(subspace) : _tensorBufferStore.get_empty_subspace();
}

VectorBundle
SerializedFastValueAttribute::get_vectors(uint32_t docid) const
{
    EntryRef ref = acquire_entry_ref(docid);
    return _tensorBufferStore.get_vectors(ref);
}

}
