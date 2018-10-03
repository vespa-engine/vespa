// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

struct Add : Rendezvous<size_t, std::pair<size_t, size_t> > {
    Add(size_t n) : Rendezvous<size_t, std::pair<size_t, size_t> >(n) {}
    void mingle() override {
        size_t sum = 0;
        for (size_t i = 0; i < size(); ++i) {
            sum += in(i);
        }
        for (size_t i = 0; i < size(); ++i) {
            out(i) = std::make_pair(sum, in(0));
        }
    }
};

struct Modify : Rendezvous<size_t, size_t> {
    Modify(size_t n) : Rendezvous<size_t, size_t>(n) {}
    void mingle() override {
        for (size_t i = 0; i < size(); ++i) {
            in(i) += 1;
        }
        for (size_t i = 0; i < size(); ++i) {
            out(i) = in(i);
        }
    }
};

template <typename T>
struct Swap : Rendezvous<T, T> {
    using Rendezvous<T, T>::in;
    using Rendezvous<T, T>::out;
    Swap() : Rendezvous<T, T>(2) {}
    void mingle() override {
        out(0) = std::move(in(1));
        out(1) = std::move(in(0));
    }
};

TEST("require that creating an empty rendezvous will fail") {
    EXPECT_EXCEPTION(Add(0), IllegalArgumentException, "");
}

TEST_F("require that a single thread can mingle with itself within a rendezvous", Add(1)) {
    EXPECT_EQUAL(10u, f1.rendezvous(10).first);
    EXPECT_EQUAL(20u, f1.rendezvous(20).first);
    EXPECT_EQUAL(30u, f1.rendezvous(30).first);
}

TEST_MT_F("require that rendezvous can mingle multiple threads", 10, Add(num_threads)) {
    EXPECT_EQUAL(45u, f1.rendezvous(thread_id).first);
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
        EXPECT_EQUAL(3u, f1.rendezvous(thread_id).first);
        EXPECT_TRUE(f2.await(25000));
    } else {
        EXPECT_EQUAL(3u, f1.rendezvous(thread_id).first);
        f2.countDown();
    }
}

TEST_MT_F("require that rendezvous can be used multiple times", 10, Add(num_threads)) {
    EXPECT_EQUAL(45u, f1.rendezvous(thread_id).first);
    EXPECT_EQUAL(45u, f1.rendezvous(thread_id).first);
    EXPECT_EQUAL(45u, f1.rendezvous(thread_id).first);
    EXPECT_EQUAL(45u, f1.rendezvous(thread_id).first);
    EXPECT_EQUAL(45u, f1.rendezvous(thread_id).first);
}

TEST_MT_FF("require that rendezvous can be run with additional threads", 100, Add(10), CountDownLatch(10)) {
    auto res = f1.rendezvous(thread_id);
    TEST_BARRIER();
    if (res.second == thread_id) {
        EXPECT_EQUAL(4950u, f1.rendezvous(res.first).first);
        f2.countDown();
    }
    EXPECT_TRUE(f2.await(25000));
}

TEST_MT_F("require that mingle can modify its own copy of input values", 10, Modify(num_threads)) {
    size_t my_input = thread_id;
    size_t my_output = f1.rendezvous(my_input);
    EXPECT_EQUAL(my_input, thread_id);
    EXPECT_EQUAL(my_output, thread_id + 1);
}

TEST_MT_F("require that threads can exchange non-copyable state", 2, Swap<std::unique_ptr<size_t> >()) {
    auto other = f1.rendezvous(std::make_unique<size_t>(thread_id));
    EXPECT_EQUAL(*other, 1 - thread_id);
}

TEST_MAIN() { TEST_RUN_ALL(); }
