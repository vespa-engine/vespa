// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/test/thread_meets.h>
#include <vespa/vespalib/testkit/test_kit.h>

#include <sys/resource.h>
#include <thread>

using namespace vespalib;
using namespace vespalib::test;
using vespalib::make_string_short::fmt;

bool verbose = false;
size_t loop_cnt = 10;
double budget = 0.25;

using Sampler = vespalib::cpu_usage::ThreadSampler;

//-----------------------------------------------------------------------------

class EndTime {
private:
    steady_time _end_time;
public:
    EndTime(duration test_time) : _end_time(steady_clock::now() + test_time) {}
    bool operator()() const { return (steady_clock::now() >= _end_time); }
};

//-----------------------------------------------------------------------------

void be_busy(duration d) {
    if (d > 0ms) {
        volatile int tmp = 123;
        auto t0 = steady_clock::now();
        while ((steady_clock::now() - t0) < d) {
            for (int i = 0; i < 1000; ++i) {
                tmp = (tmp + i);
                tmp = (tmp - i);
            }
        }
    }
}

std::vector<duration> sample(const std::vector<Sampler*> &list) {
    std::vector<duration> result;
    result.reserve(list.size());
    for (auto *sampler: list) {
        result.push_back(sampler->sample());
    }
    return result;
}

//-----------------------------------------------------------------------------

void verify_sampling(size_t thread_id, size_t num_threads, std::vector<Sampler*> &samplers, bool force_mock) {
    if (thread_id == 0) {
        TEST_BARRIER(); // #1
        auto t0 = steady_clock::now();
        std::vector<duration> pre_usage = sample(samplers);
        auto pre_total = cpu_usage::total_cpu_usage();
        TEST_BARRIER(); // #2
        TEST_BARRIER(); // #3
        auto t1 = steady_clock::now();
        std::vector<duration> post_usage = sample(samplers);
        auto post_total = cpu_usage::total_cpu_usage();
        TEST_BARRIER(); // #4
        double wall = to_s(t1 - t0);
        std::vector<double> util(4, 0.0);
        for (size_t i = 0; i < 4; ++i) {
            util[i] = to_s(post_usage[i] - pre_usage[i]) / wall;
        }
        double total_util = to_s(post_total - pre_total) / wall;
        EXPECT_GREATER(util[3], util[0]);
        // NB: cannot expect total_util to be greater than util[3]
        // here due to mock utils being 'as expected' while valgrind
        // will cut all utils in about half.
        EXPECT_GREATER(total_util, util[0]);
        fprintf(stderr, "utils: { %.3f, %.3f, %.3f, %.3f }\n", util[0], util[1], util[2], util[3]);
        fprintf(stderr, "total util: %.3f\n", total_util);
    } else {
        int idx = (thread_id - 1);
        double target_util = double(thread_id - 1) / (num_threads - 2);
        auto sampler = cpu_usage::create_thread_sampler(force_mock, target_util);
        samplers[idx] = sampler.get();
        TEST_BARRIER(); // #1
        TEST_BARRIER(); // #2
        for (size_t i = 0; i < loop_cnt; ++i) {
            be_busy(std::chrono::milliseconds(idx));
        }
        TEST_BARRIER(); // #3
        TEST_BARRIER(); // #4
    }
}

//-----------------------------------------------------------------------------

TEST_MT_F("require that dummy thread-based CPU usage sampling with known expected util works", 5, std::vector<Sampler*>(4, nullptr)) {
    TEST_DO(verify_sampling(thread_id, num_threads, f1, true));
}

TEST_MT_F("require that external thread-based CPU usage sampling works", 5, std::vector<Sampler*>(4, nullptr)) {
    TEST_DO(verify_sampling(thread_id, num_threads, f1, false));
}

TEST("measure thread CPU clock overhead") {
    auto sampler = cpu_usage::create_thread_sampler();
    duration d;
    double min_time_us = BenchmarkTimer::benchmark([&d, &sampler]() noexcept { d = sampler->sample(); }, budget) * 1000000.0;
    fprintf(stderr, "approx overhead per sample (thread CPU clock): %f us\n", min_time_us);
}

