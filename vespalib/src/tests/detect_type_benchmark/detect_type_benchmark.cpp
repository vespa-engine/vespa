// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <typeindex>

// Typically when you want a flexible way of identifying whether you
// are encountering a specific subclass, you try to dynamic_cast the
// pointer and check for a non-null return value. This is the most
// flexible way since it requires no extra code in the class itself
// and you will also detect any subclasses of the subclass you are
// testing for. Sometimes you only need to identify the exact class
// and speed in doing so is all that matters. This benchmark tries to
// isolate and measure the cost of different strategies. Note that
// dynamic_cast may be more expensive for more complex class
// hierarchies.

using vespalib::BenchmarkTimer;

constexpr int A_ID = 1;
constexpr int B_ID = 2;

constexpr size_t LOOP_CNT = 1000000;

class BaseClass {
private:
    int _static_id;
public:
    BaseClass(int id) : _static_id(id) {}
    int static_id() const { return _static_id; }
    virtual int dynamic_id() const = 0;
    virtual ~BaseClass() {}
};

struct A : BaseClass {
    A() : BaseClass(A_ID) {}
    int dynamic_id() const override { return A_ID; }    
};

struct B : BaseClass {
    B() : BaseClass(B_ID) {}
    int dynamic_id() const override { return B_ID; }    
};

using is_A = bool (*)(const BaseClass *);

//-----------------------------------------------------------------------------

struct CheckType {
    BaseClass *ptr;
    is_A pred;
    CheckType(BaseClass *ptr_in, is_A pred_in) : ptr(ptr_in), pred(pred_in) {}
    void operator()() const {
        bool result = pred(ptr);
        (void) result;
    }
};

struct Nop {
    void operator()() const noexcept {}
};

//-----------------------------------------------------------------------------

A a;
B b;
Nop nop;
double baseline = 0.0;

//-----------------------------------------------------------------------------

bool always_true(const BaseClass *) __attribute__((noinline));
bool always_true(const BaseClass *) {
    return true;
}

bool always_false(const BaseClass *) __attribute__((noinline));
bool always_false(const BaseClass *) {
    return false;
}

//-----------------------------------------------------------------------------

bool use_dynamic_cast(const BaseClass *) __attribute__((noinline));
bool use_dynamic_cast(const BaseClass *ptr) {
    return (dynamic_cast<const A*>(ptr));
}

bool use_type_index(const BaseClass *) __attribute__((noinline));
bool use_type_index(const BaseClass *ptr) {
    return (std::type_index(typeid(*ptr)) == std::type_index(typeid(A)));
}

bool use_type_id(const BaseClass *) __attribute__((noinline));
bool use_type_id(const BaseClass *ptr) {
    return (typeid(*ptr) == typeid(A));
}

bool use_dynamic_id(const BaseClass *) __attribute__((noinline));
bool use_dynamic_id(const BaseClass *ptr) {
    return (ptr->dynamic_id() == A_ID);
}

bool use_static_id(const BaseClass *) __attribute__((noinline));
bool use_static_id(const BaseClass *ptr) {
    return (ptr->static_id() == A_ID);
}

//-----------------------------------------------------------------------------

double estimate_cost_ns(CheckType check) {
    return BenchmarkTimer::benchmark(check, nop, LOOP_CNT, 5.0) * 1000.0 * 1000.0 * 1000.0;
}

void benchmark(const char *desc, is_A pred) {
    EXPECT_TRUE(pred(&a)) << desc;
    EXPECT_FALSE(pred(&b)) << desc;
    CheckType yes(&a, pred);
    CheckType no(&b, pred);
    double t1 = estimate_cost_ns(yes);
    double t2 = estimate_cost_ns(no);
    double my_cost = ((t1 + t2) / 2.0) - baseline;
    fprintf(stderr, "%s cost is %5.2f ns (true %5.2f, false %5.2f, baseline %5.2f)\n",
            desc, my_cost, t1, t2, baseline);
}

//-----------------------------------------------------------------------------

TEST(DetectTypeBenchmark, find_baseline) {
    CheckType check_true(&a, always_true);
    CheckType check_false(&b, always_false);
    double t1 = estimate_cost_ns(check_true);
    double t2 = estimate_cost_ns(check_false);
    baseline = (t1 + t2) / 2.0;
    fprintf(stderr, "baseline cost is %5.2f ns (true %5.2f, false %5.2f)\n",
            baseline, t1, t2);
}

TEST(DetectTypeBenchmark, measure_overhead) {    
    benchmark("[dynamic_cast]", use_dynamic_cast);
    benchmark("  [type_index]", use_type_index);
    benchmark("      [typeid]", use_type_id);
    benchmark("  [dynamic id]", use_dynamic_id);
    benchmark("   [static id]", use_static_id);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
