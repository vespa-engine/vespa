// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/rendezvous.h>
#include <vespa/vespalib/util/time.h>
#include <utility>
#include <thread>

using namespace vespalib;

struct Value {
    size_t value;
    Value() : value(42) {}
};

template <typename T, bool ext_id>
struct Empty : Rendezvous<int, T, ext_id> {
    Empty(size_t n) : Rendezvous<int, T, ext_id>(n) {}
    void mingle() override {}
    T meet(size_t thread_id) {
        if constexpr (ext_id) {
            return this->rendezvous(0, thread_id);
        } else {
            (void) thread_id;
            return this->rendezvous(0);
        }
    }
};

template <bool ext_id>
struct Add : Rendezvous<size_t, std::pair<size_t, size_t>, ext_id> {
    using Super = Rendezvous<size_t, std::pair<size_t, size_t>, ext_id>;
    using Super::size;
    using Super::in;
    using Super::out;
    Add(size_t n) : Super(n) {}
    void mingle() override {
        size_t sum = 0;
        for (size_t i = 0; i < size(); ++i) {
            sum += in(i);
        }
        for (size_t i = 0; i < this->size(); ++i) {
            out(i) = std::make_pair(sum, in(0));
        }
    }
};

template <bool ext_id>
struct Modify : Rendezvous<size_t, size_t, ext_id> {
    using Super = Rendezvous<size_t, size_t, ext_id>;
    using Super::size;
    using Super::in;
    using Super::out;
    Modify(size_t n) : Super(n) {}
    void mingle() override {
        for (size_t i = 0; i < size(); ++i) {
            in(i) += 1;
        }
        for (size_t i = 0; i < size(); ++i) {
            out(i) = in(i);
        }
    }
};

template <typename T, bool ext_id>
struct Swap : Rendezvous<T, T, ext_id> {
    using Super = Rendezvous<T, T, ext_id>;
    using Super::size;
    using Super::in;
    using Super::out;
    Swap() : Super(2) {}
    void mingle() override {
        out(0) = std::move(in(1));
        out(1) = std::move(in(0));
    }
};

template <bool ext_id>
struct DetectId : Rendezvous<int, size_t, ext_id> {
    using Super = Rendezvous<int, size_t, ext_id>;
    using Super::size;
    using Super::in;
    using Super::out;
    DetectId(size_t n) : Super(n) {}
    void mingle() override {
        for (size_t i = 0; i < size(); ++i) {
            out(i) = i;
        }
    }
    size_t meet(size_t thread_id) {
        if constexpr (ext_id) {
            return this->rendezvous(0, thread_id);
        } else {
            (void) thread_id;
            return this->rendezvous(0);
        }
    }
};

struct Any : Rendezvous<bool, bool> {
    Any(size_t n) : Rendezvous<bool, bool>(n) {}
    void mingle() override {
        bool result = false;
        for (size_t i = 0; i < size(); ++i) {
            result |= in(i);
        }
        for (size_t i = 0; i < size(); ++i) {
            out(i) = result;
        }
    }
    bool check(bool flag) { return this->rendezvous(flag); }
};

TEST("require that creating an empty rendezvous will fail") {
    EXPECT_EXCEPTION(Add<false>(0), IllegalArgumentException, "");
    EXPECT_EXCEPTION(Add<true>(0), IllegalArgumentException, "");
}

TEST_FF("require that a single thread can mingle with itself within a rendezvous", Add<false>(1), Add<true>(1)) {
    EXPECT_EQUAL(10u, f1.rendezvous(10).first);
    EXPECT_EQUAL(20u, f1.rendezvous(20).first);
    EXPECT_EQUAL(30u, f1.rendezvous(30).first);
    EXPECT_EQUAL(10u, f2.rendezvous(10, thread_id).first);
    EXPECT_EQUAL(20u, f2.rendezvous(20, thread_id).first);
    EXPECT_EQUAL(30u, f2.rendezvous(30, thread_id).first);
}

TEST_MT_FF("require that rendezvous can mingle multiple threads", 10, Add<false>(num_threads), Add<true>(num_threads)) {
    EXPECT_EQUAL(45u, f1.rendezvous(thread_id).first);
    EXPECT_EQUAL(45u, f2.rendezvous(thread_id, thread_id).first);
}

template <bool ext_id> using Empty1 = Empty<Value, ext_id>;
template <bool ext_id> using Empty2 = Empty<size_t, ext_id>;

