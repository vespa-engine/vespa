// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/spin_lock.h>
#include <vespa/vespalib/util/rw_spin_lock.h>
#include <vespa/vespalib/util/atomic.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/classname.h>
#include <vespa/vespalib/test/thread_meets.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <type_traits>
#include <ranges>
#include <random>
#include <array>

using namespace vespalib;
using namespace vespalib::atomic;

duration budget = 250ms;
constexpr size_t LOOP_CNT = 4096;
constexpr double LOOP_FACTOR = double(LOOP_CNT);

//-----------------------------------------------------------------------------

struct DummyLock {
    constexpr DummyLock() noexcept {}
    // BasicLockable
    constexpr void lock() noexcept {}
    constexpr void unlock() noexcept {}
    // SharedLockable
    constexpr void lock_shared() noexcept {}
    [[nodiscard]] constexpr bool try_lock_shared() noexcept { return true; }
    constexpr void unlock_shared() noexcept {}
    // rw_upgrade_downgrade_lock
    [[nodiscard]] constexpr bool try_convert_read_to_write() noexcept { return true; }
    constexpr void convert_write_to_read() noexcept {}
};

//-----------------------------------------------------------------------------

struct MyState {
    static constexpr size_t SZ = 5;
    std::array<size_t,SZ> state = {0,0,0,0,0};
    std::atomic<size_t> inconsistent_reads = 0;
    void update() {
        std::array<size_t,SZ> tmp;
        for (size_t i = 0; i < SZ; ++i) {
            tmp[i] = load_ref_relaxed(state[i]);
        }
        for (int n = 0; n < 1024; ++n) {
            for (size_t i = 0; i < SZ; ++i) {
                store_ref_relaxed(state[i], tmp[i] + 1);
            }
        }
    }
    void peek() {
        std::array<size_t,SZ> tmp;
        for (size_t i = 0; i < SZ; ++i) {
            tmp[i] = load_ref_relaxed(state[i]);
        }
        for (int n = 0; n < 1024; ++n) {
            for (size_t i = 0; i < SZ; ++i) {
                if (load_ref_relaxed(state[i]) != tmp[i]) [[unlikely]] {
                    inconsistent_reads.fetch_add(1, std::memory_order_relaxed);
                }
            }
        }
    }
    bool check(size_t expect) const {
        if (inconsistent_reads > 0) {
            return false;
        }
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
            fprintf(stderr, "    inconsistent reads: %zu\n", inconsistent_reads.load());
            fprintf(stderr, "    expected %zu, got [%zu,%zu,%zu,%zu,%zu]\n",
                    expect, state[0], state[1], state[2], state[3], state[4]);
        }
    }
};

// do work while waiting for other threads to be ready
class ActiveBarrier : Rendezvous<bool,bool> {
private:
    std::atomic<uint32_t> _ready_cnt;
    void mingle() override {
        _ready_cnt.store(0, std::memory_order_relaxed);
    }
public:
    ActiveBarrier(size_t n) : Rendezvous<bool,bool>(n), _ready_cnt(0) {}
    void operator()(auto &do_work) {
        if (_ready_cnt.fetch_add(1, std::memory_order_relaxed) + 1 < size()) {
            do_work();
        }
        while (_ready_cnt.load(std::memory_order_relaxed) < size()) {
            do_work();
        }
        rendezvous(false);
    }
};

// random generator used to make per-thread decisions
class Rnd {
private:
    std::mt19937 _engine;
    std::uniform_int_distribution<int> _dist;    
public:
    Rnd(uint32_t seed) : _engine(seed), _dist(0,9999) {}
    bool operator()(int bp) { return _dist(_engine) < bp; }
};

void fork_join(auto &&thread_fun, size_t num_threads) {
    assert(num_threads > 0);
    ThreadPool pool;
    for (size_t i = 1; i < num_threads; ++i) {
        pool.start([i,&thread_fun]{ thread_fun(i); });
    }
    thread_fun(0);
    pool.join();
}

