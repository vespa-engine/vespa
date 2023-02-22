// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "vector_bundle.h"
#include <vespa/vespalib/util/string_id.h>

namespace search::tensor {

/*
 * This class contains a reference to a tensor stored in a TensorBufferStore.
 */
class SerializedTensorRef
{
    VectorBundle                                 _vectors;
    uint32_t                                     _num_mapped_dimensions;
    vespalib::ConstArrayRef<vespalib::string_id> _labels; // all subspaces
public:
    SerializedTensorRef();
    SerializedTensorRef(VectorBundle vectors, uint32_t num_mapped_dimensions, vespalib::ConstArrayRef<vespalib::string_id> labels);
    ~SerializedTensorRef();
    const VectorBundle& get_vectors() const noexcept { return _vectors; }
    vespalib::ConstArrayRef<vespalib::string_id> get_labels(uint32_t subspace) const;
};

}
