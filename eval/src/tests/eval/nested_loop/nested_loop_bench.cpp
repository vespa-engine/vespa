// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/nested_loop.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::BenchmarkTimer;
using vespalib::eval::run_nested_loop;

using LIST = std::vector<size_t>;
using call_t = void (*)(const LIST &loop, const LIST &stride);

void perform_direct_1(const LIST &loop, const LIST &stride) {
    assert((loop.size() == 1) && (stride.size() == 1));
    size_t idx1 = 0;
    size_t expect = 0;
    for (size_t i = 0; i < loop[0]; ++i, idx1 += stride[0]) {
        assert(idx1 == expect);
        ++expect;
    }
    assert(expect == 4_Ki);
}

void perform_direct_2(const LIST &loop, const LIST &stride) {
    assert((loop.size() == 2) && (stride.size() == 2));
    size_t idx1 = 0;
    size_t expect = 0;
    for (size_t i = 0; i < loop[0]; ++i, idx1 += stride[0]) {
        size_t idx2 = idx1;
        for (size_t j = 0; j < loop[1]; ++j, idx2 += stride[1]) {
            assert(idx2 == expect);
            ++expect;
        }
    }
    assert(expect == 4_Ki);
}

void perform_direct_3(const LIST &loop, const LIST &stride) {
    assert((loop.size() == 3) && (stride.size() == 3));
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
    assert(expect == 4_Ki);
}

void perform_direct_4(const LIST &loop, const LIST &stride) {
    assert((loop.size() == 4) && (stride.size() == 4));
    size_t idx1 = 0;
    size_t expect = 0;
    for (size_t i = 0; i < loop[0]; ++i, idx1 += stride[0]) {
        size_t idx2 = idx1;
        for (size_t j = 0; j < loop[1]; ++j, idx2 += stride[1]) {
            size_t idx3 = idx2;
            for (size_t k = 0; k < loop[2]; ++k, idx3 += stride[2]) {
                size_t idx4 = idx3;
                for (size_t l = 0; l < loop[3]; ++l, idx4 += stride[3]) {
                    assert(idx4 == expect);
                    ++expect;
                }
            }
        }
    }
    assert(expect == 4_Ki);
}

void perform_direct_lambda_1(const LIST &loop, const LIST &stride) {
    assert((loop.size() == 1) && (stride.size() == 1));
    size_t expect = 0;
    auto fun = [&](size_t idx) {
        (void) idx;
        assert(idx == expect);
        ++expect;
    };
    size_t idx1 = 0;
    for (size_t i = 0; i < loop[0]; ++i, idx1 += stride[0]) {
        fun(idx1);
    }
    assert(expect == 4_Ki);
}

void perform_direct_lambda_2(const LIST &loop, const LIST &stride) {
    assert((loop.size() == 2) && (stride.size() == 2));
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
            fun(idx2);
        }
    }
    assert(expect == 4_Ki);
}

void perform_direct_lambda_3(const LIST &loop, const LIST &stride) {
    assert((loop.size() == 3) && (stride.size() == 3));
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
    assert(expect == 4_Ki);
}

void perform_direct_lambda_4(const LIST &loop, const LIST &stride) {
    assert((loop.size() == 4) && (stride.size() == 4));
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
                size_t idx4 = idx3;
                for (size_t l = 0; l < loop[3]; ++l, idx4 += stride[3]) {
                    fun(idx4);
                }
            }
        }
    }
    assert(expect == 4_Ki);
}

void perform_generic(const LIST &loop, const LIST &stride) {
    size_t expect = 0;
    auto fun = [&](size_t idx) {
        (void) idx;
        assert(idx == expect);
        ++expect;
    };
    run_nested_loop(0, loop, stride, fun);
    assert(expect == 4_Ki);
}

void nop() {}

double estimate_cost_1_us(call_t perform_fun) {
    LIST loop({4_Ki});
    LIST stride({1});
    return BenchmarkTimer::benchmark([&](){ perform_fun(loop, stride); }, nop, 10000, 5.0) * 1000.0 * 1000.0;
}

double estimate_cost_2_us(call_t perform_fun) {
    LIST loop({64,64});
    LIST stride({64,1});
    return BenchmarkTimer::benchmark([&](){ perform_fun(loop, stride); }, nop, 10000, 5.0) * 1000.0 * 1000.0;
}

double estimate_cost_3_us(call_t perform_fun) {
    LIST loop({16,16,16});
    LIST stride({256,16,1});
    return BenchmarkTimer::benchmark([&](){ perform_fun(loop, stride); }, nop, 10000, 5.0) * 1000.0 * 1000.0;
}

double estimate_cost_4_us(call_t perform_fun) {
    LIST loop({8,8,8,8});
    LIST stride({512,64,8,1});
    return BenchmarkTimer::benchmark([&](){ perform_fun(loop, stride); }, nop, 10000, 5.0) * 1000.0 * 1000.0;
}

//-----------------------------------------------------------------------------

TEST(NestedLoopBenchmark, single_loop) {
    fprintf(stderr, "---------------------------------------------------------------\n");
    fprintf(stderr, "manual direct single loop (1 layer): %g us\n", estimate_cost_1_us(perform_direct_1));
    fprintf(stderr, "manual call lambda single loop (1 layer): %g us\n", estimate_cost_1_us(perform_direct_lambda_1));
    fprintf(stderr, "generic single loop (1 layer): %g us\n", estimate_cost_1_us(perform_generic));
    fprintf(stderr, "---------------------------------------------------------------\n");
    fprintf(stderr, "manual direct single loop (2 layers): %g us\n", estimate_cost_2_us(perform_direct_2));
    fprintf(stderr, "manual call lambda single loop (2 layers): %g us\n", estimate_cost_2_us(perform_direct_lambda_2));
    fprintf(stderr, "generic single loop (2 layers): %g us\n", estimate_cost_2_us(perform_generic));
    fprintf(stderr, "---------------------------------------------------------------\n");
    fprintf(stderr, "manual direct single loop (3 layers): %g us\n", estimate_cost_3_us(perform_direct_3));
    fprintf(stderr, "manual call lambda single loop (3 layers): %g us\n", estimate_cost_3_us(perform_direct_lambda_3));
    fprintf(stderr, "generic single loop (3 layers): %g us\n", estimate_cost_3_us(perform_generic));
    fprintf(stderr, "---------------------------------------------------------------\n");
    fprintf(stderr, "manual direct single loop (4 layers): %g us\n", estimate_cost_4_us(perform_direct_4));
    fprintf(stderr, "manual call lambda single loop (4 layers): %g us\n", estimate_cost_4_us(perform_direct_lambda_4));
    fprintf(stderr, "generic single loop (4 layers): %g us\n", estimate_cost_4_us(perform_generic));
    fprintf(stderr, "---------------------------------------------------------------\n");
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