auto apply_merge(auto &&inputs, auto &&perform, auto &&merge) {
    using output_t = std::decay_t<decltype(perform(*inputs.begin()))>;
    std::mutex lock;
    std::optional<output_t> output;
    auto handle_result = [&](output_t result) {
                             std::lock_guard guard(lock);
                             if (output.has_value()) {
                                 output = merge(std::move(output).value(), std::move(result));
                             } else {
                                 output = std::move(result);
                             }
                         };
    ThreadPool pool;
    for (auto &&item: inputs) {
        pool.start([item,&perform,&handle_result]{ handle_result(perform(item)); });
    }
    pool.join();
    return output.value();
}

//-----------------------------------------------------------------------------

template<typename T>
concept basic_lockable = requires(T a) {
    { a.lock() } -> std::same_as<void>;
    { a.unlock() } -> std::same_as<void>;
};

template<typename T>
concept lockable = requires(T a) {
    { a.try_lock() } -> std::same_as<bool>;
    { a.lock() } -> std::same_as<void>;
    { a.unlock() } -> std::same_as<void>;
};

template<typename T>
concept shared_lockable = requires(T a) {
    { a.try_lock_shared() } -> std::same_as<bool>;
    { a.lock_shared() } -> std::same_as<void>;
    { a.unlock_shared() } -> std::same_as<void>;
};

template<typename T>
concept can_upgrade = requires(std::shared_lock<T> a, std::unique_lock<T> b) {
    { try_upgrade(std::move(a)) } -> std::same_as<std::unique_lock<T>>;
    { downgrade(std::move(b)) } -> std::same_as<std::shared_lock<T>>;
};

//-----------------------------------------------------------------------------

template <size_t N>
auto run_loop(auto &f) {
    static_assert(N % 4 == 0);
    for (size_t i = 0; i < N / 4; ++i) {
        f(); f(); f(); f();
    }
}

double measure_ns(auto &work) __attribute__((noinline));
double measure_ns(auto &work) {
    auto t0 = steady_clock::now();
    run_loop<LOOP_CNT>(work);
    return count_ns(steady_clock::now() - t0) / LOOP_FACTOR;
}

struct BenchmarkResult {
    double cost_ns = std::numeric_limits<double>::max();
    double range_ns = 0.0;
};

struct Meets {
    vespalib::test::ThreadMeets::Vote vote;
    vespalib::test::ThreadMeets::Avg avg;
    vespalib::test::ThreadMeets::Range<double> range;
    ActiveBarrier active_wait;
    Meets(size_t num_threads)
      : vote(num_threads), avg(num_threads), range(num_threads), active_wait(num_threads) {}
    ~Meets();
};
Meets::~Meets() = default;

BenchmarkResult benchmark_ns(auto &&work, size_t num_threads = 1) {
    Timer timer;
    Meets meets(num_threads);
    BenchmarkResult result;
    auto hook = [&](size_t thread_id) {
        for (bool once_more = true; meets.vote(once_more); once_more = (timer.elapsed() < budget)) {
            auto my_ns = measure_ns(work);
            meets.active_wait(work);
            auto cost_ns = meets.avg(my_ns);
            auto range_ns = meets.range(my_ns);
            if (thread_id == 0 && cost_ns < result.cost_ns) {
                result.cost_ns = cost_ns;
                result.range_ns = range_ns;
            }
        }
    };
    fork_join(hook, num_threads);
    return result;
}

//-----------------------------------------------------------------------------

template <typename T>
void estimate_cost() {
    T lock;
    auto name = getClassName(lock);
    static_assert(basic_lockable<T>);
    fprintf(stderr, "%s unique lock/unlock: %g ns\n", name.c_str(),
            benchmark_ns([&lock]{ lock.lock(); lock.unlock(); }).cost_ns);
    if constexpr (shared_lockable<T>) {
        fprintf(stderr, "%s shared lock/unlock: %g ns\n", name.c_str(),
                benchmark_ns([&lock]{ lock.lock_shared(); lock.unlock_shared(); }).cost_ns);
    }
    if constexpr (can_upgrade<T>) {
        auto guard = std::shared_lock(lock);
        fprintf(stderr, "%s upgrade/downgrade: %g ns\n", name.c_str(),
                benchmark_ns([&lock]{
                                 assert(lock.try_convert_read_to_write());
                                 lock.convert_write_to_read();
                             }).cost_ns);
    }
}