TEST("measure total cpu usage overhead") {
    duration d;
    double min_time_us = BenchmarkTimer::benchmark([&d]() noexcept { d = cpu_usage::total_cpu_usage(); }, budget) * 1000000.0;
    fprintf(stderr, "approx overhead per RUsage sample: %f us\n", min_time_us);
}

//-----------------------------------------------------------------------------

void verify_category(CpuUsage::Category cat, size_t idx, const vespalib::string &name) {
    switch (cat) { // make sure we known all categories
    case CpuUsage::Category::SETUP:
    case CpuUsage::Category::READ:
    case CpuUsage::Category::WRITE:
    case CpuUsage::Category::COMPACT:
    case CpuUsage::Category::OTHER:
        EXPECT_EQUAL(CpuUsage::index_of(cat), idx);
        EXPECT_EQUAL(CpuUsage::name_of(cat), name);
    }
}

TEST("require that CPU categories are as expected") {
    TEST_DO(verify_category(CpuUsage::Category::SETUP,   0u, "setup"));
    TEST_DO(verify_category(CpuUsage::Category::READ,    1u, "read"));
    TEST_DO(verify_category(CpuUsage::Category::WRITE,   2u, "write"));
    TEST_DO(verify_category(CpuUsage::Category::COMPACT, 3u, "compact"));
    TEST_DO(verify_category(CpuUsage::Category::OTHER,   4u, "other"));
    EXPECT_EQUAL(CpuUsage::num_categories, 5u);
}

TEST("require that empty sample is zero") {
    CpuUsage::Sample sample;
    EXPECT_EQUAL(sample.size(), CpuUsage::num_categories);
    for (uint32_t i = 0; i < sample.size(); ++i) {
        EXPECT_EQUAL(sample[i].count(), 0);
    }
}

TEST("require that cpu samples can be manipulated and inspected") {
    CpuUsage::Sample a;
    CpuUsage::Sample b;
    const CpuUsage::Sample &c = a;
    a[CpuUsage::Category::SETUP]    = 1ms;
    a[CpuUsage::Category::READ]     = 2ms;
    a[CpuUsage::Category::WRITE]    = 3ms;
    a[CpuUsage::Category::COMPACT]  = 4ms;
    a[CpuUsage::Category::OTHER]    = 5ms;
    for (uint32_t i = 0; i < b.size(); ++i) {
        b[i] = 10ms * (i + 1);
    }
    a.merge(b);
    for (uint32_t i = 0; i < c.size(); ++i) {
        EXPECT_EQUAL(c[i], 11ms * (i + 1));
    }
    EXPECT_EQUAL(c[CpuUsage::Category::SETUP],    11ms);
    EXPECT_EQUAL(c[CpuUsage::Category::READ],     22ms);
    EXPECT_EQUAL(c[CpuUsage::Category::WRITE],    33ms);
    EXPECT_EQUAL(c[CpuUsage::Category::COMPACT],  44ms);
    EXPECT_EQUAL(c[CpuUsage::Category::OTHER],    55ms);
}

//-----------------------------------------------------------------------------

