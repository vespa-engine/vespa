// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "serialized_tensor_ref.h"

namespace search::tensor {

SerializedTensorRef::SerializedTensorRef()
    : _vectors(),
      _num_mapped_dimensions(0),
      _labels()
{
}

SerializedTensorRef::SerializedTensorRef(VectorBundle vectors, uint32_t num_mapped_dimensions, vespalib::ConstArrayRef<vespalib::string_id> labels)
    : _vectors(vectors),
      _num_mapped_dimensions(num_mapped_dimensions),
      _labels(labels)
{
}

SerializedTensorRef::~SerializedTensorRef() = default;

vespalib::ConstArrayRef<vespalib::string_id>
SerializedTensorRef::get_labels(uint32_t subspace) const
{
    assert(subspace < _vectors.subspaces());
    return {_labels.data() + subspace * _num_mapped_dimensions, _num_mapped_dimensions};
}

}
