// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <atomic>
#include <mutex>
#include <condition_variable>
#include <thread>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/cpu_usage.h>

#ifdef __linux__
#include <linux/futex.h>
#include <sys/syscall.h>
#endif //linux__

#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;

struct State {
    std::atomic<uint32_t> value; // 0: ready, 1: wakeup, 2: stop, 3: initial
    static_assert(sizeof(value) == sizeof(uint32_t));
    State() : value(3) {}
    void set_ready() {
        value.store(0, std::memory_order_relaxed);
    }
    void set_wakeup() {
        value.store(1, std::memory_order_relaxed);
    }
    void set_stop() {
        value.store(2, std::memory_order_relaxed);
    }
    bool is_ready() const {
        return (value.load(std::memory_order_relaxed) == 0);
    }
    bool should_stop() const {
        return (value.load(std::memory_order_relaxed) == 2);
    }
};

struct UseSpin : State {
    void wakeup() {
        set_wakeup();
    }
    void stop() {
        set_stop();
    }
    void wait() {
        while (is_ready()) {
        }
    }
};

struct UseSpinYield : State {
    void wakeup() {
        set_wakeup();
    }
    void stop() {
        set_stop();
    }
    void wait() {
        while (is_ready()) {
            std::this_thread::yield();
        }
    }
};

struct UseCond : State {
    std::mutex mutex;
    std::condition_variable cond;
    void wakeup() {
        std::unique_lock<std::mutex> lock(mutex);
        set_wakeup();
        cond.notify_one();
    }
    void stop() {
        std::unique_lock<std::mutex> lock(mutex);
        set_stop();
        cond.notify_one();
    }
    void wait() {
        std::unique_lock<std::mutex> lock(mutex);
        while (is_ready()) {
            cond.wait(lock);
        }
    }
};

struct UseCondNolock : State {
    std::mutex mutex;
    std::condition_variable cond;
    void wakeup() {
        std::unique_lock<std::mutex> lock(mutex);
        set_wakeup();
        lock.unlock();
        cond.notify_one();
    }
    void stop() {
        std::unique_lock<std::mutex> lock(mutex);
        set_stop();
        lock.unlock();
        cond.notify_one();
    }
    void wait() {
        std::unique_lock<std::mutex> lock(mutex);
        while (is_ready()) {
            cond.wait(lock);
        }
    }
};

struct UsePipe : State {
    int pipefd[2];
    UsePipe() {
        int res = pipe(pipefd);
        assert(res == 0);
    }
    ~UsePipe() {
        close(pipefd[0]);
        close(pipefd[1]);
    }
    void wakeup() {
        set_wakeup();
        char token = 'T';
        [[maybe_unused]] ssize_t res = write(pipefd[1], &token, 1);
        // assert(res == 1);
    }
    void stop() {
        set_stop();
        char token = 'T';
        [[maybe_unused]] ssize_t res = write(pipefd[1], &token, 1);
        // assert(res == 1);
    }
    void wait() {
        char token_trash[128];
        [[maybe_unused]] ssize_t res = read(pipefd[0], token_trash, sizeof(token_trash));
        // assert(res == 1);
    }
};

#if __cpp_lib_atomic_wait
struct UseAtomic : State {
    void wakeup() {
        set_wakeup();
        value.notify_one();
    }
    void stop() {
        set_stop();
        value.notify_one();
    }
    void wait() {
        value.wait(0);
        // assert(!is_ready());
    }
};
#endif

#ifdef __linux__
struct UseFutex : State {
    void wakeup() {
        set_wakeup();
        syscall(SYS_futex, reinterpret_cast<uint32_t*>(&value),
                FUTEX_WAKE_PRIVATE, 1, nullptr, nullptr, 0);
    }
    void stop() {
        set_stop();
        syscall(SYS_futex, reinterpret_cast<uint32_t*>(&value),
                FUTEX_WAKE_PRIVATE, 1, nullptr, nullptr, 0);
    }
    void wait() {
        while (is_ready()) {
            syscall(SYS_futex, reinterpret_cast<uint32_t*>(&value),
                    FUTEX_WAIT_PRIVATE, 0, nullptr, nullptr, 0);
        }
    }
};
#endif //linux__

