// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/llvm/compiled_function.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/eval/eval/interpreted_function.h>

using namespace vespalib::eval;

std::vector<vespalib::string> params_5({"p", "o", "q", "f", "w"});

double sum_sum = 0.0;

const char *function_str = "(0.35*p + 0.15*o + 0.30*q + 0.20*f) * w";
Function function_ast = Function::parse(params_5, function_str);
InterpretedFunction interpreted_function(SimpleTensorEngine::ref(), function_ast, NodeTypes());
CompiledFunction compiled_function(function_ast, PassParams::SEPARATE);
auto jit_function = compiled_function.get_function<5>();

double gcc_function(double p, double o, double q, double f, double w) {
    return (0.35*p + 0.15*o + 0.30*q + 0.20*f) * w;
}

InterpretedFunction::Context icontext(interpreted_function);

double interpret_function(double p, double o, double q, double f, double w) {
    icontext.clear_params();
    icontext.add_param(p);
    icontext.add_param(o);
    icontext.add_param(q);
    icontext.add_param(f);
    icontext.add_param(w);
    return interpreted_function.eval(icontext).as_double();
}

//-----------------------------------------------------------------------------

const char *big_function_str = "(0.35*p + 0.15*o + 0.30*q + 0.20*f) * w + "
    "(0.35*p + 0.15*o + 0.30*q + 0.20*f) * w + "
    "(0.35*p + 0.15*o + 0.30*q + 0.20*f) * w + "
    "(0.35*p + 0.15*o + 0.30*q + 0.20*f) * w";

Function big_function_ast = Function::parse(params_5, big_function_str);
InterpretedFunction big_interpreted_function(SimpleTensorEngine::ref(), big_function_ast, NodeTypes());
CompiledFunction big_compiled_function(big_function_ast, PassParams::SEPARATE);
auto big_jit_function = big_compiled_function.get_function<5>();

double big_gcc_function(double p, double o, double q, double f, double w) {
    return (0.35*p + 0.15*o + 0.30*q + 0.20*f) * w +
        (0.35*p + 0.15*o + 0.30*q + 0.20*f) * w +
        (0.35*p + 0.15*o + 0.30*q + 0.20*f) * w +
        (0.35*p + 0.15*o + 0.30*q + 0.20*f) * w;
}

InterpretedFunction::Context big_icontext(big_interpreted_function);

double big_interpret_function(double p, double o, double q, double f, double w) {
    big_icontext.clear_params();
    big_icontext.add_param(p);
    big_icontext.add_param(o);
    big_icontext.add_param(q);
    big_icontext.add_param(f);
    big_icontext.add_param(w);
    return big_interpreted_function.eval(big_icontext).as_double();
}

//-----------------------------------------------------------------------------

double measure_best(CompiledFunction::expand<5>::type function) {
    double sum = 0.0;
    vespalib::BenchmarkTimer timer(1.0);
    while (timer.has_budget()) {
        timer.before();
        for (int p = 0; p < 10; ++p) {
            for (int o = 0; o < 10; ++o) {
                for (int q = 0; q < 10; ++q) {
                    for (int f = 0; f < 10; ++f) {
                        for (int w = 0; w < 10; ++w) {
                            sum += function(p, o, q, f, w);
                        }
                    }
                }
            }
        }
        timer.after();
    }
    return (timer.min_time() * 1000.0);
}

//-----------------------------------------------------------------------------

TEST("require that small functions return the same result") {
    EXPECT_EQUAL(interpret_function(1,2,3,4,5), jit_function(1,2,3,4,5));
    EXPECT_EQUAL(interpret_function(1,2,3,4,5), gcc_function(1,2,3,4,5));
    EXPECT_EQUAL(interpret_function(5,4,3,2,1), jit_function(5,4,3,2,1));
    EXPECT_EQUAL(interpret_function(5,4,3,2,1), gcc_function(5,4,3,2,1));
}

TEST("require that big functions return the same result") {
    EXPECT_EQUAL(big_interpret_function(1,2,3,4,5), big_jit_function(1,2,3,4,5));
    EXPECT_EQUAL(big_interpret_function(1,2,3,4,5), big_gcc_function(1,2,3,4,5));
    EXPECT_EQUAL(big_interpret_function(5,4,3,2,1), big_jit_function(5,4,3,2,1));
    EXPECT_EQUAL(big_interpret_function(5,4,3,2,1), big_gcc_function(5,4,3,2,1));
}

TEST("measure small function eval/jit/gcc speed") {
    double interpret_time = measure_best(interpret_function);
    double jit_time = measure_best(jit_function);
    double gcc_time = measure_best(gcc_function);
    double jit_vs_interpret_speed = (1.0/jit_time)/(1.0/interpret_time);
    double gcc_vs_jit_speed = (1.0/gcc_time)/(1.0/jit_time);
    fprintf(stderr, "interpret: %g ms\n", interpret_time);
    fprintf(stderr, "jit compiled: %g ms\n", jit_time);
    fprintf(stderr, "gcc compiled: %g ms\n", gcc_time);
    fprintf(stderr, "jit speed compared to interpret: %g\n", jit_vs_interpret_speed);
    fprintf(stderr, "gcc speed compared to jit: %g\n", gcc_vs_jit_speed);
}

TEST("measure big function eval/jit/gcc speed") {
    double interpret_time = measure_best(big_interpret_function);
    double jit_time = measure_best(big_jit_function);
    double gcc_time = measure_best(big_gcc_function);
    double jit_vs_interpret_speed = (1.0/jit_time)/(1.0/interpret_time);
    double gcc_vs_jit_speed = (1.0/gcc_time)/(1.0/jit_time);
    fprintf(stderr, "interpret: %g ms\n", interpret_time);
    fprintf(stderr, "jit compiled: %g ms\n", jit_time);
    fprintf(stderr, "gcc compiled: %g ms\n", gcc_time);
    fprintf(stderr, "jit speed compared to interpret: %g\n", jit_vs_interpret_speed);
    fprintf(stderr, "gcc speed compared to jit: %g\n", gcc_vs_jit_speed);
}

TEST_MAIN() { TEST_RUN_ALL(); }