struct CpuUsage::Test {
    struct BlockingTracker : ThreadTracker {
        std::atomic<size_t> called;
        ThreadMeets::Nop sync_entry;
        ThreadMeets::Swap<Sample> swap_sample;
        BlockingTracker()
          : called(0), sync_entry(2), swap_sample() {}
        Sample sample() noexcept override {
            if (called++) {
                return Sample();
            }
            sync_entry();
            return swap_sample(Sample());
        }
    };
    struct SimpleTracker : ThreadTracker {
        Sample my_sample;
        std::atomic<size_t> called;
        SimpleTracker(Sample sample) noexcept
          : my_sample(sample), called(0) {}
        Sample sample() noexcept override {
            ++called;
            return my_sample;
        }
    };
    struct Fixture {
        CpuUsage my_usage;
        std::shared_ptr<BlockingTracker> blocking;
        std::vector<std::shared_ptr<SimpleTracker>> simple_list;
        Fixture() : my_usage() {}
        void add_blocking() {
            ASSERT_TRUE(!blocking);
            blocking = std::make_unique<BlockingTracker>();
            my_usage.add_thread(blocking);
        }
        void add_simple(Sample sample) {
            auto simple = std::make_shared<SimpleTracker>(sample);
            simple_list.push_back(simple);
            my_usage.add_thread(simple);
        }
        void add_remove_simple(Sample sample) {
            auto simple = std::make_shared<SimpleTracker>(sample);
            my_usage.add_thread(simple);
            my_usage.remove_thread(simple);
        }
        size_t count_threads() {
            Guard guard(my_usage._lock);
            return my_usage._threads.size();
        }
        bool is_sampling() {
            Guard guard(my_usage._lock);
            return my_usage._sampling;
        }
        size_t count_conflicts() {
            Guard guard(my_usage._lock);
            if (!my_usage._conflict) {
                return 0;
            }
            return my_usage._conflict->waiters;
        }
        size_t count_simple_samples() {
            size_t result = 0;
            for (const auto &simple: simple_list) {
                result += simple->called;
            }
            return result;
        }
        TimedSample sample() { return my_usage.sample_or_wait(); }
        ~Fixture() {
            if (blocking) {
                my_usage.remove_thread(std::move(blocking));
            }
            for (auto &simple: simple_list) {
                my_usage.remove_thread(std::move(simple));
            }
            ASSERT_EQUAL(count_threads(), 0u);
        }
    };
    struct TrackerImpl {
        ThreadTrackerImpl impl;
        TrackerImpl(cpu_usage::ThreadSampler::UP sampler)
          : impl(std::move(sampler)) {}
        CpuUsage::Sample sample() { return impl.sample(); }
        CpuUsage::Category set_category(CpuUsage::Category cat) { return impl.set_category(cat); }
    };
};

TEST_F("require that CpuUsage sample calls sample on thread trackers", CpuUsage::Test::Fixture()) {
    CpuUsage::Sample sample;
    sample[CpuUsage::Category::READ] = 10ms;
    f1.add_simple(sample);
    f1.add_simple(sample);
    f1.add_simple(sample);
    EXPECT_EQUAL(f1.count_threads(), 3u);
    auto result = f1.sample();
    EXPECT_EQUAL(result.second[CpuUsage::Category::READ], duration(30ms));
    EXPECT_EQUAL(f1.count_simple_samples(), 3u);
    result = f1.sample();
    EXPECT_EQUAL(result.second[CpuUsage::Category::READ], duration(60ms));
    EXPECT_EQUAL(f1.count_simple_samples(), 6u);
}

TEST_F("require that threads added and removed between CpuUsage sample calls are tracked", CpuUsage::Test::Fixture()) {
    CpuUsage::Sample sample;
    sample[CpuUsage::Category::READ] = 10ms;
    auto result = f1.sample();
    EXPECT_EQUAL(result.second[CpuUsage::Category::READ], duration(0ms));
    f1.add_remove_simple(sample);
    f1.add_remove_simple(sample);
    f1.add_remove_simple(sample);
    EXPECT_EQUAL(f1.count_threads(), 0u);
    result = f1.sample();
    EXPECT_EQUAL(result.second[CpuUsage::Category::READ], duration(30ms));
    result = f1.sample();
    EXPECT_EQUAL(result.second[CpuUsage::Category::READ], duration(30ms));
}

