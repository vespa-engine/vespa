// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("bitvector_benchmark");
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/common/bitvector.h>

using namespace search;

namespace {

size_t scan(BitVector & bv) __attribute__((noinline));

size_t scan(BitVector & bv)
{
    size_t count(0);
    for (BitVector::Index i(bv.getFirstTrueBit()), m(bv.size()); i < m; i = bv.getNextTrueBit(i+1)) {
        count++;
    }
    return count;
}

}

// This test is 10% faster with table lookup than with runtime shifting.
TEST(BitvectorBenchmarkTest, speed_of_getNextTrueBit)
{
    BitVector::UP bv(BitVector::create(100000000));
    bv->setInterval(0, bv->size());

    for (size_t i(0); i < 10; i++) {
        EXPECT_EQ(bv->size(), scan(*bv));
    }
    EXPECT_EQ(bv->size(), bv->countTrueBits());
}

GTEST_MAIN_RUN_ALL_TESTS()
