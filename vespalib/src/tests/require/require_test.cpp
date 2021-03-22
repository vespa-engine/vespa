// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/gtest/gtest.h>

//-----------------------------------------------------------------------------

void pass_require() {
    bool this_is_true = true;
    REQUIRE(this_is_true);
}

void pass_require_eq() {
    int a = 3;
    int b = 3;
    REQUIRE_EQ(a, b);
}

TEST(RequireTest, require_can_pass) {
    EXPECT_NO_THROW(pass_require());
}

TEST(RequireTest, require_eq_can_pass) {
    EXPECT_NO_THROW(pass_require_eq());
}

//-----------------------------------------------------------------------------

void fail_require() {
    bool this_is_false = false;
    REQUIRE(this_is_false);
}

void fail_require_eq() {
    int a = 3;
    int b = 5;
    REQUIRE_EQ(a, b);
}

TEST(RequireTest, require_can_fail) {
    using E = vespalib::RequireFailedException;
    EXPECT_THROW(
        {
            try { fail_require(); }
            catch(const E &e) {
                fprintf(stderr, "e.getMessage() is >>>%s<<<\n", e.getMessage().c_str());
                fprintf(stderr, "e.getLocation() is >>>%s<<<\n", e.getLocation().c_str());
                fprintf(stderr, "e.what() is >>>%s<<<\n", e.what());
                throw;
            }
        }, E);
}

TEST(RequireTest, require_eq_can_fail) {
    using E = vespalib::RequireFailedException;
    EXPECT_THROW(
        {
            try { fail_require_eq(); }
            catch(const E &e) {
                fprintf(stderr, "e.getMessage() is >>>%s<<<\n", e.getMessage().c_str());
                fprintf(stderr, "e.getLocation() is >>>%s<<<\n", e.getLocation().c_str());
                fprintf(stderr, "e.what() is >>>%s<<<\n", e.what());
                throw;
            }
        }, E);
}

//-----------------------------------------------------------------------------

constexpr bool foo(bool flag) {
    REQUIRE(flag);
    return flag;
}

constexpr int foo(int a, int b) {
    REQUIRE_EQ(a, b);
    return (a + b);
}

TEST(RequireTest, require_can_be_constexpr) {
    constexpr bool flag = foo(true);
    EXPECT_TRUE(flag);
}

TEST(RequireTest, require_eq_can_be_constexpr) {
    constexpr int value = foo(2, 2);
    EXPECT_EQ(value, 4);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
