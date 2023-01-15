// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
#include <cstddef>
namespace vespalib {
/**
 * Compute Hamming distance between two binary blobs
 *
 * @param lhs a blob (to interpret as a bitvector with sz*8 bits)
 * @param rhs a blob (to interpret as a bitvector with sz*8 bits)
 * @param sz number of bytes in each blob
 * @return number of bits that differ when comparing the two blobs
 **/
size_t binary_hamming_distance(const void *lhs, const void *rhs, size_t sz);
}