TEST_MT_FFFF("require that unset rendezvous outputs are default constructed", 10,
             Empty1<false>(num_threads), Empty2<false>(num_threads),
             Empty1<true>(num_threads), Empty2<true>(num_threads))
{
    EXPECT_EQUAL(42u, f1.meet(thread_id).value);
    EXPECT_EQUAL(0u, f2.meet(thread_id));
    EXPECT_EQUAL(42u, f3.meet(thread_id).value);
    EXPECT_EQUAL(0u, f4.meet(thread_id));
}

TEST_MT_FFFF("require that mingle is not called until all threads are present", 3,
             Add<false>(num_threads), CountDownLatch(num_threads - 1),
             Add<true>(num_threads), CountDownLatch(num_threads - 1))
{
    for (bool ext_id: {false, true}) {
        CountDownLatch &latch = ext_id ? f4 : f2;
        if (thread_id == 0) {
            EXPECT_FALSE(latch.await(20ms));
            if (ext_id) {
                EXPECT_EQUAL(3u, f3.rendezvous(thread_id, thread_id).first);
            } else {
                EXPECT_EQUAL(3u, f1.rendezvous(thread_id).first);
            }
            EXPECT_TRUE(latch.await(25s));
        } else {
            if (ext_id) {
                EXPECT_EQUAL(3u, f3.rendezvous(thread_id, thread_id).first);
            } else {
                EXPECT_EQUAL(3u, f1.rendezvous(thread_id).first);
            }
            latch.countDown();
        }
    }
}

TEST_MT_FF("require that rendezvous can be used multiple times", 10, Add<false>(num_threads), Add<true>(num_threads)) {
    EXPECT_EQUAL(45u, f1.rendezvous(thread_id).first);
    EXPECT_EQUAL(45u, f2.rendezvous(thread_id, thread_id).first);
    EXPECT_EQUAL(45u, f1.rendezvous(thread_id).first);
    EXPECT_EQUAL(45u, f2.rendezvous(thread_id, thread_id).first);
    EXPECT_EQUAL(45u, f1.rendezvous(thread_id).first);
    EXPECT_EQUAL(45u, f2.rendezvous(thread_id, thread_id).first);
}

TEST_MT_FF("require that rendezvous can be run with additional threads", 100, Add<false>(10), CountDownLatch(10)) {
    auto res = f1.rendezvous(thread_id);
    TEST_BARRIER();
    if (res.second == thread_id) {
        EXPECT_EQUAL(4950u, f1.rendezvous(res.first).first);
        f2.countDown();
    }
    EXPECT_TRUE(f2.await(25s));
}

TEST_MT_FF("require that mingle can modify its own copy of input values", 10, Modify<false>(num_threads), Modify<true>(num_threads)) {
    size_t my_input = thread_id;
    size_t my_output1 = f1.rendezvous(my_input);
    size_t my_output2 = f2.rendezvous(my_input, thread_id);
    EXPECT_EQUAL(my_input, thread_id);
    EXPECT_EQUAL(my_output1, thread_id + 1);
    EXPECT_EQUAL(my_output2, thread_id + 1);
}

using Swap_false = Swap<std::unique_ptr<size_t>,false>;
using Swap_true = Swap<std::unique_ptr<size_t>,true>;

TEST_MT_FF("require that threads can exchange non-copyable state", 2, Swap_false(), Swap_true()) {
    auto other1 = f1.rendezvous(std::make_unique<size_t>(thread_id));
    EXPECT_EQUAL(*other1, 1 - thread_id);
    auto other2 = f2.rendezvous(std::make_unique<size_t>(thread_id), thread_id);
    EXPECT_EQUAL(*other2, 1 - thread_id);
}

TEST_MT_F("require that participation id can be explicitly defined", 10, DetectId<true>(num_threads)) {
    for (size_t i = 0; i < 128; ++i) {
        size_t my_id = f1.meet(thread_id);
        EXPECT_EQUAL(my_id, thread_id);
    }
}

TEST_MT_FF("require that participation id is unstable when not explicitly defined", 10, DetectId<false>(num_threads), Any(num_threads)) {
    bool id_mismatch = false;
    size_t old_id = f1.meet(thread_id);
    for (size_t i = 0; !id_mismatch; ++i) {
        if ((i % num_threads) == thread_id) {
            std::this_thread::sleep_for(std::chrono::milliseconds(i));
        }
        size_t new_id = f1.meet(thread_id);
        if (new_id != old_id) {
            id_mismatch = true;
        }
        id_mismatch = f2.check(id_mismatch);
    }
    EXPECT_TRUE(id_mismatch);
}

TEST_MAIN() { TEST_RUN_ALL(); }
