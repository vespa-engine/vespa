// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
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
typedef std::unique_ptr<Simple> Hard;

struct Base {
    virtual void foo() = 0;
    virtual ~Base() {}
};
struct Child1 : Base {
    void foo() override {}
};
struct Child2 : Base {
    void foo() override {}
};

VESPA_CAN_SKIP_DESTRUCTION(Child2);

TEST("require that copy ctor detection works") {
    EXPECT_EQUAL(std::copy_constructible<Simple>, true);
    EXPECT_EQUAL(std::copy_constructible<Hard>, false);
    EXPECT_EQUAL(std::copy_constructible<ArrayQueue<Simple> >, true);
    EXPECT_EQUAL(std::copy_constructible<ArrayQueue<Hard> >, false);
    EXPECT_EQUAL(std::copy_constructible<std::unique_ptr<Hard> >, false);
}

TEST("require that can_skip_destruction works") {
    EXPECT_EQUAL(can_skip_destruction<Simple>, true);
    EXPECT_EQUAL(can_skip_destruction<Hard>, false);
    EXPECT_EQUAL(can_skip_destruction<Child1>, false);
    EXPECT_EQUAL(can_skip_destruction<Child2>, true);
}

struct NoType {};
struct TypeType { using type = NoType; };
struct NoTypeType { static constexpr int type = 3; };

TEST("require that type type member can be detected") {
    EXPECT_FALSE(has_type_type<NoType>);
    EXPECT_TRUE(has_type_type<TypeType>);
    EXPECT_FALSE(has_type_type<NoTypeType>);
}

TEST_MAIN() { TEST_RUN_ALL(); }
