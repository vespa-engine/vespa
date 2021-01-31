// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/latch.h>

using namespace vespalib;

TEST("require that write then read works") {
    Latch<int> latch;
    EXPECT_TRUE(!latch.has_value());
    latch.write(42);
    EXPECT_TRUE(latch.has_value());
    EXPECT_EQUAL(latch.read(), 42);
    EXPECT_TRUE(!latch.has_value());
}

TEST_MT_FFF("require that read waits for write", 2, Latch<int>(), Gate(), TimeBomb(60)) {
    if (thread_id == 0) {
        EXPECT_TRUE(!f2.await(10ms));
        f1.write(123);
        EXPECT_TRUE(f2.await(60s));
    } else {
        EXPECT_EQUAL(f1.read(), 123);
        f2.countDown();
    }
}

TEST_MT_FFF("require that write waits for read", 2, Latch<int>(), Gate(), TimeBomb(60)) {
    if (thread_id == 0) {
        f1.write(123);
        f1.write(456);
        f2.countDown();
    } else {
        EXPECT_TRUE(!f2.await(10ms));
        EXPECT_EQUAL(f1.read(), 123);
        EXPECT_TRUE(f2.await(60s));
        EXPECT_EQUAL(f1.read(), 456);
    }
}

struct MyInt {
    int value;
    MyInt(int value_in) : value(value_in) {}
    MyInt(MyInt &&rhs) = default;
    MyInt(const MyInt &rhs) = delete;
    MyInt &operator=(const MyInt &rhs) = delete;
    MyInt &operator=(MyInt &&rhs) = delete;
};

TEST("require that un-assignable non-default-constructable move-only objects can be used") {
    Latch<MyInt> latch;
    latch.write(MyInt(1337));
    EXPECT_EQUAL(latch.read().value, 1337);
}

struct MyObj {
    static int total;
    int *with_state;
    MyObj(int &with_state_in) : with_state(&with_state_in) {}
    MyObj(MyObj &&rhs) {
        with_state = rhs.with_state;
        rhs.with_state = nullptr;
    }
    void detach() { with_state = nullptr; }
    ~MyObj() {
        ++total;
        if (with_state) {
            ++(*with_state);
        }
    }
    MyObj(const MyObj &rhs) = delete;
    MyObj &operator=(const MyObj &rhs) = delete;
    MyObj &operator=(MyObj &&rhs) = delete;
};
int MyObj::total = 0;

TEST("require that latched objects are appropriately destructed") {
    int with_state = 0;
    int total_sample = 0;
    {
        Latch<MyObj> latch1;
        Latch<MyObj> latch2;
        Latch<MyObj> latch3;
        latch2.write(MyObj(with_state));
        latch3.write(MyObj(with_state));
        latch2.read().detach();
        EXPECT_TRUE(!latch1.has_value());
        EXPECT_TRUE(!latch2.has_value());
        EXPECT_TRUE(latch3.has_value());
        EXPECT_EQUAL(with_state, 0);
        EXPECT_GREATER_EQUAL(MyObj::total, 1);
        total_sample = MyObj::total;
    }
    EXPECT_EQUAL(MyObj::total, total_sample + 1);
    EXPECT_EQUAL(with_state, 1);
}

TEST_MAIN() { TEST_RUN_ALL(); }
