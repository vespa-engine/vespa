// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compiled_function.h"
#include <vespa/eval/eval/param_usage.h>
#include <vespa/eval/eval/gbdt.h>
#include <vespa/eval/eval/node_traverser.h>
#include <vespa/eval/eval/check_type.h>
#include <vespa/eval/eval/tensor_nodes.h>
#include <vespa/vespalib/util/classname.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/approx.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.eval.llvm.compiled_function");

namespace vespalib {
namespace eval {

namespace {

double empty_function_0() { return 0.0; }
double empty_function_1(double) { return 0.0; }
double empty_function_2(double, double) { return 0.0; }
double empty_function_3(double, double, double) { return 0.0; }
double empty_function_4(double, double, double, double) { return 0.0; }
double empty_function_5(double, double, double, double, double) { return 0.0; }
double empty_array_function(const double *) { return 0.0; }
double empty_lazy_function(CompiledFunction::resolve_function, void *) { return 0.0; }

double my_resolve(void *ctx, size_t idx) { return ((double *)ctx)[idx]; }

} // namespace vespalib::eval::<unnamed>

CompiledFunction::CompiledFunction(const Function &function_in, PassParams pass_params_in,
                                   const gbdt::Optimize::Chain &forest_optimizers)
    : _llvm_wrapper(),
      _address(nullptr),
      _num_params(function_in.num_params()),
      _pass_params(pass_params_in)
{
    size_t id = _llvm_wrapper.make_function(function_in.num_params(),
                                            _pass_params,
                                            function_in.root(),
                                            forest_optimizers);
    _llvm_wrapper.compile();
    _address = _llvm_wrapper.get_function_address(id);
}

CompiledFunction::CompiledFunction(CompiledFunction &&rhs)
    : _llvm_wrapper(std::move(rhs._llvm_wrapper)),
      _address(rhs._address),
      _num_params(rhs._num_params),
      _pass_params(rhs._pass_params)
{
    rhs._address = nullptr;
}

double
CompiledFunction::estimate_cost_us(const std::vector<double> &params, double budget) const
{
    assert(params.size() == _num_params);
    if (_pass_params == PassParams::ARRAY) {
        auto function = get_function();
        auto empty = empty_array_function;
        auto actual = [&](){function(&params[0]);};
        auto baseline = [&](){empty(&params[0]);};
        return BenchmarkTimer::benchmark(actual, baseline, budget) * 1000.0 * 1000.0;
    }
    if (_pass_params == PassParams::LAZY) {
        auto function = get_lazy_function();
        auto empty = empty_lazy_function;
        auto actual = [&](){function(my_resolve, const_cast<double*>(&params[0]));};
        auto baseline = [&](){empty(my_resolve, const_cast<double*>(&params[0]));};
        return BenchmarkTimer::benchmark(actual, baseline, budget) * 1000.0 * 1000.0;
    }
    assert(_pass_params == PassParams::SEPARATE);
    if (params.size() == 0) {
        auto function = get_function<0>();
        auto empty = empty_function_0;
        auto actual = [&](){function();};
        auto baseline = [&](){empty();};
        return BenchmarkTimer::benchmark(actual, baseline, budget) * 1000.0 * 1000.0;
    }
    if (params.size() == 1) {
        auto function = get_function<1>();
        auto empty = empty_function_1;
        auto actual = [&](){function(params[0]);};
        auto baseline = [&](){empty(params[0]);};
        return BenchmarkTimer::benchmark(actual, baseline, budget) * 1000.0 * 1000.0;
    }
    if (params.size() == 2) {
        auto function = get_function<2>();
        auto empty = empty_function_2;        
        auto actual = [&](){function(params[0], params[1]);};
        auto baseline = [&](){empty(params[0], params[1]);};
        return BenchmarkTimer::benchmark(actual, baseline, budget) * 1000.0 * 1000.0;
    }
    if (params.size() == 3) {
        auto function = get_function<3>();
        auto empty = empty_function_3;
        auto actual = [&](){function(params[0], params[1], params[2]);};
        auto baseline = [&](){empty(params[0], params[1], params[2]);};
        return BenchmarkTimer::benchmark(actual, baseline, budget) * 1000.0 * 1000.0;
    }
    if (params.size() == 4) {
        auto function = get_function<4>();
        auto empty = empty_function_4;
        auto actual = [&](){function(params[0], params[1], params[2], params[3]);};
        auto baseline = [&](){empty(params[0], params[1], params[2], params[3]);};
        return BenchmarkTimer::benchmark(actual, baseline, budget) * 1000.0 * 1000.0;
    }
    if (params.size() == 5) {
        auto function = get_function<5>();
        auto empty = empty_function_5;
        auto actual = [&](){function(params[0], params[1], params[2], params[3], params[4]);};
        auto baseline = [&](){empty(params[0], params[1], params[2], params[3], params[4]);};
        return BenchmarkTimer::benchmark(actual, baseline, budget) * 1000.0 * 1000.0;
    }
    LOG_ABORT("should not be reached");
}

Function::Issues
CompiledFunction::detect_issues(const Function &function)
{
    struct NotSupported : NodeTraverser {
        std::vector<vespalib::string> issues;
        bool open(const nodes::Node &) override { return true; }
        void close(const nodes::Node &node) override {
            if (nodes::check_type<nodes::TensorMap,
                                  nodes::TensorJoin,
                                  nodes::TensorReduce,
                                  nodes::TensorRename,
                                  nodes::TensorLambda,
                                  nodes::TensorConcat>(node)) {
                issues.push_back(make_string("unsupported node type: %s",
                                getClassName(node).c_str()));
            }
        }
    } checker;
    function.root().traverse(checker);
    return Function::Issues(std::move(checker.issues));
}

bool
CompiledFunction::should_use_lazy_params(const Function &function)
{
    if (gbdt::contains_gbdt(function.root(), 16)) {
        return false; // contains gbdt
    }
    auto usage = vespalib::eval::check_param_usage(function);
    for (double p_use: usage) {
        if (!approx_equal(p_use, 1.0)) {
            return true; // param not always used
        }
    }
    return false; // all params always used
}

} // namespace vespalib::eval
} // namespace vespalib
