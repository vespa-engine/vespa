// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_address_ref.h"
#include <xxhash.h>

namespace vespalib::tensor {

uint32_t
SparseTensorAddressRef::calcHash() const {
    return XXH32(_start, _size, 0);
}

}

