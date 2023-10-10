// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hidden_sequence.h"
#include <vespa/vespalib/coro/generator.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/sequence.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <ranges>
#include <vector>

using vespalib::coro::Generator;
using vespalib::BenchmarkTimer;
using vespalib::Sequence;

template <std::ranges::input_range T>
size_t calc_sum(T &&values) {
    size_t sum = 0;
    for (auto&& value: values) {
        sum += value;
    }
    return sum;
}

size_t calc_sum(Sequence<size_t> &seq) {
    size_t sum = 0;
    for (; seq.valid(); seq.next()) {
        sum += seq.get();
    }
    return sum;
}
size_t calc_sum(Sequence<size_t>::UP &ptr) { return calc_sum(*ptr); }

std::vector<size_t> make_data() __attribute__((noinline));
std::vector<size_t> make_data() {
    size_t n = 1000000;
    std::vector<size_t> data;
    data.reserve(n);
    for (size_t i = 0; i < n; ++i) {
        data.push_back(i + n);
    }
    return data;
}

struct MySeq : Sequence<size_t> {
    const std::vector<size_t> &data;
    size_t pos;
    MySeq(const std::vector<size_t> &data_in)
      : data(data_in), pos(0) {}
    bool valid() const override { return pos < data.size(); }
    size_t get() const override { return data[pos]; }
    void next() override { ++pos; }
};

size_t calc_sum_direct(const std::vector<size_t> &data) {
    return calc_sum(data);
}

size_t calc_sum_sequence(const std::vector<size_t> &data) {
    MySeq my_seq(data);
    return calc_sum(my_seq);
}

Generator<size_t> gen_values(const std::vector<size_t> &data) {
    for (size_t value: data) {
        co_yield value;
    }
}

size_t calc_sum_generator(const std::vector<size_t> &data) {
    return calc_sum(gen_values(data));
}

Generator<size_t> gen_values_noinline(const std::vector<size_t> &data) __attribute__((noinline));
Generator<size_t> gen_values_noinline(const std::vector<size_t> &data) {
    for (size_t value: data) {
        co_yield value;
    }
}

size_t calc_sum_generator_noinline(const std::vector<size_t> &data) {
    return calc_sum(gen_values_noinline(data));
}

double bench(auto fun, const std::vector<size_t> &data, size_t &res) {
    BenchmarkTimer timer(5.0);
    while (timer.has_budget()) {
        timer.before();
        res = fun(data);
        timer.after();
    }
    return timer.min_time() * 1000.0;
}

double bench_indirect(auto factory, const std::vector<size_t> &data, size_t &res) {
    BenchmarkTimer timer(5.0);
    while (timer.has_budget()) {
        auto seq = factory(data);
        timer.before();
        res = calc_sum(seq);
        timer.after();
    }
    return timer.min_time() * 1000.0;
}

TEST(GeneratorBench, direct_vs_generated_for_loop) {
    auto data = make_data();
    size_t result[5] = { 0, 1, 2, 3, 4 };
    double sequence_ms = bench(calc_sum_sequence, data, result[0]);
    fprintf(stderr, "sequence: %g ms\n", sequence_ms);
    double hidden_sequence_ms = bench_indirect(make_ext_seq, data, result[1]);
    fprintf(stderr, "hidden sequence: %g ms\n", hidden_sequence_ms);
    double generator_noinline_ms = bench(calc_sum_generator_noinline, data, result[2]);
    fprintf(stderr, "generator_noinline: %g ms\n", generator_noinline_ms);
    double generator_ms = bench(calc_sum_generator, data, result[3]);
    fprintf(stderr, "generator: %g ms\n", generator_ms);
    double direct_ms = bench(calc_sum_direct, data, result[4]);
    fprintf(stderr, "direct: %g ms\n", direct_ms);
    EXPECT_EQ(result[0], result[1]);
    EXPECT_EQ(result[0], result[2]);
    EXPECT_EQ(result[0], result[3]);
    EXPECT_EQ(result[0], result[4]);
    fprintf(stderr, "ratio (generator/direct): %g\n", (generator_ms/direct_ms));
    fprintf(stderr, "ratio (generator_noinline/generator): %g\n", (generator_noinline_ms/generator_ms));
    fprintf(stderr, "ratio (sequence/generator_noinline): %g\n", (sequence_ms/generator_noinline_ms));
    fprintf(stderr, "ratio (sequence/generator): %g\n", (sequence_ms/generator_ms));
}

GTEST_MAIN_RUN_ALL_TESTS()
