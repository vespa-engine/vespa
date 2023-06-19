// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/spin_lock.h>
#include <vespa/vespalib/util/atomic.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <array>

using namespace vespalib;
using namespace vespalib::atomic;

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

template <typename T> size_t thread_safety_loop(T &lock, MyState &state, size_t thread_id, size_t thread_limit) {
    size_t loop_cnt = (thread_safety_work / thread_limit);
    TEST_BARRIER();
    auto t0 = steady_clock::now();
    TEST_BARRIER();
    if (thread_id < thread_limit) {
        for (size_t i = 0; i < loop_cnt; ++i) {
            std::lock_guard guard(lock);
            state.update();
        }
    }
    TEST_BARRIER();
    auto t1 = steady_clock::now();
    if (thread_id == 0) {
        auto t2 = steady_clock::now();
        size_t total_ms = count_ms(t2 - t0);
        fprintf(stderr, "---> thread_safety_loop with %zu threads used %zu ms\n", thread_limit, total_ms);
    }
    TEST_BARRIER();
    if (verbose && (thread_id < thread_limit)) {
        size_t local_ms = count_ms(t1 - t0);
        fprintf(stderr, "    -- thread %zu used %zu ms\n", thread_id, local_ms);
    }
    TEST_BARRIER();
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

TEST("require that locks can be used with lock_guard and unique_lock") {
    TEST_DO(basic_usage<DummyLock>());
    TEST_DO(basic_usage<SpinLock>());
}

TEST_MT_FF("report whether DummyLock is thread safe", 24, DummyLock(), MyState()) {
    size_t expect = thread_safety_loop(f1, f2, thread_id, 24);
    if (thread_id == 0) {
        f2.report(expect, "DummyLock");
    }
}

TEST_MT_FF("require that SpinLock is thread safe", 24, SpinLock(), MyState()) {
    size_t expect = thread_safety_loop(f1, f2, thread_id, 24);
    expect += thread_safety_loop(f1, f2, thread_id, 12);
    expect += thread_safety_loop(f1, f2, thread_id, 6);
    expect += thread_safety_loop(f1, f2, thread_id, 3);
    if (thread_id == 0) {
        f2.report(expect, "SpinLock");
        EXPECT_TRUE(f2.check(expect));
    }
}

TEST_MT_FF("require that std::mutex is thread safe", 24, std::mutex(), MyState()) {
    size_t expect = thread_safety_loop(f1, f2, thread_id, 24);
    expect += thread_safety_loop(f1, f2, thread_id, 12);
    expect += thread_safety_loop(f1, f2, thread_id, 6);
    expect += thread_safety_loop(f1, f2, thread_id, 3);
    if (thread_id == 0) {
        f2.report(expect, "std::mutex");
        EXPECT_TRUE(f2.check(expect));
    }
}

TEST("estimate single-threaded lock/unlock cost") {
    estimate_cost<DummyLock>("DummyLock");
    estimate_cost<SpinLock>("SpinLock");
    estimate_cost<std::mutex>("std::mutex");
}

int main(int argc, char **argv) {
    TEST_MASTER.init(__FILE__);
    if ((argc == 2) && (argv[1] == std::string("verbose"))) {
        verbose = true;
        budget = 10.0;
        thread_safety_work = 32000000;
    }
    TEST_RUN_ALL();
    return (TEST_MASTER.fini() ? 0 : 1);
}
