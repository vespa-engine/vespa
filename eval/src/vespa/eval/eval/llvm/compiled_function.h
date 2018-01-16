// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/lazy_params.h>
#include <vespa/eval/eval/gbdt.h>
#include "llvm_wrapper.h"

namespace vespalib {
namespace eval {

/**
 * A Function that has been compiled to machine code using LLVM. Note
 * that tensors are currently not supported for compiled functions.
 **/
class CompiledFunction
{
public:
    // expand<N>::type will resolve to the type of a function that
    // takes N separate double parameters and returns double.

    // count down N and add a single double parameter to the list of parameters
    template <size_t N, typename... T> struct expand : expand<N - 1, double, T...> {};
    // when N is 0; define 'type' with the list of collected parameters
    template <typename... T> struct expand<0, T...> { using type = double(*)(T...); };

    using array_function = double (*)(const double *);

    using resolve_function = LazyParams::resolve_function;
    using lazy_function = double (*)(resolve_function, void *ctx);

private:
    LLVMWrapper _llvm_wrapper;
    void       *_address;
    size_t      _num_params;
    PassParams  _pass_params;

public:
    typedef std::unique_ptr<CompiledFunction> UP;
    CompiledFunction(const Function &function_in, PassParams pass_params_in,
                     const gbdt::Optimize::Chain &forest_optimizers);
    CompiledFunction(const Function &function_in, PassParams pass_params_in)
        : CompiledFunction(function_in, pass_params_in, gbdt::Optimize::best) {}
    CompiledFunction(CompiledFunction &&rhs);
    size_t num_params() const { return _num_params; }
    PassParams pass_params() const { return _pass_params; }
    template <size_t NUM_PARAMS>
    typename expand<NUM_PARAMS>::type get_function() const {
        assert(_pass_params == PassParams::SEPARATE);
        assert(_num_params == NUM_PARAMS);
        return ((typename expand<NUM_PARAMS>::type)_address);
    }
    array_function get_function() const {
        assert(_pass_params == PassParams::ARRAY);
        return ((array_function)_address);
    }
    lazy_function get_lazy_function() const {
        assert(_pass_params == PassParams::LAZY);
        return ((lazy_function)_address);
    }
    const std::vector<gbdt::Forest::UP> &get_forests() const {
        return _llvm_wrapper.get_forests();
    }
    double estimate_cost_us(const std::vector<double> &params, double budget = 5.0) const;
    static Function::Issues detect_issues(const Function &function);
    static bool should_use_lazy_params(const Function &function);
};

} // namespace vespalib::eval
} // namespace vespalib
