// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for resultset.

#include <vespa/log/log.h>
LOG_SETUP("resultset_test");

#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/common/resultset.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/arraysize.h>

using namespace search;
using vespalib::arraysize;

namespace {

void concatenate(const ResultSet *input_array[], size_t array_size,
                 ResultSet &output)
{
    size_t hit_count = 0;
    for (size_t i = 0; i < array_size; ++i) {
        hit_count += input_array[i]->getArrayUsed();
    }
    output.allocArray(hit_count);
    RankedHit *p = output.getArray();
    for (size_t i = 0; i < array_size; ++i) {
        const ResultSet &set = *input_array[i];
        memcpy(p, set.getArray(), set.getArrayUsed() * sizeof(RankedHit));
        p += set.getArrayUsed();
        if (set.getBitOverflow()) {
            if (output.getBitOverflow()) {
                output.getBitOverflow()->orWith(*set.getBitOverflow());
            } else {
                output.setBitOverflow(BitVector::create(*set.getBitOverflow()));
            }
        }
    }
    output.setArrayUsed(hit_count);
}


void addHit(ResultSet &set, unsigned int doc_id, double rank) {
    if (set.getArrayAllocated() == 0) {
        set.allocArray(10);
    }
    ASSERT_LESS(set.getArrayUsed(), set.getArrayAllocated());
    RankedHit *hit_array = set.getArray();
    hit_array[set.getArrayUsed()]._docId = doc_id;
    hit_array[set.getArrayUsed()]._rankValue = rank;
    set.setArrayUsed(set.getArrayUsed() + 1);
}

TEST("require that mergeWithOverflow works") {
    ResultSet set1;
    addHit(set1, 2, 4.2);
    addHit(set1, 4, 3.2);
    BitVector::UP bit_vector = BitVector::create(20);
    bit_vector->setBit(2);
    bit_vector->setBit(4);
    bit_vector->setBit(7);
    bit_vector->invalidateCachedCount();
    set1.setBitOverflow(std::move(bit_vector));
    EXPECT_EQUAL(3u, set1.getNumHits());
    set1.mergeWithBitOverflow();
    EXPECT_EQUAL(3u, set1.getNumHits());
}

TEST("require that resultsets can be concatenated") {
    ResultSet set1;
    addHit(set1, 2, 4.2);
    addHit(set1, 4, 3.2);
    BitVector::UP bit_vector = BitVector::create(20);
    bit_vector->setBit(7);
    set1.setBitOverflow(std::move(bit_vector));

    ResultSet set2;
    addHit(set2, 12, 4.2);
    addHit(set2, 14, 3.2);
    bit_vector = BitVector::create(20);
    bit_vector->setBit(17);
    set2.setBitOverflow(std::move(bit_vector));

    const ResultSet *sets[] = { &set1, &set2 };
    ResultSet target;
    concatenate(sets, arraysize(sets), target);

    EXPECT_EQUAL(4u, target.getArrayAllocated());
    ASSERT_EQUAL(4u, target.getArrayUsed());
    EXPECT_EQUAL(2u, target.getArray()[0]._docId);
    EXPECT_EQUAL(4.2, target.getArray()[0]._rankValue);
    EXPECT_EQUAL(4u, target.getArray()[1]._docId);
    EXPECT_EQUAL(3.2, target.getArray()[1]._rankValue);
    EXPECT_EQUAL(12u, target.getArray()[2]._docId);
    EXPECT_EQUAL(4.2, target.getArray()[2]._rankValue);
    EXPECT_EQUAL(14u, target.getArray()[3]._docId);
    EXPECT_EQUAL(3.2, target.getArray()[3]._rankValue);

    BitVector * bv = target.getBitOverflow();
    ASSERT_TRUE(bv);
    EXPECT_EQUAL(20u, bv->size());
    EXPECT_EQUAL(7u, bv->getNextTrueBit(0));
    EXPECT_EQUAL(17u, bv->getNextTrueBit(8));
    EXPECT_EQUAL(20u, bv->getNextTrueBit(18));
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
