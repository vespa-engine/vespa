// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/gtest/gtest.h>

using E = vespalib::RequireFailedException;

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

TEST(RequireTest, require_eq_implicit_approx_for_double) {
    double foo = 1.0;
    double bar = 1.0 + 1e-9;
    REQUIRE(foo != bar);
    REQUIRE_EQ(foo, bar);
}

//-----------------------------------------------------------------------------

struct MyA {
    int a;
    int b;
    template <typename T>
    bool operator==(const T &rhs) const {
        return (a == rhs.a) && (b == rhs.b);
    }
};
std::ostream &operator<<(std::ostream &out, const MyA &a) {
    out << "MyA { a: " << a.a << ", b: " << a.b << " }";
    return out;
}

struct MyB {
    int a;
    int b;
};

struct MyC {
    char a;
    ssize_t b;
};

TEST(RequireTest, explicit_compare_and_print) {
    MyA x{5, 7};
    MyA y{5, 6};
    REQUIRE_EQ(x, x);
    EXPECT_THROW(REQUIRE_EQ(x, y), E);
}

TEST(RequireTest, implicit_compare_and_print) {
    MyB x{5, 7};
    MyB y{5, 6};
    REQUIRE_EQ(x, x);
    EXPECT_THROW(REQUIRE_EQ(x, y), E);
}

TEST(RequireTest, comparable_but_unprintable) {
    MyA x{5, 7};
    MyC y{5, 6};
    REQUIRE_EQ(x, x);
    EXPECT_THROW(REQUIRE_EQ(x, y), E);
}

// manual test for uncompilable code (uncomparable values)
TEST(RequireTest, uncomment_to_manually_check_uncompilable_code) {
    MyA a{5, 7};
    MyB b{5, 7};
    MyC c{5, 7};
    (void) a;
    (void) b;
    (void) c;
    // REQUIRE_EQ(b, a);
    // REQUIRE_EQ(c, c);
}

//-----------------------------------------------------------------------------

TEST(RequireTest, explicit_require_failure) {
    EXPECT_THROW(
        {
            try { REQUIRE_FAILED("this is my message"); }
            catch(const E &e) {
                fprintf(stderr, "e.getMessage() is >>>%s<<<\n", e.getMessage().c_str());
                fprintf(stderr, "e.getLocation() is >>>%s<<<\n", e.getLocation().c_str());
                fprintf(stderr, "e.what() is >>>%s<<<\n", e.what());
                throw;
            }
        }, E);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
