// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/spin_lock.h>
#include <vespa/vespalib/util/rw_spin_lock.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/classname.h>
#include <vespa/vespalib/test/thread_meets.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <type_traits>
#include <ranges>
#include <random>
#include <array>

using namespace vespalib;
using namespace vespalib::test;

bool bench = false;
duration budget = 250ms;
constexpr size_t LOOP_CNT = 4096;
size_t thread_safety_work = 1'000'000;
size_t state_loop = 1;

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
    std::array<std::atomic<size_t>,SZ> state = {0,0,0,0,0};
    std::atomic<size_t> inconsistent_reads = 0;
    std::atomic<size_t> expected_writes = 0;
    [[nodiscard]] size_t update() {
        std::array<size_t,SZ> tmp;
        for (size_t i = 0; i < SZ; ++i) {
            tmp[i] = state[i].load(std::memory_order_relaxed);
        }
        for (size_t n = 0; n < state_loop; ++n) {
            for (size_t i = 0; i < SZ; ++i) {
                state[i].store(tmp[i] + 1, std::memory_order_relaxed);
            }
        }
        return 1;
    }
    [[nodiscard]] size_t peek() {
        size_t my_inconsistent_reads = 0;
        std::array<size_t,SZ> tmp;
        for (size_t i = 0; i < SZ; ++i) {
            tmp[i] = state[i].load(std::memory_order_relaxed);
        }
        for (size_t n = 0; n < state_loop; ++n) {
            for (size_t i = 0; i < SZ; ++i) {
                if (state[i].load(std::memory_order_relaxed) != tmp[i]) [[unlikely]] {
                    ++my_inconsistent_reads;
                }
            }
        }
        return my_inconsistent_reads;
    }
    void commit_inconsistent_reads(size_t n) {
        inconsistent_reads.fetch_add(n, std::memory_order_relaxed);
    }
    void commit_expected_writes(size_t n) {
        expected_writes.fetch_add(n, std::memory_order_relaxed);
    }
    [[nodiscard]] bool check() const {
        if (inconsistent_reads > 0) {
            return false;
        }
        for (const auto& value: state) {
            if (value != expected_writes) {
               return false;
            }
        }
        return true;
    }
    void report(const char *name) const {
        if (check()) {
            fprintf(stderr, "%s is thread safe\n", name);
        } else {
            fprintf(stderr, "%s is not thread safe\n", name);
            fprintf(stderr, "    inconsistent reads: %zu\n", inconsistent_reads.load());
            fprintf(stderr, "    expected %zu, got [%zu,%zu,%zu,%zu,%zu]\n",
                    expected_writes.load(), state[0].load(), state[1].load(), state[2].load(), state[3].load(), state[4].load());
        }
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
    constexpr double factor = LOOP_CNT;
    auto t0 = steady_clock::now();
    run_loop<LOOP_CNT>(work);
    return count_ns(steady_clock::now() - t0) / factor;
}

struct BenchmarkResult {
    double cost_ns;
    double range_ns;
    BenchmarkResult()
      : cost_ns(std::numeric_limits<double>::max()), range_ns(0.0) {}
    BenchmarkResult(double cost_ns_in, double range_ns_in)
      : cost_ns(cost_ns_in), range_ns(range_ns_in) {}
};

struct Meets {
    vespalib::test::ThreadMeets::Avg avg;
    vespalib::test::ThreadMeets::Range<double> range;
    Meets(size_t num_threads) : avg(num_threads), range(num_threads) {}
};

BenchmarkResult benchmark_ns(auto &&work, size_t num_threads = 1) {
    Meets meets(num_threads);
    auto entry = [&](Nexus &ctx) {
        Timer timer;
        BenchmarkResult result;
        for (bool once_more = true; ctx.vote(once_more); once_more = (timer.elapsed() < budget)) {
            auto my_ns = measure_ns(work);
            auto cost_ns = meets.avg(my_ns);
            auto range_ns = meets.range(my_ns);
            if (cost_ns < result.cost_ns) {
                result.cost_ns = cost_ns;
                result.range_ns = range_ns;
            }
        }
        return result;
    };
    return Nexus::run(num_threads, entry);
}

//-----------------------------------------------------------------------------

