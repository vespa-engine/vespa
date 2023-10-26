// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/common/condensedbitvectors.h>
#include <vespa/log/log.h>

LOG_SETUP("condensedbitvector_test");

using search::CondensedBitVector;
using vespalib::GenerationHolder;

TEST("Verify state after init")
{
    GenerationHolder genHolder;
    CondensedBitVector::UP cbv(CondensedBitVector::create(8, genHolder));
    EXPECT_EQUAL(32u, cbv->getKeyCapacity());
    EXPECT_EQUAL(8u, cbv->getCapacity());
    EXPECT_EQUAL(8u, cbv->getSize());
}


TEST("Verify set/get")
{
    GenerationHolder genHolder;
    CondensedBitVector::UP cbv(CondensedBitVector::create(8, genHolder));
    for (size_t i(0); i < 32; i++) {
        for (size_t j(0); j < 8; j++) {
            EXPECT_FALSE(cbv->get(i,j));
        }
    }
    cbv->set(23,5, false);
    EXPECT_FALSE(cbv->get(23, 5));
    for (size_t i(0); i < 32; i++) {
        for (size_t j(0); j < 8; j++) {
            EXPECT_FALSE(cbv->get(i,j));
        }
    }
    cbv->set(23,5, true);
    EXPECT_TRUE(cbv->get(23, 5));
    size_t sum(0);
    for (size_t i(0); i < 32; i++) {
        for (size_t j(0); j < 8; j++) {
            sum += cbv->get(i,j) ? 1 : 0;
        }
    }
    EXPECT_EQUAL(1u, sum);
}

TEST_MAIN() { TEST_RUN_ALL(); }
