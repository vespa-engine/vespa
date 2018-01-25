// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/llvm/compiled_function.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/eval/tensor/default_tensor_engine.h>

using namespace vespalib::eval;
using vespalib::BenchmarkTimer;
using vespalib::tensor::DefaultTensorEngine;

double budget = 0.25;

//-----------------------------------------------------------------------------

const char *function_str = "(0.35*p + 0.15*o + 0.30*q + 0.20*f) * w";

double gcc_function(double p, double o, double q, double f, double w) {
    return (0.35*p + 0.15*o + 0.30*q + 0.20*f) * w;
}

//-----------------------------------------------------------------------------

const char *big_function_str = "(0.35*p + 0.15*o + 0.30*q + 0.20*f) * w + "
    "(0.35*p + 0.15*o + 0.30*q + 0.20*f) * w + "
    "(0.35*p + 0.15*o + 0.30*q + 0.20*f) * w + "
    "(0.35*p + 0.15*o + 0.30*q + 0.20*f) * w";

double big_gcc_function(double p, double o, double q, double f, double w) {
    return (0.35*p + 0.15*o + 0.30*q + 0.20*f) * w +
        (0.35*p + 0.15*o + 0.30*q + 0.20*f) * w +
        (0.35*p + 0.15*o + 0.30*q + 0.20*f) * w +
        (0.35*p + 0.15*o + 0.30*q + 0.20*f) * w;
}

//-----------------------------------------------------------------------------

struct Fixture {
    Function function;
    InterpretedFunction interpreted_simple;
    InterpretedFunction interpreted;
    CompiledFunction separate;
    CompiledFunction array;
    CompiledFunction lazy;
    Fixture(const vespalib::string &expr)
        : function(Function::parse(expr)),
          interpreted_simple(SimpleTensorEngine::ref(), function, NodeTypes()),
          interpreted(DefaultTensorEngine::ref(), function,
                      NodeTypes(function, std::vector<ValueType>(function.num_params(), ValueType::double_type()))),
          separate(function, PassParams::SEPARATE),
          array(function, PassParams::ARRAY),
          lazy(function, PassParams::LAZY) {}
};

//-----------------------------------------------------------------------------

Fixture small(function_str);
Fixture big(big_function_str);
std::vector<double> test_params = {1.0, 2.0, 3.0, 4.0, 5.0};

//-----------------------------------------------------------------------------

double empty_function_5(double, double, double, double, double) { return 0.0; }
double estimate_cost_us(const std::vector<double> &params, CompiledFunction::expand<5>::type function) {
    CompiledFunction::expand<5>::type empty = empty_function_5;
    auto actual = [&](){function(params[0], params[1], params[2], params[3], params[4]);};
    auto baseline = [&](){empty(params[0], params[1], params[2], params[3], params[4]);};
    return BenchmarkTimer::benchmark(actual, baseline, budget) * 1000.0 * 1000.0;
}

