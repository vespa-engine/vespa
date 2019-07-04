// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/tensor/dense/dense_dimension_combiner.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::tensor;

void verifyLeft(DenseDimensionCombiner &d, size_t last) {
    d.commonReset();
    d.leftReset();
    EXPECT_TRUE(d.leftInRange());
    EXPECT_EQUAL(d.leftIdx(), 0u);
    size_t expect = 0;
    while (d.leftInRange()) {
        d.stepLeft();
        EXPECT_GREATER(d.leftIdx(), expect);
        expect = d.leftIdx();
    }
    EXPECT_FALSE(d.leftInRange());
    EXPECT_EQUAL(expect, last);
    d.leftReset();
    EXPECT_TRUE(d.leftInRange());
    EXPECT_EQUAL(d.leftIdx(), 0u);
}

void verifyRight(DenseDimensionCombiner &d, size_t last) {
    d.commonReset();
    d.rightReset();
    EXPECT_TRUE(d.rightInRange());
    EXPECT_EQUAL(d.rightIdx(), 0u);
    size_t expect = 0;
    while (d.rightInRange()) {
        d.stepRight();
        EXPECT_GREATER(d.rightIdx(), expect);
        expect = d.rightIdx();
    }
    EXPECT_FALSE(d.rightInRange());
    EXPECT_EQUAL(expect, last);
    d.rightReset();
    EXPECT_TRUE(d.rightInRange());
    EXPECT_EQUAL(d.rightIdx(), 0u);
}


TEST("require that one left, one common, one right dimension works") {
    ValueType t12_lc = ValueType::tensor_type({{"d1_l", 3},{"d2_c", 4}});
    ValueType t23_cr = ValueType::tensor_type({{"d2_c", 4},{"d3_r", 5}});

    DenseDimensionCombiner d(t12_lc, t23_cr);

    EXPECT_TRUE(d.leftInRange());
    EXPECT_TRUE(d.rightInRange());
    EXPECT_TRUE(d.commonInRange());
    EXPECT_EQUAL(d.leftIdx(), 0u);
    EXPECT_EQUAL(d.rightIdx(), 0u);
    EXPECT_EQUAL(d.outputIdx(), 0u);

    d.stepCommon();
    EXPECT_TRUE(d.leftInRange());
    EXPECT_TRUE(d.rightInRange());
    EXPECT_TRUE(d.commonInRange());
    EXPECT_EQUAL(d.leftIdx(), 1u);
    EXPECT_EQUAL(d.rightIdx(), 5u);
    EXPECT_EQUAL(d.outputIdx(), 5u);

    d.stepRight();
    EXPECT_TRUE(d.leftInRange());
    EXPECT_TRUE(d.rightInRange());
    EXPECT_TRUE(d.commonInRange());
    EXPECT_EQUAL(d.leftIdx(), 1u);
    EXPECT_EQUAL(d.rightIdx(), 6u);
    EXPECT_EQUAL(d.outputIdx(), 6u);

    d.stepLeft();
    EXPECT_TRUE(d.leftInRange());
    EXPECT_TRUE(d.rightInRange());
    EXPECT_TRUE(d.commonInRange());
    EXPECT_EQUAL(d.leftIdx(), 5u);
    EXPECT_EQUAL(d.rightIdx(), 6u);
    EXPECT_EQUAL(d.outputIdx(), 26u);

    d.stepLeft();
    EXPECT_TRUE(d.leftInRange());
    EXPECT_TRUE(d.rightInRange());
    EXPECT_TRUE(d.commonInRange());
    EXPECT_EQUAL(d.leftIdx(), 9u);
    EXPECT_EQUAL(d.rightIdx(), 6u);
    EXPECT_EQUAL(d.outputIdx(), 46u);

    d.stepLeft();
    EXPECT_FALSE(d.leftInRange());
    EXPECT_TRUE(d.rightInRange());
    EXPECT_TRUE(d.commonInRange());
    EXPECT_EQUAL(d.leftIdx(), 13u);
    EXPECT_EQUAL(d.rightIdx(), 6u);
    EXPECT_EQUAL(d.outputIdx(), 6u);

    d.leftReset();
    EXPECT_TRUE(d.leftInRange());
    EXPECT_TRUE(d.rightInRange());
    EXPECT_TRUE(d.commonInRange());
    EXPECT_EQUAL(d.leftIdx(), 1u);
    EXPECT_EQUAL(d.rightIdx(), 6u);
    EXPECT_EQUAL(d.outputIdx(), 6u);

    d.stepCommon();
    EXPECT_TRUE(d.leftInRange());
    EXPECT_TRUE(d.rightInRange());
    EXPECT_TRUE(d.commonInRange());
    EXPECT_EQUAL(d.leftIdx(), 2u);
    EXPECT_EQUAL(d.rightIdx(), 11u);
    EXPECT_EQUAL(d.outputIdx(), 11u);

    d.stepRight();
    EXPECT_TRUE(d.leftInRange());
    EXPECT_TRUE(d.rightInRange());
    EXPECT_TRUE(d.commonInRange());
    EXPECT_EQUAL(d.leftIdx(), 2u);
    EXPECT_EQUAL(d.rightIdx(), 12u);
    EXPECT_EQUAL(d.outputIdx(), 12u);

    TEST_DO(verifyLeft(d, 12));
    TEST_DO(verifyRight(d, 20));
}

