// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/nested_loop.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::BenchmarkTimer;
using vespalib::eval::run_nested_loop;

using LIST = std::vector<size_t>;
using call_t = void (*)(const LIST &loop, const LIST &stride);

void perform_direct(const LIST &loop, const LIST &stride) {
    size_t idx1 = 0;
    size_t expect = 0;
    for (size_t i = 0; i < loop[0]; ++i, idx1 += stride[0]) {
        size_t idx2 = idx1;
        for (size_t j = 0; j < loop[1]; ++j, idx2 += stride[1]) {
            size_t idx3 = idx2;
            for (size_t k = 0; k < loop[2]; ++k, idx3 += stride[2]) {
                assert(idx3 == expect);
                ++expect;
            }
        }
    }
    assert(expect == 4096);
}

void perform_direct_lambda(const LIST &loop, const LIST &stride) {
    size_t expect = 0;
    auto fun = [&](size_t idx) {
        (void) idx;
        assert(idx == expect);
        ++expect;
    };
    size_t idx1 = 0;
    for (size_t i = 0; i < loop[0]; ++i, idx1 += stride[0]) {
        size_t idx2 = idx1;
        for (size_t j = 0; j < loop[1]; ++j, idx2 += stride[1]) {
            size_t idx3 = idx2;
            for (size_t k = 0; k < loop[2]; ++k, idx3 += stride[2]) {
                fun(idx3);
            }
        }
    }
    assert(expect == 4096);
}

void perform_generic(const LIST &loop, const LIST &stride) {
    size_t expect = 0;
    auto fun = [&](size_t idx) {
        (void) idx;
        assert(idx == expect);
        ++expect;
    };
    run_nested_loop(0, loop, stride, fun);
    assert(expect == 4096);
}

void perform_generic_isolate_first(const LIST &loop, const LIST &stride) {
    size_t expect = 0;
    auto fun = [&](size_t idx) {
        (void) idx;
        assert(idx == expect);
        ++expect;
    };
    run_nested_loop(0, loop, stride, fun, fun);
    assert(expect == 4096);
}

void nop() {}

double estimate_cost_us(call_t perform_fun) {
    LIST loop({16,16,16});
    LIST stride({256,16,1});
    return BenchmarkTimer::benchmark([&](){ perform_fun(loop, stride); }, nop, 100000, 5.0) * 1000.0 * 1000.0;
}

//-----------------------------------------------------------------------------

TEST(NestedLoopBenchmark, single_loop) {
    fprintf(stderr, "manual direct single loop: %g us\n", estimate_cost_us(perform_direct));
    fprintf(stderr, "manual call lambda single loop: %g us\n", estimate_cost_us(perform_direct_lambda));
    fprintf(stderr, "generic single loop: %g us\n", estimate_cost_us(perform_generic));
    fprintf(stderr, "generic single loop (isolate first): %g us\n", estimate_cost_us(perform_generic_isolate_first));
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
