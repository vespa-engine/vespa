// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/typed_cells.h>
#include <vespa/searchlib/tensor/distance_functions.h>
#include <vespa/searchlib/tensor/distance_function_factory.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP("distance_function_test");

using namespace search::tensor;
using vespalib::eval::Int8Float;
using vespalib::eval::TypedCells;
using vespalib::eval::CellType;
using vespalib::BenchmarkTimer;
using search::attribute::DistanceMetric;

template <typename T>
TypedCells t(const std::vector<T> &v) { return TypedCells(v); }

class ZeroDf : public DistanceFunction {
public:
    ZeroDf() : DistanceFunction(vespalib::eval::CellType::INT8) {}

    double calc(const vespalib::eval::TypedCells& , const vespalib::eval::TypedCells& ) const override
    { return 0.0; }
    double convert_threshold(double ) const override { return 0.0; }
    double to_rawscore(double ) const override { return 0.0; }
    double calc_with_limit(const vespalib::eval::TypedCells& ,
                           const vespalib::eval::TypedCells& ,
                           double ) const override
    { return 0.0; }
};

struct CheckDist {
    DistanceFunction &df;
    void operator()() const {
        static const std::vector<Int8Float> v1{ 1.0, -2.0, 3.0, -4.0, 5.0, -6.0, 7.0, -8.0};
        static const std::vector<Int8Float> v2{ 1.0, 3.0, 5.0, 9.0, -1.0, -3.0, -5.0, -9.0};
        double result = df.calc(t(v1), t(v2));
        (void) result;
    }
};

double estimate_cost_ns(DistanceFunction &df) {
    constexpr size_t LOOP_CNT = 10000;
    ZeroDf zeroDf;
    CheckDist nop(zeroDf);
    CheckDist actual(df);
    return BenchmarkTimer::benchmark(actual, nop, LOOP_CNT, 5.0) * 1000.0 * 1000.0 * 1000.0;
}

void benchmark(const char *desc, DistanceFunction &df) {
    double t = estimate_cost_ns(df);
    fprintf(stderr, "%s cost is %10.2f ns\n", desc, t);
}

TEST(DistanceFunctionsTest, euclidean_int8_benchmark)
{
    auto slow = std::make_unique<SquaredEuclideanDistance>(CellType::FLOAT);
    auto experimental = std::make_unique<SquaredEuclideanDistancePI8>();

    benchmark("[default]", *slow);
    benchmark("[SSE/MMX]", *experimental);
}

GTEST_MAIN_RUN_ALL_TESTS()