template <typename T>
struct Wakeup : T {
    using T::should_stop;
    using T::set_ready;
    using T::wait;
    cpu_usage::ThreadSampler::UP cpu;
    std::thread thread;
    Wakeup() : thread([this]{ run(); }) {}
    void run() {
        cpu = cpu_usage::create_thread_sampler();
        while (!should_stop()) {
            set_ready();
            wait();
        }
    }
};

constexpr size_t N = 8;
constexpr size_t WAKE_CNT = 1000000;

template <typename T> auto create_list() __attribute__((noinline));
template <typename T> auto create_list() {
    std::vector<T*> list;
    for (size_t i = 0; i < N; ++i) {
        list.push_back(new T());
    }
    return list;
}

template <typename T>
void destroy_list(T &list) __attribute__((noinline));
template <typename T>
void destroy_list(T &list) {
    for (auto *item: list) {
        item->stop();
        item->thread.join();
        delete item;
    }
}

template <typename T>
void wait_until_ready(const T &list) __attribute__((noinline));
template <typename T>
void wait_until_ready(const T &list) {
    size_t num_ready = 0;
    do {
        num_ready = 0;
        for (auto *item: list) {
            if (item->is_ready()) {
                ++num_ready;
            }
        }
    } while (num_ready < N);
}

template <typename T>
duration sample_cpu(T &list) {
    duration result = duration::zero();
    for (auto *item: list) {
        result += item->cpu->sample();
    }
    return result;
}

template <typename T>
auto perform_wakeups(T &list, size_t target) __attribute__((noinline));
template <typename T>
auto perform_wakeups(T &list, size_t target) {
    size_t wake_cnt = 0;
    size_t skip_cnt = 0;
    while (wake_cnt < target) {
        for (auto *item: list) {
            if (item->is_ready()) {
                item->wakeup();
                ++wake_cnt;
            } else {
                ++skip_cnt;
            }
        }
    }
    return std::make_pair(wake_cnt, skip_cnt);
}

template <typename T>
void benchmark() {
    auto list = create_list<T>();
    wait_until_ready(list);
    auto t0 = steady_clock::now();
    while ((steady_clock::now() - t0) < 1s) {
        // warmup
        perform_wakeups(list, WAKE_CNT / 64);
    }
    auto t1 = steady_clock::now();
    auto cpu0 = sample_cpu(list);
    auto res = perform_wakeups(list, WAKE_CNT);
    auto t2 = steady_clock::now();
    auto cpu1 = sample_cpu(list);
    wait_until_ready(list);
    destroy_list(list);
    double run_time = to_s(t2 - t1);
    double cpu_time = to_s(cpu1 - cpu0);
    double cpu_load = (cpu_time / (N * run_time));
    fprintf(stderr, "wakeups per second: %zu (skipped: %zu, cpu load: %.3f)\n",
            size_t(res.first / run_time), res.second, cpu_load);
}

TEST(WakeupBench, using_spin) { benchmark<Wakeup<UseSpin>>(); }
TEST(WakeupBench, using_spin_yield) { benchmark<Wakeup<UseSpinYield>>(); }
TEST(WakeupBench, using_cond) { benchmark<Wakeup<UseCond>>(); }
TEST(WakeupBench, using_cond_nolock) { benchmark<Wakeup<UseCondNolock>>(); }
TEST(WakeupBench, using_pipe) { benchmark<Wakeup<UsePipe>>(); }
#if __cpp_lib_atomic_wait
TEST(WakeupBench, using_atomic) { benchmark<Wakeup<UseAtomic>>(); }
#endif

#ifdef __linux__
TEST(WakeupBench, using_futex) { benchmark<Wakeup<UseFutex>>(); }
#endif //linux__

GTEST_MAIN_RUN_ALL_TESTS()
