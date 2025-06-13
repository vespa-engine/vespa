// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <functional>

TEST(ExploreModernCppTest, verify_how_std_function_copies_lambda_closures) {
    size_t count = 0;
    size_t value = 0;
    auto closure = [count,&value]() mutable noexcept { ++count; value += count; };
    closure();
    EXPECT_EQ(0u, count);
    EXPECT_EQ(1u, value); // +1
    closure();
    EXPECT_EQ(3u, value); // +2
    std::function<void()> fun = closure;
    fun();
    EXPECT_EQ(6u, value); // +3
    closure();
    EXPECT_EQ(9u, value); // +3 (fun had a copy of count)
    auto &closure_ref = closure;
    std::function<void()> fun2 = closure_ref;   
    fun2();
    EXPECT_EQ(13u, value); // +4
    closure();
    EXPECT_EQ(17u, value); // +4 (fun2 had a copy of count)
    std::function<void()> fun3 = std::ref(closure);
    fun3();
    EXPECT_EQ(22u, value); // +5
    closure();
    EXPECT_EQ(28u, value); // +6 (fun only had a copy of the wrapper)
}

GTEST_MAIN_RUN_ALL_TESTS()
