// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_cells_iterator.h"

namespace vespalib::tensor {

void
DenseTensorCellsIterator::next()
{
    ++_cellIdx;
    if (valid()) {
        for (int64_t i = (_address.size() - 1); i >= 0; --i) {
            _address[i]++;
            if (_address[i] != _type.dimensions()[i].size) {
                // Outer dimension labels can only be increased when this label wraps around.
                break;
            } else {
                _address[i] = 0;
            }
        }
    }
}

}
