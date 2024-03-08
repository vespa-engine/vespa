// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/optimized.h>

using namespace vespalib;

template <typename TestType>
class OptimizedTest : public ::testing::Test
{
protected:
    OptimizedTest();
    ~OptimizedTest() override;
};

template <typename TestType>
OptimizedTest<TestType>::OptimizedTest() = default;
template <typename TestType>
OptimizedTest<TestType>::~OptimizedTest() = default;

using OptimizedTestTypes = ::testing::Types<unsigned int, unsigned long, unsigned long long>;
TYPED_TEST_SUITE(OptimizedTest, OptimizedTestTypes);

TYPED_TEST(OptimizedTest, test_msb_idx)
{
    using T = TypeParam;
    EXPECT_EQ(Optimized::msbIdx(T(0)), 0);
    EXPECT_EQ(Optimized::msbIdx(T(1)), 0);
    EXPECT_EQ(Optimized::msbIdx(T(-1)), int(sizeof(T)*8 - 1));
    T v(static_cast<T>(-1));
    for (size_t i(0); i < sizeof(T); i++) {
        for (size_t j(0); j < 8; j++) {
            EXPECT_EQ(Optimized::msbIdx(v), int(sizeof(T)*8 - (i*8+j) - 1));
            v = v >> 1;
        }
    }
}

TYPED_TEST(OptimizedTest, test_lsb_idx)
{
    using T = TypeParam;
    EXPECT_EQ(Optimized::lsbIdx(T(0)), 0);
    EXPECT_EQ(Optimized::lsbIdx(T(1)), 0);
    EXPECT_EQ(Optimized::lsbIdx(T(T(1)<<(sizeof(T)*8 - 1))), int(sizeof(T)*8 - 1));
    EXPECT_EQ(Optimized::lsbIdx(T(-1)), 0);
    T v(static_cast<T>(-1));
    for (size_t i(0); i < sizeof(T); i++) {
        for (size_t j(0); j < 8; j++) {
            EXPECT_EQ(Optimized::lsbIdx(v), int(i*8+j));
            v = v << 1;
        }
    }
}

TYPED_TEST(OptimizedTest, test_pop_count)
{
    using T = TypeParam;
    EXPECT_EQ(0, Optimized::popCount(T(0)));
    EXPECT_EQ(1, Optimized::popCount(T(1)));
    EXPECT_EQ(int(8 * sizeof(T)), Optimized::popCount(T(-1)));
}

GTEST_MAIN_RUN_ALL_TESTS()

