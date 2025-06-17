// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/spin_lock.h>
#include <vespa/vespalib/util/atomic.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/classname.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <array>

using namespace vespalib;
using namespace vespalib::atomic;
using vespalib::test::Nexus;

bool verbose = false;
double budget = 0.25;
size_t thread_safety_work = 1000000;

struct DummyLock {
    void lock() {}
    void unlock() {}
};

//-----------------------------------------------------------------------------

struct MyState {
    static constexpr size_t SZ = 5;
    std::array<size_t,SZ> state = {0,0,0,0,0};
    void update() {
        std::array<size_t,SZ> tmp;
        for (size_t i = 0; i < SZ; ++i) {
            store_ref_relaxed(tmp[i], load_ref_relaxed(state[i]));
        }
        for (size_t i = 0; i < SZ; ++i) {
            store_ref_relaxed(state[i], load_ref_relaxed(tmp[i]) + 1);
        }
    }
    bool check(size_t expect) const {
        for (const auto& value: state) {
            if (load_ref_relaxed(value) != expect) {
                return false;
            }
        }
        return true;
    }
    void report(size_t expect, const char *name) const {
        if (check(expect)) {
            fprintf(stderr, "%s is thread safe\n", name);
        } else {
            fprintf(stderr, "%s is not thread safe\n", name);
            fprintf(stderr, "    expected %zu, got [%zu,%zu,%zu,%zu,%zu]\n",
                    expect, state[0], state[1], state[2], state[3], state[4]);
        }
    }
};

//-----------------------------------------------------------------------------

template <typename T> void  basic_usage() {
    T lock;
    {
        std::lock_guard guard(lock);
    }
    {
        std::unique_lock guard(lock);
    }
}

//-----------------------------------------------------------------------------

template <typename T> size_t thread_safety_loop(Nexus &ctx, T &lock, MyState &state, size_t thread_limit) {
    auto thread_id = ctx.thread_id();
    size_t loop_cnt = (thread_safety_work / thread_limit);
    ctx.barrier();
    auto t0 = steady_clock::now();
    ctx.barrier();
    if (thread_id < thread_limit) {
        for (size_t i = 0; i < loop_cnt; ++i) {
            std::lock_guard guard(lock);
            state.update();
        }
    }
    ctx.barrier();
    auto t1 = steady_clock::now();
    if (thread_id == 0) {
        auto t2 = steady_clock::now();
        size_t total_ms = count_ms(t2 - t0);
        fprintf(stderr, "---> thread_safety_loop with %zu threads used %zu ms\n", thread_limit, total_ms);
    }
    ctx.barrier();
    if (verbose && (thread_id < thread_limit)) {
        size_t local_ms = count_ms(t1 - t0);
        fprintf(stderr, "    -- thread %zu used %zu ms\n", thread_id, local_ms);
    }
    ctx.barrier();
    return (loop_cnt * thread_limit);
}

//-----------------------------------------------------------------------------

template <typename T> void estimate_cost(const char *name) __attribute__((noinline));
template <typename T> void estimate_cost(const char *name) {
    T lock;
    auto lock_loop = [&]()
                     {
                         // 250 * 4 = 1000 times lock/unlock
                         for (size_t i = 0; i < 250; ++i) {
                             // 4 times lock/unlock
                             lock.lock();
                             lock.unlock();
                             lock.lock();
                             lock.unlock();
                             lock.lock();
                             lock.unlock();
                             lock.lock();
                             lock.unlock();
                         }
                     };
    BenchmarkTimer timer(budget);
    while (timer.has_budget()) {
        timer.before();
        lock_loop();
        timer.after();
    }
    auto cost_ns = timer.min_time() * 1000.0 * 1000.0;
    fprintf(stderr, "%s: estimated lock/unlock time: %g ns\n", name, cost_ns);
}

//-----------------------------------------------------------------------------

TEST(SpinLockTest, require_that_locks_can_be_used_with_lock_guard_and_unique_lock) {
    GTEST_DO(basic_usage<DummyLock>());
    GTEST_DO(basic_usage<SpinLock>());
}

//-----------------------------------------------------------------------------

template <typename T>
class TypedLockTest : public ::testing::Test {};

TYPED_TEST_SUITE_P(TypedLockTest);

TYPED_TEST_P(TypedLockTest, thread_safety) {
    using LockType = TypeParam;
    bool is_dummy = std::same_as<LockType,DummyLock>;
    size_t num_threads = 24;
    LockType f1;
    MyState f2;
    auto task = [&](Nexus &ctx){
                    size_t expect = thread_safety_loop(ctx, f1, f2, 24);
                    if (!is_dummy) {
                        expect += thread_safety_loop(ctx, f1, f2, 12);
                        expect += thread_safety_loop(ctx, f1, f2, 6);
                        expect += thread_safety_loop(ctx, f1, f2, 3);
                    }
                    if (ctx.thread_id() == 0) {
                        f2.report(expect, getClassName(f1).c_str());
                        if (!is_dummy) {
                            EXPECT_TRUE(f2.check(expect));
                        }
                    }
                };
    Nexus::run(num_threads, task);
}

REGISTER_TYPED_TEST_SUITE_P(TypedLockTest, thread_safety);

using LockTypes = ::testing::Types<DummyLock,SpinLock,std::mutex>;
INSTANTIATE_TYPED_TEST_SUITE_P(MyLocks, TypedLockTest, LockTypes);

//-----------------------------------------------------------------------------

TEST(SpinLockTest, estimate_single_threaded_lock_unlock_cost) {
    estimate_cost<DummyLock>("DummyLock");
    estimate_cost<SpinLock>("SpinLock");
    estimate_cost<std::mutex>("std::mutex");
}

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    if ((argc == 2) && (argv[1] == std::string("verbose"))) {
        verbose = true;
        budget = 10.0;
        thread_safety_work = 32000000;
    }
    return RUN_ALL_TESTS();
}
