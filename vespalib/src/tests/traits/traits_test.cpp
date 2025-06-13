// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/traits.h>
#include <vespa/vespalib/util/arrayqueue.hpp>
#include <concepts>

using namespace vespalib;

struct Simple {
    int value;
    int moved;
    explicit Simple(int v) : value(v), moved(0) {}
    Simple(const Simple &rhs) : value(rhs.value), moved(rhs.moved) {}
    Simple(Simple &&rhs) : value(rhs.value), moved(rhs.moved + 1) {}
};
using Hard = std::unique_ptr<Simple>;

struct Base {
    virtual void foo() = 0;
    virtual ~Base() = default;
};
struct Child1 : Base {
    void foo() override {}
};
struct Child2 : Base {
    void foo() override {}
};

VESPA_CAN_SKIP_DESTRUCTION(Child2);

TEST(TraitsTest, require_that_copy_ctor_detection_works) {
    EXPECT_EQ(std::copy_constructible<Simple>, true);
    EXPECT_EQ(std::copy_constructible<Hard>, false);
    EXPECT_EQ(std::copy_constructible<ArrayQueue<Simple> >, true);
    EXPECT_EQ(std::copy_constructible<ArrayQueue<Hard> >, false);
    EXPECT_EQ(std::copy_constructible<std::unique_ptr<Hard> >, false);
}

TEST(TraitsTest, require_that_can_skip_destruction_works) {
    EXPECT_EQ(can_skip_destruction<Simple>, true);
    EXPECT_EQ(can_skip_destruction<Hard>, false);
    EXPECT_EQ(can_skip_destruction<Child1>, false);
    EXPECT_EQ(can_skip_destruction<Child2>, true);
}

struct NoType {};
struct TypeType { using type = NoType; };
struct NoTypeType { static constexpr int type = 3; };

TEST(TraitsTest, require_that_type_type_member_can_be_detected) {
    EXPECT_FALSE(has_type_type<NoType>);
    EXPECT_TRUE(has_type_type<TypeType>);
    EXPECT_FALSE(has_type_type<NoTypeType>);
}

GTEST_MAIN_RUN_ALL_TESTS()
