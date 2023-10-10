// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "generic_btree_bucket_database.h"

namespace storage::bucketdb {

using document::BucketId;

// TODO getMinDiffBits is hoisted from lockablemap.cpp, could probably be rewritten in terms of xor and MSB bit scan instr
/*
 *       63 -------- ... -> 0
 *     a: 1101111111 ... 0010
 *     b: 1101110010 ... 0011
 * a ^ b: 0000001101 ... 0001
 *              ^- diff bit = 57
 *
 * 63 - vespalib::Optimized::msbIdx(a ^ b) ==> 6
 *
 * what if a == b? special case? not a problem if we can prove this never happens.
 */

// TODO dedupe and unify common code
uint8_t
getMinDiffBits(uint16_t minBits, const BucketId& a, const BucketId& b) {
    for (uint32_t i = minBits; i <= std::min(a.getUsedBits(), b.getUsedBits()); i++) {
        BucketId a1(i, a.getRawId());
        BucketId b1(i, b.getRawId());
        if (b1.getId() != a1.getId()) {
            return i;
        }
    }
    return minBits;
}

uint8_t next_parent_bit_seek_level(uint8_t minBits, const BucketId& a, const BucketId& b) {
    const uint8_t min_used = std::min(a.getUsedBits(), b.getUsedBits());
    assert(min_used >= minBits); // Always monotonically descending towards leaves
    for (uint32_t i = minBits; i <= min_used; i++) {
        BucketId a1(i, a.getRawId());
        BucketId b1(i, b.getRawId());
        if (b1.getId() != a1.getId()) {
            return i;
        }
    }
    // The bit prefix is equal, which means that one node is a parent of the other. In this
    // case we have to force the seek to continue from the next level in the tree.
    return std::max(min_used, minBits) + 1;
}

}