TEST_MT_FF("require that sample conflicts are resolved correctly", 5, CpuUsage::Test::Fixture(), std::vector<CpuUsage::TimedSample>(num_threads - 1)) {
    if (thread_id == 0) {
        CpuUsage::Sample s1;
        s1[CpuUsage::Category::SETUP] = 10ms;
        CpuUsage::Sample s2;
        s2[CpuUsage::Category::READ] = 20ms;
        CpuUsage::Sample s3;
        s3[CpuUsage::Category::WRITE] = 30ms;
        CpuUsage::Sample s4;
        s4[CpuUsage::Category::COMPACT] = 40ms;
        f1.add_blocking();
        f1.add_simple(s1); // should be sampled
        EXPECT_TRUE(!f1.is_sampling());
        EXPECT_EQUAL(f1.count_conflicts(), 0u);
        TEST_BARRIER(); // #1
        f1.blocking->sync_entry();
        EXPECT_TRUE(f1.is_sampling());
        while (f1.count_conflicts() < (num_threads - 2)) {
            // wait for appropriate number of conflicts
            std::this_thread::sleep_for(1ms);
        }
        f1.add_simple(s2); // should NOT be sampled (pending add)
        f1.add_remove_simple(s3); // should be sampled (pending remove);
        EXPECT_EQUAL(f1.count_threads(), 2u);
        EXPECT_TRUE(f1.is_sampling());
        EXPECT_EQUAL(f1.count_conflicts(), (num_threads - 2));
        f1.blocking->swap_sample(s4);
        TEST_BARRIER(); // #2
        EXPECT_TRUE(!f1.is_sampling());
        EXPECT_EQUAL(f1.count_conflicts(), 0u);
        EXPECT_EQUAL(f1.count_threads(), 3u);
        EXPECT_EQUAL(f2[0].second[CpuUsage::Category::SETUP],   duration(10ms));
        EXPECT_EQUAL(f2[0].second[CpuUsage::Category::READ],    duration(0ms));
        EXPECT_EQUAL(f2[0].second[CpuUsage::Category::WRITE],   duration(30ms));
        EXPECT_EQUAL(f2[0].second[CpuUsage::Category::COMPACT], duration(40ms));
        for (size_t i = 1; i < (num_threads - 1); ++i) {
            EXPECT_EQUAL(f2[i].first, f2[0].first);
            EXPECT_EQUAL(f2[i].second[CpuUsage::Category::SETUP],   f2[0].second[CpuUsage::Category::SETUP]);
            EXPECT_EQUAL(f2[i].second[CpuUsage::Category::READ],    f2[0].second[CpuUsage::Category::READ]);
            EXPECT_EQUAL(f2[i].second[CpuUsage::Category::WRITE],   f2[0].second[CpuUsage::Category::WRITE]);
            EXPECT_EQUAL(f2[i].second[CpuUsage::Category::COMPACT], f2[0].second[CpuUsage::Category::COMPACT]);
        }
    } else {
        TEST_BARRIER(); // #1
        f2[thread_id - 1] = f1.sample();
        TEST_BARRIER(); // #2
    }
}

//-----------------------------------------------------------------------------

struct DummySampler : public cpu_usage::ThreadSampler {
    duration &ref;
    DummySampler(duration &ref_in) : ref(ref_in) {}
    duration sample() const noexcept override { return ref; }
};

TEST("require that thread tracker implementation can track cpu use") {
    duration t = duration::zero();
    CpuUsage::Test::TrackerImpl tracker(std::make_unique<DummySampler>(t));
    t += 10ms;
    tracker.set_category(CpuUsage::Category::SETUP);
    t += 15ms;
    tracker.set_category(CpuUsage::Category::READ);
    t += 10ms;
    auto sample = tracker.sample();
    EXPECT_EQUAL(sample[CpuUsage::Category::SETUP], duration(15ms));
    EXPECT_EQUAL(sample[CpuUsage::Category::READ],  duration(10ms));
    EXPECT_EQUAL(sample[CpuUsage::Category::WRITE], duration(0ms));
    t += 15ms;
    tracker.set_category(CpuUsage::Category::WRITE);
    t += 10ms;
    sample = tracker.sample();
    EXPECT_EQUAL(sample[CpuUsage::Category::SETUP], duration(0ms));
    EXPECT_EQUAL(sample[CpuUsage::Category::READ],  duration(15ms));
    EXPECT_EQUAL(sample[CpuUsage::Category::WRITE], duration(10ms));
}

