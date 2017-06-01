// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_cells_iterator.h"

namespace vespalib {
namespace tensor {

void
DenseTensorCellsIterator::next()
{
    ++_cellIdx;
    if (valid()) {
        for (int64_t i = (_address.size() - 1); i >= 0; --i) {
            _address[i] = (_address[i] + 1) % _type.dimensions()[i].size;
            if (_address[i] != 0) {
                // Outer dimension labels can only be increased when this label wraps around.
                break;
            }
        }
    }
}

} // namespace vespalib::tensor
} // namespace vespalib
