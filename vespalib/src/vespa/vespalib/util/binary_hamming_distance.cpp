// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "binary_hamming_distance.h"
#include <vespa/vespalib/hwaccelerated/private_helpers.hpp>

namespace vespalib {

size_t
binary_hamming_distance(const void *lhs, const void *rhs, size_t sz) noexcept {
    return hwaccelerated::helper::autovec_binary_hamming_distance(lhs, rhs, sz);
}

}
