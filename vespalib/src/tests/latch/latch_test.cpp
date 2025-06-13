// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/latch.h>

using namespace vespalib;
using vespalib::test::Nexus;

TEST(LatchTest, require_that_write_then_read_works) {
    Latch<int> latch;
    EXPECT_TRUE(!latch.has_value());
    latch.write(42);
    EXPECT_TRUE(latch.has_value());
    EXPECT_EQ(latch.read(), 42);
    EXPECT_TRUE(!latch.has_value());
}

TEST(LatchTest, require_that_read_waits_for_write) {
    size_t num_threads = 2;
    Latch<int> f1;
    Gate f2;
    TimeBomb f3(60);
    auto task = [&](Nexus &ctx){
                    if (ctx.thread_id() == 0) {
                        EXPECT_TRUE(!f2.await(10ms));
                        f1.write(123);
                        EXPECT_TRUE(f2.await(60s));
                    } else {
                        EXPECT_EQ(f1.read(), 123);
                        f2.countDown();
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(LatchTest, require_that_write_waits_for_read) {
    size_t num_threads = 2;
    Latch<int> f1;
    Gate f2;
    TimeBomb f3(60);
    auto task = [&](Nexus &ctx){
                    if (ctx.thread_id() == 0) {
                        f1.write(123);
                        f1.write(456);
                        f2.countDown();
                    } else {
                        EXPECT_TRUE(!f2.await(10ms));
                        EXPECT_EQ(f1.read(), 123);
                        EXPECT_TRUE(f2.await(60s));
                        EXPECT_EQ(f1.read(), 456);
                    }
                };
    Nexus::run(num_threads, task);
}

struct MyInt {
    int value;
    MyInt(int value_in) : value(value_in) {}
    MyInt(MyInt &&rhs) = default;
    MyInt(const MyInt &rhs) = delete;
    MyInt &operator=(const MyInt &rhs) = delete;
    MyInt &operator=(MyInt &&rhs) = delete;
};

TEST(LatchTest, require_that_un_assignable_non_default_constructable_move_only_objects_can_be_used) {
    Latch<MyInt> latch;
    latch.write(MyInt(1337));
    EXPECT_EQ(latch.read().value, 1337);
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

TEST(LatchTest, require_that_latched_objects_are_appropriately_destructed) {
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
        EXPECT_EQ(with_state, 0);
        EXPECT_GE(MyObj::total, 1);
        total_sample = MyObj::total;
    }
    EXPECT_EQ(MyObj::total, total_sample + 1);
    EXPECT_EQ(with_state, 1);
}

GTEST_MAIN_RUN_ALL_TESTS()
