// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/rendezvous.h>
#include <utility>

using namespace vespalib;

struct Value {
    size_t value;
    Value() : value(42) {}
};

template <typename T>
struct Empty : Rendezvous<int, T> {
    Empty(size_t n) : Rendezvous<int, T>(n) {}
    void mingle() override {}
    T meet() { return this->rendezvous(0); }
};

struct Add : Rendezvous<int, std::pair<int, int> > {
    Add(size_t n) : Rendezvous<int, std::pair<int, int> >(n) {}
    void mingle() override {
        int sum = 0;
        for (size_t i = 0; i < size(); ++i) {
            sum += in(i);
        }
        for (size_t i = 0; i < size(); ++i) {
            out(i) = std::make_pair(sum, in(0));
        }
    }
};

TEST("require that creating an empty rendezvous will fail") {
    EXPECT_EXCEPTION(Add(0), IllegalArgumentException, "");
}

TEST_F("require that a single thread can mingle with itself within a rendezvous", Add(1)) {
    EXPECT_EQUAL(10, f1.rendezvous(10).first);
    EXPECT_EQUAL(20, f1.rendezvous(20).first);
    EXPECT_EQUAL(30, f1.rendezvous(30).first);
}

TEST_MT_F("require that rendezvous can mingle multiple threads", 10, Add(num_threads)) {
    EXPECT_EQUAL(45, f1.rendezvous(thread_id).first);
}

typedef Empty<Value> Empty1;
typedef Empty<size_t> Empty2;
TEST_MT_FF("require that unset rendezvous outputs are default constructed", 10, Empty1(num_threads), Empty2(num_threads)) {
    EXPECT_EQUAL(42u, f1.meet().value);
    EXPECT_EQUAL(0u, f2.meet());
}

TEST_MT_FF("require that mingle is not called until all threads are present", 3, Add(num_threads),
           CountDownLatch(num_threads - 1))
{
    if (thread_id == 0) {
        EXPECT_FALSE(f2.await(20));
        EXPECT_EQUAL(3, f1.rendezvous(thread_id).first);
        EXPECT_TRUE(f2.await(25000));
    } else {
        EXPECT_EQUAL(3, f1.rendezvous(thread_id).first);
        f2.countDown();
    }
}

TEST_MT_F("require that rendezvous can be used multiple times", 10, Add(num_threads)) {
    EXPECT_EQUAL(45, f1.rendezvous(thread_id).first);
    EXPECT_EQUAL(45, f1.rendezvous(thread_id).first);
    EXPECT_EQUAL(45, f1.rendezvous(thread_id).first);
    EXPECT_EQUAL(45, f1.rendezvous(thread_id).first);
    EXPECT_EQUAL(45, f1.rendezvous(thread_id).first);
}

TEST_MT_FF("require that rendezvous can be run with additional threads", 100, Add(10), CountDownLatch(10)) {
    std::pair<int, int> res = f1.rendezvous(thread_id);
    TEST_BARRIER();
    if (size_t(res.second) == thread_id) {
        EXPECT_EQUAL(4950, f1.rendezvous(res.first).first);
        f2.countDown();
    }
    EXPECT_TRUE(f2.await(25000));
}

TEST_MAIN() { TEST_RUN_ALL(); }
