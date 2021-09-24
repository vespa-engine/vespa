// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

TensorSpec makeTensor(size_t numCells, double cellBias) {
    return GenSpec(cellBias).idx("x", numCells).cells(CellType::INT8);
}

const double leftBias = 3.0;
const double rightBias = 5.0;

double calcHammingDistance(size_t numCells) {
    double result = 0;
    for (size_t i = 0; i < numCells; ++i) {
        uint8_t a = (i + leftBias);
        uint8_t b = (i + rightBias);
        uint8_t bits = a ^ b;
        for (int j = 0; j < 8; ++j) {
            if (bits & 1) {
                result += 1;
            }
            bits >>= 1;
        }
    }
    return result;
}

void check_gen_with_result(size_t sz, double wanted) {
    EvalFixture::ParamRepo param_repo;
    param_repo.add("a", makeTensor(sz, leftBias));
    param_repo.add("b", makeTensor(sz, rightBias));
    vespalib::string expr = "reduce(hamming(a,b),sum,x)";
    EvalFixture evaluator(prod_factory, expr, param_repo, true);
    EXPECT_EQ(GenSpec(wanted).gen(), evaluator.result());
    EXPECT_EQ(evaluator.result(), EvalFixture::ref(expr, param_repo));
    auto info = evaluator.find_all<DenseHammingDistance>();
    EXPECT_EQ(info.size(), 1u);
};

TEST(DenseHammingDistanceTest, basic_hamming_distance_computation) {
    check_gen_with_result(1, 2); // 3 ^ 5 = 6
    check_gen_with_result(2, 3); // 4 ^ 6 = 2
    check_gen_with_result(3, 4); // 5 ^ 7 = 2
    check_gen_with_result(4, 7); // 6 ^ 8 = 14
    check_gen_with_result(5, 10); // 7 ^ 9 = 14
}

void assertHammingDistance(size_t numCells) {
    check_gen_with_result(numCells, calcHammingDistance(numCells));
}

TEST(DenseHammingDistanceTest, compare_hamming_distance_results) {
    assertHammingDistance(8);
    assertHammingDistance(16);
    assertHammingDistance(32);
    assertHammingDistance(64);
    assertHammingDistance(128);
    assertHammingDistance(256);
    assertHammingDistance(512);
    assertHammingDistance(1024);
    assertHammingDistance(8 + 3);
    assertHammingDistance(16 + 3);
    assertHammingDistance(32 + 3);
    assertHammingDistance(64 + 3);
    assertHammingDistance(128 + 3);
    assertHammingDistance(256 + 3);
    assertHammingDistance(512 + 3);
    assertHammingDistance(1024 + 3);
}

struct FunInfo {
    using LookFor = DenseHammingDistance;
    void verify(const LookFor &fun) const {
        EXPECT_FALSE(fun.result_is_mutable());
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
    assertOptimized("reduce(hamming(x1$1,x1$2),sum)");
    assertOptimized("reduce(hamming(x3$1,x3$2),sum)");
    assertOptimized("reduce(hamming(x17$1,x17$2),sum)");
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

TEST(DenseHammingDistanceOptimizer, multi_dimensional_hamming_distance_can_be_optimized) {
    assertOptimized("reduce(hamming(x3y3$1,x3y3$2),sum)");
}

TEST(DenseHammingDistanceOptimizer, result_must_be_double_to_trigger_optimization) {
    assertOptimized("reduce(hamming(x3y3$1,x3y3$2),sum,x,y)");
    assertNotOptimized("reduce(hamming(x3y3$1,x3y3$2),sum,x)");
    assertNotOptimized("reduce(hamming(x3y3$1,x3y3$2),sum,y)");
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()