TEST("require that two left, no common, two right dimensions works") {
    ValueType t12_ll = ValueType::tensor_type({{"d1_l", 3},{"d2_l", 4}});
    ValueType t34_rr = ValueType::tensor_type({{"d3_r", 5},{"d4_r", 2}});

    DenseDimensionCombiner d(t12_ll, t34_rr);

    EXPECT_TRUE(d.leftInRange());
    EXPECT_TRUE(d.rightInRange());
    EXPECT_TRUE(d.commonInRange());
    EXPECT_EQUAL(d.leftIdx(), 0u);
    EXPECT_EQUAL(d.rightIdx(), 0u);
    EXPECT_EQUAL(d.outputIdx(), 0u);

    d.stepCommon();
    EXPECT_TRUE(d.leftInRange());
    EXPECT_TRUE(d.rightInRange());
    EXPECT_FALSE(d.commonInRange());
    EXPECT_EQUAL(d.leftIdx(), 0u);
    EXPECT_EQUAL(d.rightIdx(), 0u);
    EXPECT_EQUAL(d.outputIdx(), 120u);

    d.commonReset();
    d.stepRight();
    EXPECT_TRUE(d.leftInRange());
    EXPECT_TRUE(d.rightInRange());
    EXPECT_TRUE(d.commonInRange());
    EXPECT_EQUAL(d.leftIdx(), 0u);
    EXPECT_EQUAL(d.rightIdx(), 1u);
    EXPECT_EQUAL(d.outputIdx(), 1u);

    d.stepLeft();
    EXPECT_TRUE(d.leftInRange());
    EXPECT_TRUE(d.rightInRange());
    EXPECT_TRUE(d.commonInRange());
    EXPECT_EQUAL(d.leftIdx(), 1u);
    EXPECT_EQUAL(d.rightIdx(), 1u);
    EXPECT_EQUAL(d.outputIdx(), 11u);

    d.stepLeft();
    d.stepLeft();
    d.stepLeft();
    d.stepLeft();
    d.stepLeft();
    d.stepLeft();
    d.stepLeft();

    EXPECT_TRUE(d.leftInRange());
    EXPECT_TRUE(d.rightInRange());
    EXPECT_TRUE(d.commonInRange());
    EXPECT_EQUAL(d.leftIdx(), 8u);
    EXPECT_EQUAL(d.rightIdx(), 1u);
    EXPECT_EQUAL(d.outputIdx(), 81u);

    TEST_DO(verifyLeft(d, 12));
    TEST_DO(verifyRight(d, 10));
}

TEST_MAIN() { TEST_RUN_ALL(); }
