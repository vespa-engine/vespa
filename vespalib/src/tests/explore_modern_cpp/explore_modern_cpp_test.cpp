// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <functional>

TEST("verify how std::function copies lambda closures") {
    size_t count = 0;
    size_t value = 0;
    auto closure = [count,&value]() mutable noexcept { ++count; value += count; };
    closure();
    EXPECT_EQUAL(0u, count);
    EXPECT_EQUAL(1u, value); // +1
    closure();
    EXPECT_EQUAL(3u, value); // +2
    std::function<void()> fun = closure;
    fun();
    EXPECT_EQUAL(6u, value); // +3
    closure();
    EXPECT_EQUAL(9u, value); // +3 (fun had a copy of count)
    auto &closure_ref = closure;
    std::function<void()> fun2 = closure_ref;   
    fun2();
    EXPECT_EQUAL(13u, value); // +4
    closure();
    EXPECT_EQUAL(17u, value); // +4 (fun2 had a copy of count)
    std::function<void()> fun3 = std::ref(closure);
    fun3();
    EXPECT_EQUAL(22u, value); // +5
    closure();
    EXPECT_EQUAL(28u, value); // +6 (fun only had a copy of the wrapper)
}

TEST_MAIN() { TEST_RUN_ALL(); }