TEST("measure small function eval/jit/gcc speed") {
    Fixture &fixture = small;
    CompiledFunction::expand<5>::type fun = gcc_function;

    EXPECT_EQUAL(fixture.separate.get_function<5>()(1,2,3,4,5), fun(1,2,3,4,5));
    EXPECT_EQUAL(fixture.separate.get_function<5>()(5,4,3,2,1), fun(5,4,3,2,1));

    double interpret_simple_time = fixture.interpreted_simple.estimate_cost_us(test_params, budget);
    fprintf(stderr, "interpret (simple): %g us\n", interpret_simple_time);
    double interpret_time = fixture.interpreted.estimate_cost_us(test_params, budget);
    fprintf(stderr, "interpret: %g us\n", interpret_time);
    double jit_time = estimate_cost_us(test_params, fixture.separate.get_function<5>());
    fprintf(stderr, "jit compiled: %g us\n", jit_time);
    double gcc_time = estimate_cost_us(test_params, fun);
    fprintf(stderr, "gcc compiled: %g us\n", gcc_time);
    double default_vs_simple_speed = (1.0/interpret_time)/(1.0/interpret_simple_time);
    double jit_vs_interpret_speed = (1.0/jit_time)/(1.0/interpret_time);
    double gcc_vs_jit_speed = (1.0/gcc_time)/(1.0/jit_time);
    fprintf(stderr, "default typed vs simple untyped interpret speed: %g\n", default_vs_simple_speed);
    fprintf(stderr, "jit speed compared to interpret: %g\n", jit_vs_interpret_speed);
    fprintf(stderr, "gcc speed compared to jit: %g\n", gcc_vs_jit_speed);

    double jit_time_separate = fixture.separate.estimate_cost_us(test_params, budget);
    fprintf(stderr, "jit compiled: %g (separate) us\n", jit_time_separate);
    double jit_time_array = fixture.array.estimate_cost_us(test_params, budget);
    fprintf(stderr, "jit compiled: %g (array) us\n", jit_time_array);
    double jit_time_lazy = fixture.lazy.estimate_cost_us(test_params, budget);
    fprintf(stderr, "jit compiled: %g (lazy) us\n", jit_time_lazy);
    double separate_vs_array_speed = (1.0/jit_time_separate)/(1.0/jit_time_array);
    double array_vs_lazy_speed = (1.0/jit_time_array)/(1.0/jit_time_lazy);
    fprintf(stderr, "seperate params speed compared to array params: %g\n", separate_vs_array_speed);
    fprintf(stderr, "array params speed compared to lazy params: %g\n", array_vs_lazy_speed);
}

TEST("measure big function eval/jit/gcc speed") {
    Fixture &fixture = big;
    CompiledFunction::expand<5>::type fun = big_gcc_function;

    EXPECT_EQUAL(fixture.separate.get_function<5>()(1,2,3,4,5), fun(1,2,3,4,5));
    EXPECT_EQUAL(fixture.separate.get_function<5>()(5,4,3,2,1), fun(5,4,3,2,1));

    double interpret_simple_time = fixture.interpreted_simple.estimate_cost_us(test_params, budget);
    fprintf(stderr, "interpret (simple): %g us\n", interpret_simple_time);
    double interpret_time = fixture.interpreted.estimate_cost_us(test_params, budget);
    fprintf(stderr, "interpret: %g us\n", interpret_time);
    double jit_time = estimate_cost_us(test_params, fixture.separate.get_function<5>());
    fprintf(stderr, "jit compiled: %g us\n", jit_time);
    double gcc_time = estimate_cost_us(test_params, fun);
    fprintf(stderr, "gcc compiled: %g us\n", gcc_time);
    double default_vs_simple_speed = (1.0/interpret_time)/(1.0/interpret_simple_time);
    double jit_vs_interpret_speed = (1.0/jit_time)/(1.0/interpret_time);
    double gcc_vs_jit_speed = (1.0/gcc_time)/(1.0/jit_time);
    fprintf(stderr, "default typed vs simple untyped interpret speed: %g\n", default_vs_simple_speed);
    fprintf(stderr, "jit speed compared to interpret: %g\n", jit_vs_interpret_speed);
    fprintf(stderr, "gcc speed compared to jit: %g\n", gcc_vs_jit_speed);

    double jit_time_separate = fixture.separate.estimate_cost_us(test_params, budget);
    fprintf(stderr, "jit compiled: %g (separate) us\n", jit_time_separate);
    double jit_time_array = fixture.array.estimate_cost_us(test_params, budget);
    fprintf(stderr, "jit compiled: %g (array) us\n", jit_time_array);
    double jit_time_lazy = fixture.lazy.estimate_cost_us(test_params, budget);
    fprintf(stderr, "jit compiled: %g (lazy) us\n", jit_time_lazy);
    double separate_vs_array_speed = (1.0/jit_time_separate)/(1.0/jit_time_array);
    double array_vs_lazy_speed = (1.0/jit_time_array)/(1.0/jit_time_lazy);
    fprintf(stderr, "seperate params speed compared to array params: %g\n", separate_vs_array_speed);
    fprintf(stderr, "array params speed compared to lazy params: %g\n", array_vs_lazy_speed);
}

TEST_MAIN() { TEST_RUN_ALL(); }
