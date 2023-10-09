// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/dense_hamming_distance.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("dense_hamming_distance_function_test");

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

struct FunInfo {
    using LookFor = DenseHammingDistance;
    void verify(const LookFor &fun) const {
        EXPECT_TRUE(fun.result_is_mutable());
    }
};

void assertOptimized(const vespalib::string &expr) {
    CellTypeSpace just_int8({CellType::INT8}, 2);
    EvalFixture::verify<FunInfo>(expr, {FunInfo{}}, just_int8);
    CellTypeSpace just_double({CellType::DOUBLE}, 2);
    EvalFixture::verify<FunInfo>(expr, {}, just_double);
}

void assertNotOptimized(const vespalib::string &expr) {
    CellTypeSpace just_int8({CellType::INT8}, 2);
    EvalFixture::verify<FunInfo>(expr, {}, just_int8);
}

TEST(DenseHammingDistanceOptimizer, hamming_distance_works_with_tensor_function) {
    assertOptimized("reduce(hamming(x5$1,x5$2),sum)");
    assertOptimized("reduce(hamming(x5$1,x5$2),sum,x)");
    assertOptimized("reduce(join(x5$1,x5$2,f(x,y)(hamming(x,y))),sum)");
    assertOptimized("reduce(join(x5$1,x5$2,f(x,y)(hamming(x,y))),sum,x)");
}

TEST(DenseHammingDistanceOptimizer, hamming_distance_with_compatible_dimensions_is_optimized) {
    // various vector sizes
    assertOptimized("reduce(hamming(x1$1,x1$2),sum)");
    assertOptimized("reduce(hamming(x3$1,x3$2),sum)");
    assertOptimized("reduce(hamming(x7$1,x7$2),sum)");
    assertOptimized("reduce(hamming(x8$1,x8$2),sum)");
    assertOptimized("reduce(hamming(x9$1,x9$2),sum)");
    assertOptimized("reduce(hamming(x17$1,x17$2),sum)");
    // multiple dimensions
    assertOptimized("reduce(hamming(x3y3$1,x3y3$2),sum)");
    assertOptimized("reduce(hamming(x3y4$1,x3y4$2),sum)");
    // with trivial dimensions
    assertOptimized("reduce(hamming(a1x3$1,x3$2),sum)");
    assertOptimized("reduce(hamming(x3$1z1,x3$2),sum)");
    assertOptimized("reduce(hamming(a1x3$1,b1x3$2z1),sum)");
}

TEST(DenseHammingDistanceOptimizer, hamming_distance_with_mapped_dimensions_is_NOT_optimized) {
    assertNotOptimized("reduce(hamming(x3_1$1,x3_1$2),sum)");
    assertNotOptimized("reduce(hamming(x3_1y2$1,x3_1y2$2),sum)");
}

TEST(DenseHammingDistanceOptimizer, hamming_distance_with_incompatible_dimensions_is_NOT_optimized) {
    assertNotOptimized("reduce(hamming(x3,y3),sum)");
    assertNotOptimized("reduce(hamming(y3,x3),sum)");
    assertNotOptimized("reduce(hamming(x3,x3y3),sum)");
    assertNotOptimized("reduce(hamming(x3y3,x3),sum)");
}

TEST(DenseHammingDistanceOptimizer, expressions_similar_to_hamming_distance_are_not_optimized) {
    assertNotOptimized("reduce(hamming(x3$1,x3$2),prod)");
}

TEST(DenseHammingDistanceOptimizer, result_must_be_double_to_trigger_optimization) {
    assertOptimized("reduce(hamming(x3y3$1,x3y3$2),sum,x,y)");
    assertNotOptimized("reduce(hamming(x3y3$1,x3y3$2),sum,x)");
    assertNotOptimized("reduce(hamming(x3y3$1,x3y3$2),sum,y)");
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()