template <typename T>
void estimate_cost() {
    T lock;
    auto name = getClassName(lock);
    static_assert(basic_lockable<T>);
    fprintf(stderr, "%s exclusive lock/unlock: %g ns\n", name.c_str(),
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
void thread_safety_loop(Nexus &ctx, T &lock, MyState &state, Meets &meets, int read_bp) {
    Rnd rnd(ctx.thread_id());
    size_t write_cnt = 0;
    size_t bad_reads = 0;
    size_t loop_cnt = thread_safety_work / ctx.num_threads();
    ctx.barrier();
    auto t0 = steady_clock::now();
    for (size_t i = 0; i < loop_cnt; ++i) {
        if (rnd(read_bp)) {
            if constexpr (shared_lockable<T>) {
                std::shared_lock guard(lock);
                bad_reads += state.peek();
            } else {
                std::lock_guard guard(lock);
                bad_reads += state.peek();
            }
        } else {
            {
                std::lock_guard guard(lock);
                write_cnt += state.update();                
            }
        }
    }
    auto t1 = steady_clock::now();
    ctx.barrier();
    auto t2 = steady_clock::now();
    auto my_ms = count_ns(t1 - t0) / 1'000'000.0;
    auto total_ms = count_ns(t2 - t0) / 1'000'000.0;
    auto cost_ms = meets.avg(my_ms);
    auto range_ms = meets.range(my_ms);
    if (ctx.thread_id() == 0) {
        fprintf(stderr, "---> %s with %2zu threads (%5d bp r): avg: %10.2f ms, range: %10.2f ms, max: %10.2f ms\n",
                getClassName(lock).c_str(), ctx.num_threads(), read_bp, cost_ms, range_ms, total_ms);
    }
    state.commit_inconsistent_reads(bad_reads);
    state.commit_expected_writes(write_cnt);
}

//-----------------------------------------------------------------------------

TEST(RWSpinLockTest, different_guards_work_with_rw_spin_lock) {
    static_assert(basic_lockable<RWSpinLock>);
    static_assert(lockable<RWSpinLock>);
    static_assert(shared_lockable<RWSpinLock>);
    static_assert(can_upgrade<RWSpinLock>);
    RWSpinLock lock;
    { auto guard = std::lock_guard(lock); }
    { auto guard = std::unique_lock(lock); }
    { auto guard = std::shared_lock(lock); }
}

TEST(RWSpinLockTest, estimate_basic_costs) {
    Rnd rnd(123);
    MyState state;
    fprintf(stderr, "   rnd cost: %8.2f ns\n", benchmark_ns([&]{ rnd(50); }).cost_ns);
    fprintf(stderr, "  peek cost: %8.2f ns\n", benchmark_ns([&]{ (void) state.peek(); }).cost_ns);
    fprintf(stderr, "update cost: %8.2f ns\n", benchmark_ns([&]{ (void) state.update(); }).cost_ns);
}

template <typename T>
void benchmark_lock() {
    auto lock = std::make_unique<T>();
    auto state = std::make_unique<MyState>();
    for (size_t bp: {10000, 9999, 5000, 0}) {
        for (size_t num_threads: {8, 4, 2, 1}) {
            if (bench || (bp == 9999 && num_threads == 8)) {
                Meets meets(num_threads);
                Nexus::run(num_threads, [&](Nexus &ctx) {
                    thread_safety_loop(ctx, *lock, *state, meets, bp);
                });
            }
        }
    }
    state->report(getClassName(*lock).c_str());
    if (!std::same_as<T,DummyLock>) {
        EXPECT_TRUE(state->check());
    }
}

TEST(RWSpinLockTest, benchmark_dummy_lock)   { benchmark_lock<DummyLock>(); }
TEST(RWSpinLockTest, benchmark_rw_spin_lock) { benchmark_lock<RWSpinLock>(); }
TEST(RWSpinLockTest, benchmark_shared_mutex) { benchmark_lock<std::shared_mutex>(); }
TEST(RWSpinLockTest, benchmark_mutex)        { benchmark_lock<std::mutex>(); }
TEST(RWSpinLockTest, benchmark_spin_lock)    { benchmark_lock<SpinLock>(); }

TEST(RWSpinLockTest, estimate_single_threaded_costs) {
    estimate_cost<DummyLock>();
    estimate_cost<SpinLock>();
    estimate_cost<std::mutex>();
    estimate_cost<RWSpinLock>();
    estimate_cost<std::shared_mutex>();
}

int main(int argc, char **argv) {
    if (argc > 1 && (argv[1] == std::string("bench"))) {
        bench = true;
        budget = 5s;
        state_loop = 1024;
        fprintf(stderr, "running in benchmarking mode\n");
        ++argv;
        --argc;
    }
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
