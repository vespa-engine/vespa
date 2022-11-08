// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/coro/generator.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <ranges>
#include <vector>

using vespalib::coro::Generator;
using vespalib::BenchmarkTimer;

std::vector<size_t> make_data() __attribute__((noinline));
std::vector<size_t> make_data(size_t size) {
    std::vector<size_t> data;
    for (size_t i = 0; i < size; ++i) {
        data.push_back(i);
    }
    return data;
}

template <std::ranges::input_range T>
size_t calc_sum(T&& values) {
    size_t sum = 0;
    for (auto&& value: values) {
        sum += value;
    }
    return sum;
}

size_t calc_sum_direct(const std::vector<size_t> &values) {
    return calc_sum(values);
}

size_t calc_sum_wrapped(const std::vector<size_t> &values) {
    return calc_sum([](const std::vector<size_t> &inner_values)->Generator<size_t>
                    {
                        for (auto&& value: inner_values) {
                            co_yield value;
                        }
                    }(values));
}

TEST(GeneratorBench, direct_vs_wrapped_vector_for_loop) {
    std::vector<size_t> data = make_data(100000);
    double direct_ms = BenchmarkTimer::benchmark([&data](){
                                                     size_t sink = calc_sum_direct(data);
                                                     (void) sink;
                                                 }, 5.0) * 1000.0;
    fprintf(stderr, "direct: %g ms\n", direct_ms);
    double wrapped_ms = BenchmarkTimer::benchmark([&data](){
                                                      size_t sink = calc_sum_wrapped(data);
                                                      (void) sink;
                                                  }, 5.0) * 1000.0;
    fprintf(stderr, "wrapped: %g ms\n", wrapped_ms);
    fprintf(stderr, "ratio: %g\n", (wrapped_ms/direct_ms));
}

GTEST_MAIN_RUN_ALL_TESTS()
