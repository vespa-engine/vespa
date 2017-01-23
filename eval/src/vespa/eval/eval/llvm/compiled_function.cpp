// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "compiled_function.h"
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/eval/eval/node_traverser.h>
#include <vespa/eval/eval/check_type.h>
#include <vespa/eval/eval/tensor_nodes.h>
#include <vespa/vespalib/util/classname.h>

namespace vespalib {
namespace eval {

namespace {

double empty_function(const double *) { return 0.0; }

} // namespace vespalib::eval::<unnamed>

CompiledFunction::CompiledFunction(const Function &function_in, PassParams pass_params_in,
                                   const gbdt::Optimize::Chain &forest_optimizers)
    : _llvm_wrapper(),
      _address(nullptr),
      _num_params(function_in.num_params()),
      _pass_params(pass_params_in)
{
    _address = _llvm_wrapper.compile_function(function_in.num_params(),
                                              (_pass_params == PassParams::ARRAY),
                                              function_in.root(),
                                              forest_optimizers);
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
CompiledFunction::estimate_cost_us(const std::vector<double> &params) const
{
    assert(_pass_params == PassParams::ARRAY);
    assert(params.size() == _num_params);
    auto function = get_function();
    auto actual = [&](){function(&params[0]);};
    auto baseline = [&](){empty_function(&params[0]);};
    return BenchmarkTimer::benchmark(actual, baseline, 4.0) * 1000.0 * 1000.0;
}

Function::Issues
CompiledFunction::detect_issues(const Function &function)
{
    struct NotSupported : NodeTraverser {
        std::vector<vespalib::string> issues;
        bool open(const nodes::Node &) override { return true; }
        void close(const nodes::Node &node) override {
            if (nodes::check_type<nodes::TensorSum,
                                  nodes::TensorMap,
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

} // namespace vespalib::eval
} // namespace vespalib
