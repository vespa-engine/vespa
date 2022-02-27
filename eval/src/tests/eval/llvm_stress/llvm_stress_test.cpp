// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/llvm/compiled_function.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/size_literals.h>
#include <random>
#include <thread>

using vespalib::make_string_short::fmt;
using namespace vespalib;
using namespace vespalib::eval;

//-----------------------------------------------------------------------------
// This test will stress llvm compilation by performing multiple
// compilations concurrently to try to uncover any potential races
// that could lead to memory overwrites. Run manually for best
// results.
// -----------------------------------------------------------------------------

duration budget = duration::zero();
size_t expr_size = 1;

//-----------------------------------------------------------------------------

struct Opts {
    char **arg;
    char **end;
    Opts(int argc, char **argv) : arg(argv), end(arg + argc) {
        if (arg != end) {
            ++arg; // skip self
        }
    }
    int get_int(const char *name, int fallback) {
        int value = fallback;
        if (arg != end) {
            value = atoi(*arg);
            fprintf(stderr, "%s: %d (<- '%s')\n", name, value, *arg);
            ++arg;
        } else {
            fprintf(stderr, "%s: %d (default)\n", name, value);
        }
        return value;
    }
};

struct Done {
    steady_time end_time;
    Done(duration how_long) : end_time() { reset(how_long); }
    void reset(duration how_long) { end_time = steady_clock::now() + how_long; }
    bool operator()() const { return steady_clock::now() >= end_time; }
};

struct Rnd {
    std::mt19937 gen;
    Rnd(size_t seed) : gen(seed) {}
    size_t get_int(size_t min, size_t max) {
        std::uniform_int_distribution<size_t> dist(min, max);
        return dist(gen);
    }
};

vespalib::string make_expr(size_t size, Rnd &rnd) {
    if (size < 2) {
        auto x = rnd.get_int(0, 99);
        if (x < 2) {
            return "0";
        } else if (x < 10) {
            return "0.75";
        } else if (x < 18) {
            return "1";
        } else if (x < 26) {
            return "1.25";
        } else {
            static vespalib::string params("abcdefghijk");
            auto idx = rnd.get_int(0, params.size() - 1);
            return params.substr(idx, 1);
        }
    } else {
        auto x = rnd.get_int(0, 99);
        if ((x < 80) || (size < 3)) {
            auto left = rnd.get_int(1, size - 1);
            auto right = size - left;
            if (x < 40) {
                return "(" + make_expr(left, rnd) + "+" + make_expr(right, rnd) + ")";
            } else {
                return "(" + make_expr(left, rnd) + "*" + make_expr(right, rnd) + ")";
            }
        } else {
            auto cond = rnd.get_int(1, size - 2);
            auto left = rnd.get_int(1, size - cond - 1);
            auto right = size - cond - left;
            return "if(" + make_expr(cond, rnd) + "," + make_expr(left, rnd) + "," + make_expr(right, rnd) + ")";
        }
    }
}

//-----------------------------------------------------------------------------

TEST_MT_F("stress test llvm compilation", 64, Done(budget)) {
    size_t my_seed = 5489u + (123 * thread_id);
    Rnd rnd(my_seed);
    const auto &done = f1;
    auto my_expr = make_expr(expr_size, rnd);
    if ((thread_id == 0) && (my_expr.size() < 128)) {
        fprintf(stderr, "example expression: %s\n", my_expr.c_str());
    }
    auto my_fun = Function::parse(my_expr);
    ASSERT_TRUE(!my_fun->has_error());
    while (!done()) {
        CompiledFunction arr_cf(*my_fun, PassParams::ARRAY);
        CompiledFunction lazy_cf(*my_fun, PassParams::LAZY);
        std::thread([my_fun]{ CompiledFunction my_arr_cf(*my_fun, PassParams::ARRAY); }).join();
        std::thread([my_fun]{ CompiledFunction my_lazy_cf(*my_fun, PassParams::LAZY); }).join();
    }
}

//-----------------------------------------------------------------------------

int main(int argc, char **argv) {
    TEST_MASTER.init(__FILE__);
    Opts opts(argc, argv);
    budget = 1s * opts.get_int("seconds to run", 1);
    expr_size = opts.get_int("expression size", 16);
    TEST_RUN_ALL();
    return (TEST_MASTER.fini() ? 0 : 1);
}
