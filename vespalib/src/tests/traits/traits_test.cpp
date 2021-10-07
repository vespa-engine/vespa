// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/traits.h>
#include <vespa/vespalib/util/arrayqueue.hpp>

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

TEST("require that is_copyable works") {
    EXPECT_EQUAL(is_copyable<Simple>::value, true);
    EXPECT_EQUAL(is_copyable<Hard>::value, false);
    EXPECT_EQUAL(is_copyable<ArrayQueue<Simple> >::value, true);
    EXPECT_EQUAL(is_copyable<ArrayQueue<Hard> >::value, false);
    EXPECT_EQUAL(is_copyable<std::unique_ptr<Hard> >::value, false);
}

TEST("require that can_skip_destruction works") {
    EXPECT_EQUAL(can_skip_destruction<Simple>::value, true);
    EXPECT_EQUAL(can_skip_destruction<Hard>::value, false);
    EXPECT_EQUAL(can_skip_destruction<Child1>::value, false);
    EXPECT_EQUAL(can_skip_destruction<Child2>::value, true);
}

struct NoType {};
struct TypeType { using type = NoType; };
struct NoTypeType { static constexpr int type = 3; };

TEST("require that type type member can be detected") {
    EXPECT_FALSE(has_type_type_v<NoType>);
    EXPECT_TRUE(has_type_type_v<TypeType>);
    EXPECT_FALSE(has_type_type_v<NoTypeType>);
}

TEST_MAIN() { TEST_RUN_ALL(); }