//-----------------------------------------------------------------------------

template <typename T>
size_t thread_safety_loop(T &lock, MyState &state, Meets &meets, int read_bp, size_t thread_id) {
    Timer timer;
    Rnd rnd(thread_id);
    size_t write_cnt = 0;
    BenchmarkResult result;
    auto do_work = [&]
                   {
                       if (rnd(read_bp)) {
                           if constexpr (shared_lockable<T>) {
                               std::shared_lock guard(lock);
                               state.peek();
                           } else {
                               std::lock_guard guard(lock);
                               state.peek();
                           }
                       } else {
                           {
                               std::lock_guard guard(lock);
                               state.update();                
                           }
                           ++write_cnt;
                       }
                   };
    for (bool once_more = true; meets.vote(once_more); once_more = (timer.elapsed() < budget)) {
        auto my_est = measure_ns(do_work);
        meets.active_wait(do_work);
        auto cost_ns = meets.avg(my_est);
        auto range_ns = meets.range(my_est);
        if (cost_ns < result.cost_ns) {
            result.cost_ns = cost_ns;
            result.range_ns = range_ns;
        }
    }
    if (thread_id == 0) {
        fprintf(stderr, "---> %s with %2zu threads (%5d bp r): %12.2f ns, range: %12.2f ns\n",
                getClassName(lock).c_str(), meets.vote.size(), read_bp, result.cost_ns, result.range_ns);
    }
    return write_cnt;
}

//-----------------------------------------------------------------------------

TEST("require that rw spin locks can be used with lock_guard, unique_lock and shared_lock") {
    static_assert(basic_lockable<RWSpinLock>);
    static_assert(lockable<RWSpinLock>);
    static_assert(shared_lockable<RWSpinLock>);
    static_assert(can_upgrade<RWSpinLock>);
    RWSpinLock lock;
    { auto guard = std::lock_guard(lock); }
    { auto guard = std::unique_lock(lock); }
    { auto guard = std::shared_lock(lock); }
}

TEST("estimate basic cost") {
    Rnd rnd(123);
    MyState state;
    fprintf(stderr, "   rnd cost: %8.2f ns\n", benchmark_ns([&]{ rnd(50); }).cost_ns);
    fprintf(stderr, "  peek cost: %8.2f ns\n", benchmark_ns([&]{ state.peek(); }).cost_ns);
    fprintf(stderr, "update cost: %8.2f ns\n", benchmark_ns([&]{ state.update(); }).cost_ns);
}

void benchmark_lock(auto &lock) {
    size_t expect = 0;
    auto state = std::make_unique<MyState>();
    for (size_t bp: {10000, 9999, 5000, 0}) {
        for (size_t num_threads: {8, 4, 2, 1}) {
            Meets meets(num_threads);
            auto hook = [&](size_t thread_id) {
                            return thread_safety_loop(lock, *state, meets, bp, thread_id);
                        };
            expect += apply_merge(std::views::iota(size_t(0), num_threads), hook, [](auto a, auto b){ return a + b; });
        }
    }
    state->report(expect, getClassName(lock).c_str());
    EXPECT_TRUE(state->check(expect));
}

TEST_F("benchmark RWSpinLock", RWSpinLock()) { benchmark_lock(f1); }
TEST_F("benchmark std::shared_mutex", std::shared_mutex()) { benchmark_lock(f1); }
TEST_F("benchmark std::mutex", std::mutex()) { benchmark_lock(f1); }
TEST_F("benchmark SpinLock", SpinLock()) { benchmark_lock(f1); }

TEST("estimate single-threaded lock/unlock cost") {
    estimate_cost<DummyLock>();
    estimate_cost<SpinLock>();
    estimate_cost<std::mutex>();
    estimate_cost<RWSpinLock>();
    estimate_cost<std::shared_mutex>();
}

int main(int argc, char **argv) {
    TEST_MASTER.init(__FILE__);
    if ((argc == 2) && (argv[1] == std::string("bench"))) {
        budget = 5s;
    }
    TEST_RUN_ALL();
    return (TEST_MASTER.fini() ? 0 : 1);
}
