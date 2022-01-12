// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/testkit/test_kit.h>

#include <thread>

using namespace vespalib;

bool verbose = false;
size_t loop_cnt = 10;
double budget = 0.25;

using Sampler = vespalib::cpu_usage::ThreadSampler;

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
