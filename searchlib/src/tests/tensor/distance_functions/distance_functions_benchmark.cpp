// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/typed_cells.h>
#include <vespa/searchlib/common/geo_gcd.h>
#include <vespa/searchlib/tensor/distance_functions.h>
#include <vespa/searchlib/tensor/distance_function_factory.h>
#include <vespa/searchlib/tensor/mips_distance_transform.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/classname.h>

using namespace search::tensor;
using vespalib::eval::Int8Float;
using vespalib::BFloat16;
using vespalib::eval::TypedCells;
using search::attribute::DistanceMetric;

size_t npos = std::string::npos;

double run_calc(size_t iterations, TypedCells b, const BoundDistanceFunction & df) __attribute_noinline__;
double run_calc_with_limit(size_t iterations, TypedCells b, const BoundDistanceFunction & df) __attribute_noinline__;

double
run_calc(size_t iterations, TypedCells b, const BoundDistanceFunction & df) {
    vespalib::BenchmarkTimer timer(1.0);
    double min_result = std::numeric_limits<double>::max();
    while (timer.has_budget()) {
        timer.before();
        for (size_t i(0); i < iterations; i++) {
            min_result = std::min(df.calc(b), min_result);
        }
        timer.after();
    }
    printf("%s::calc: Time used = %1.3f, min_result=%3.3f\n",
           vespalib::getClassName(df).c_str(), timer.min_time(), min_result);
    return min_result;
}

double
run_calc_with_limit(size_t iterations, TypedCells b, const BoundDistanceFunction & df) {
    vespalib::BenchmarkTimer timer(1.0);
    double min_result = std::numeric_limits<double>::max();
    while (timer.has_budget()) {
        timer.before();
        for (size_t i(0); i < iterations; i++) {
            min_result = std::min(df.calc_with_limit(b, std::numeric_limits<double>::max()), min_result);
        }
        timer.after();
    }

    printf("%s::calc_with_limit: Time used = %1.3f, min_result=%3.3f\n",
           vespalib::getClassName(df).c_str(), timer.min_time(), min_result);
    return min_result;
}

template<typename T>
void benchmark(size_t iterations, size_t elems) __attribute_noinline__;

template<typename T>
void benchmark(size_t iterations, size_t elems, const DistanceFunctionFactory & df) {
    std::vector<T> av, bv;
    srandom(7);
    av.reserve(elems);
    bv.reserve(elems);
    for (size_t i(0); i < elems; i++) {
        av.push_back(random()%128);
        bv.push_back(random()%128);
    }
    TypedCells a_cells(av), b_cells(bv);

    double calc_result = run_calc(iterations, b_cells, *df.for_query_vector(a_cells));
    double calc_with_limit_result = run_calc_with_limit(iterations, b_cells, *df.for_query_vector(a_cells));
    assert(calc_result == calc_with_limit_result);
}

template<typename T>
void benchmark(size_t iterations, size_t elems, const std::string & dist_functions) {
    if (dist_functions.find("euclid") != npos) {
        benchmark<T>(iterations, elems, EuclideanDistanceFunctionFactory<T>());
    }
    if (dist_functions.find("angular") != npos) {
        if constexpr ( ! std::is_same<T, BFloat16>()) {
            benchmark<T>(iterations, elems, AngularDistanceFunctionFactory<T>());
        }
    }
    if (dist_functions.find("prenorm") != npos) {
        if constexpr ( ! std::is_same<T, BFloat16>()) {
            benchmark<T>(iterations, elems, PrenormalizedAngularDistanceFunctionFactory<T>());
        }
    }
    if (dist_functions.find("mips") != npos) {
        if constexpr ( !std::is_same<T, BFloat16>()) {
            benchmark<T>(iterations, elems, MipsDistanceFunctionFactory<T>());
        }
    }
}

void
benchmark(size_t iterations, size_t elems, const std::string & dist_functions, const std::string & data_types) {
    if (data_types.find("double") != npos) {
        benchmark<double>(iterations, elems, dist_functions);
    }
    if (data_types.find("float32") != npos) {
        benchmark<float>(iterations, elems, dist_functions);
    }
    if (data_types.find("bfloat16") != npos) {
        benchmark<BFloat16>(iterations, elems, dist_functions);
    }
    if (data_types.find("float8") != npos) {
        benchmark<Int8Float>(iterations, elems, dist_functions);
    }
}

int
main(int argc, char *argv[]) {
    size_t num_iterations = 10000000;
    size_t num_elems = 1024;
    std::string dist_functions = "angular euclid prenorm mips";
    std::string data_types = "double float32 bfloat16 float8";
    if (argc > 1) { num_iterations = atol(argv[1]); }
    if (argc > 2) { num_elems = atol(argv[2]); }
    if (argc > 3) { dist_functions = argv[3]; }
    if (argc > 4) { data_types = argv[4]; }

    printf("Benchmarking %ld iterations with vector length %ld with distance functions '%s' for data types '%s'\n",
           num_iterations, num_elems, dist_functions.c_str(), data_types.c_str());
    benchmark(num_iterations, num_elems, dist_functions, data_types);

    return 0;
}
