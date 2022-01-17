// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/testkit/test_kit.h>

#include <sys/resource.h>
#include <thread>

using namespace vespalib;
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
        TEST_BARRIER(); // #2
        TEST_BARRIER(); // #3
        auto t1 = steady_clock::now();
        std::vector<duration> post_usage = sample(samplers);
        TEST_BARRIER(); // #4
        double wall = to_s(t1 - t0);
        std::vector<double> load(4, 0.0);
        for (size_t i = 0; i < 4; ++i) {
            load[i] = to_s(post_usage[i] - pre_usage[i]) / wall;
        }
        EXPECT_GREATER(load[3], load[0]);
        fprintf(stderr, "loads: { %.2f, %.2f, %.2f, %.2f }\n", load[0], load[1], load[2], load[3]);
    } else {
        int idx = (thread_id - 1);
        double target_load = double(thread_id - 1) / (num_threads - 2);
        auto sampler = cpu_usage::create_thread_sampler(force_mock, target_load);
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

TEST_MT_F("require that dummy thread-based CPU usage sampling with known expected load works", 5, std::vector<Sampler*>(4, nullptr)) {
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

//-----------------------------------------------------------------------------

void verify_category(CpuUsage::Category cat, size_t idx) {
    switch (cat) { // make sure we known all categories
    case CpuUsage::Category::SETUP:
    case CpuUsage::Category::READ:
    case CpuUsage::Category::WRITE:
    case CpuUsage::Category::COMPACT:
    case CpuUsage::Category::MAINTAIN:
    case CpuUsage::Category::NETWORK:
    case CpuUsage::Category::OTHER:
        EXPECT_EQUAL(CpuUsage::index_of(cat), idx);
    }
}

TEST("require that CPU categories are as expected") {
    TEST_DO(verify_category(CpuUsage::Category::SETUP,   0u));
    TEST_DO(verify_category(CpuUsage::Category::READ,    1u));
    TEST_DO(verify_category(CpuUsage::Category::WRITE,   2u));
    TEST_DO(verify_category(CpuUsage::Category::COMPACT, 3u));
    TEST_DO(verify_category(CpuUsage::Category::MAINTAIN,4u));
    TEST_DO(verify_category(CpuUsage::Category::NETWORK, 5u));
    TEST_DO(verify_category(CpuUsage::Category::OTHER,   6u));
    EXPECT_EQUAL(CpuUsage::num_categories, 7u);
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
    a[CpuUsage::Category::MAINTAIN] = 5ms;
    a[CpuUsage::Category::NETWORK]  = 6ms;
    a[CpuUsage::Category::OTHER]    = 7ms;
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
    EXPECT_EQUAL(c[CpuUsage::Category::MAINTAIN], 55ms);
    EXPECT_EQUAL(c[CpuUsage::Category::NETWORK],  66ms);
    EXPECT_EQUAL(c[CpuUsage::Category::OTHER],    77ms);
}

//-----------------------------------------------------------------------------

// prototype for the class we want to use to integrate CPU usage into
// metrics as load values. NB: this class is not thread safe.

class CpuMonitor {
private:
    duration _old_usage;
    CpuUsage::TimedSample _old_sample;
    duration _min_delay;
    std::array<double,CpuUsage::num_categories+1> _load;

    static duration total_usage() {
        rusage usage;
        memset(&usage, 0, sizeof(usage));
        getrusage(RUSAGE_SELF, &usage);
        return from_timeval(usage.ru_utime) + from_timeval(usage.ru_stime);
    }

public:
    CpuMonitor(duration min_delay)
      : _old_usage(total_usage()),
        _old_sample(CpuUsage::sample()),
        _min_delay(min_delay),
        _load() {}

    std::array<double,CpuUsage::num_categories+1> get_load() {
        if (steady_clock::now() >= (_old_sample.first + _min_delay)) {
            auto new_usage = total_usage();
            auto new_sample = CpuUsage::sample();
            auto dt = to_s(new_sample.first - _old_sample.first);
            double sampled_load = 0.0;
            for (size_t i = 0; i < CpuUsage::num_categories; ++i) {
                _load[i] = to_s(new_sample.second[i] - _old_sample.second[i]) / dt;
                sampled_load += _load[i];
            }
            _load[CpuUsage::num_categories] = (to_s(new_usage - _old_usage) / dt) - sampled_load;
            _old_usage = new_usage;
            _old_sample = new_sample;
        }
        return _load;
    }
};

std::array<vespalib::string,CpuUsage::num_categories+1> names
{ "SETUP", "READ", "WRITE", "COMPACT", "MAINTAIN", "NETWORK", "OTHER", "UNKNOWN" };

void do_sample_cpu_usage(const EndTime &end_time) {
    CpuMonitor monitor(8ms);
    while (!end_time()) {
        std::this_thread::sleep_for(verbose ? 1s : 10ms);
        auto load = monitor.get_load();
        vespalib::string body;
        for (size_t i = 0; i < load.size(); ++i) {
            if (!body.empty()) {
                body.append(", ");
            }
            body.append(fmt("%s: %.2f", names[i].c_str(), load[i]));
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

void do_external_work(CpuUsage::Category cat1, CpuUsage::Category cat2, const EndTime &end_time) {
    auto my_usage1 = CpuUsage::use(cat1);
    while (!end_time()) {
        std::thread thread([cat2](){
                auto my_usage2 = CpuUsage::use(cat2);
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
    case 3: return do_nested_work(CpuUsage::Category::NETWORK, CpuUsage::Category::READ, f1);
    case 4: return do_external_work(CpuUsage::Category::SETUP, CpuUsage::Category::COMPACT, f1);
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
