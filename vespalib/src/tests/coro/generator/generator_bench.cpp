// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
size_t calc_sum(T&& values) {
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

struct ValueRange {
    using iterator_concept = std::input_iterator_tag;
    using difference_type = std::ptrdiff_t;
    using value_type = size_t;
    size_t first;
    size_t last;
    ValueRange() noexcept : first(0), last(0) {}
    ValueRange(size_t first_in, size_t last_in) noexcept
      : first(first_in), last(last_in) {}
    auto begin() noexcept { return ValueRange(first, last); }
    auto end() const noexcept { return std::default_sentinel_t(); }
    bool operator==(std::default_sentinel_t) const noexcept { return (first == last); }
    ValueRange &operator++() noexcept { ++first; return *this; }
    void operator++(int) noexcept { operator++(); }
    size_t operator*() const noexcept { return first; }
};

size_t calc_sum_direct(size_t first, size_t last) {
    return calc_sum(ValueRange(first, last));
}

Generator<size_t> gen_values(size_t first, size_t last) {
    for (size_t i = first; i < last; ++i) {
        co_yield i;
    }
}

size_t calc_sum_generator(size_t first, size_t last) {
    return calc_sum(gen_values(first, last));
}

Generator<const size_t &> gen_values_ref(size_t first, size_t last) {
    for (size_t i = first; i < last; ++i) {
        co_yield i;
    }
}

size_t calc_sum_generator_ref(size_t first, size_t last) {
    return calc_sum(gen_values_ref(first, last));
}

struct MySeq : Sequence<size_t> {
    size_t first;
    size_t last;
    MySeq(size_t first_in, size_t last_in)
      : first(first_in), last(last_in) {}
    bool valid() const override { return (first < last); }
    size_t get() const override { return first; }
    void next() override { ++first; }
};

size_t calc_sum_sequence(size_t first, size_t last) {
    MySeq seq(first, last);
    return calc_sum(seq);
}

TEST(GeneratorBench, direct_vs_generated_for_loop) {
    size_t first = 0;
    size_t last = 100000;
    size_t res_direct = 0;
    size_t res_generator = 1;
    size_t res_generator_ref = 2;
    size_t res_sequence = 3;
    double direct_ms = BenchmarkTimer::benchmark([first,last,&res_direct](){
                                                     res_direct = calc_sum_direct(first, last);
                                                 }, 5.0) * 1000.0;
    fprintf(stderr, "direct: %g ms\n", direct_ms);
    double generator_ms = BenchmarkTimer::benchmark([first,last,&res_generator](){
                                                        res_generator = calc_sum_generator(first, last);
                                                    }, 5.0) * 1000.0;
    fprintf(stderr, "generator: %g ms\n", generator_ms);
    double generator_ref_ms = BenchmarkTimer::benchmark([first,last,&res_generator_ref](){
                                                            res_generator_ref = calc_sum_generator_ref(first, last);
                                                        }, 5.0) * 1000.0;
    fprintf(stderr, "generator_ref: %g ms\n", generator_ref_ms);
    double sequence_ms = BenchmarkTimer::benchmark([first,last,&res_sequence](){
                                                       res_sequence = calc_sum_sequence(first, last);
                                                   }, 5.0) * 1000.0;
    fprintf(stderr, "sequence: %g ms\n", sequence_ms);
    EXPECT_EQ(res_direct, res_generator);
    EXPECT_EQ(res_direct, res_generator_ref);
    EXPECT_EQ(res_direct, res_sequence);
    fprintf(stderr, "ratio (generator/direct): %g\n", (generator_ms/direct_ms));
    fprintf(stderr, "ratio (generator/sequence): %g\n", (generator_ms/sequence_ms));
    fprintf(stderr, "ratio (generator/generator_ref): %g\n", (generator_ms/generator_ref_ms));
}

GTEST_MAIN_RUN_ALL_TESTS()