TEST("require that thread tracker implementation reports previous CPU category") {
    duration t = duration::zero();
    CpuUsage::Test::TrackerImpl tracker(std::make_unique<DummySampler>(t));
    EXPECT_EQUAL(CpuUsage::index_of(CpuUsage::Category::OTHER),
                 CpuUsage::index_of(tracker.set_category(CpuUsage::Category::SETUP)));
    EXPECT_EQUAL(CpuUsage::index_of(CpuUsage::Category::SETUP),
                 CpuUsage::index_of(tracker.set_category(CpuUsage::Category::READ)));
    EXPECT_EQUAL(CpuUsage::index_of(CpuUsage::Category::READ),
                 CpuUsage::index_of(tracker.set_category(CpuUsage::Category::READ)));
}

TEST("require that thread tracker implementation does not track OTHER cpu use") {
    duration t = duration::zero();
    CpuUsage::Test::TrackerImpl tracker(std::make_unique<DummySampler>(t));
    t += 10ms;
    tracker.set_category(CpuUsage::Category::OTHER);
    t += 15ms;
    tracker.set_category(CpuUsage::Category::READ);
    tracker.set_category(CpuUsage::Category::OTHER);
    t += 15ms;
    auto sample = tracker.sample();
    EXPECT_EQUAL(sample[CpuUsage::Category::READ],  duration(0ms));
    EXPECT_EQUAL(sample[CpuUsage::Category::OTHER], duration(0ms));
}

//-----------------------------------------------------------------------------

void do_sample_cpu_usage(const EndTime &end_time) {
    auto my_usage = CpuUsage::use(CpuUsage::Category::SETUP);
    CpuUtil cpu(8ms);
    while (!end_time()) {
        std::this_thread::sleep_for(verbose ? 1s : 10ms);
        auto util = cpu.get_util();
        vespalib::string body;
        for (size_t i = 0; i < util.size(); ++i) {
            if (!body.empty()) {
                body.append(", ");
            }
            body.append(fmt("%s: %.3f", CpuUsage::name_of(CpuUsage::Category(i)).c_str(), util[i]));
        }
        fprintf(stderr, "CPU: %s\n", body.c_str());
    }
}

void do_full_work(CpuUsage::Category cat, const EndTime &end_time) {
    auto my_usage = CpuUsage::use(cat);
    while (!end_time()) {
        be_busy(4ms);
    }
}

void do_some_work(CpuUsage::Category cat, const EndTime &end_time) {
    auto my_usage = CpuUsage::use(cat);
    while (!end_time()) {
        be_busy(4ms);
        std::this_thread::sleep_for(4ms);
    }
}

void do_nested_work(CpuUsage::Category cat1, CpuUsage::Category cat2, const EndTime &end_time) {
    auto my_usage1 = CpuUsage::use(cat1);
    while (!end_time()) {
        be_busy(4ms);
        auto my_usage2 = CpuUsage::use(cat2);
        be_busy(4ms);
    }
}

void do_external_work(CpuUsage::Category cat, const EndTime &end_time) {
    auto my_usage1 = CpuUsage::use(CpuUsage::Category::SETUP);
    while (!end_time()) {
        std::thread thread([cat](){
                auto my_usage2 = CpuUsage::use(cat);
                be_busy(4ms);
            });
        thread.join();
    }
}

TEST_MT_F("use top-level API to sample CPU usage", 5, EndTime(verbose ? 10s : 100ms)) {
    switch (thread_id) {
    case 0: return do_sample_cpu_usage(f1);
    case 1: return do_full_work(CpuUsage::Category::WRITE, f1);
    case 2: return do_some_work(CpuUsage::Category::READ, f1);
    case 3: return do_nested_work(CpuUsage::Category::OTHER, CpuUsage::Category::READ, f1);
    case 4: return do_external_work(CpuUsage::Category::COMPACT, f1);
    default: TEST_FATAL("missing thread id case");
    }
}

//-----------------------------------------------------------------------------

int main(int argc, char **argv) {
    TEST_MASTER.init(__FILE__);
    if ((argc == 2) && (argv[1] == std::string("verbose"))) {
        verbose = true;
        loop_cnt = 1000;
        budget = 5.0;
    }
    TEST_RUN_ALL();
    return (TEST_MASTER.fini() ? 0 : 1);
}
