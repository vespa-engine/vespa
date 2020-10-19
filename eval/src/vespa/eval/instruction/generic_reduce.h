// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/aggr.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/nested_loop.h>

namespace vespalib { class Stash; }
namespace vespalib::eval { struct ValueBuilderFactory; }

namespace vespalib::eval::instruction {

//-----------------------------------------------------------------------------

struct DenseReducePlan {
    size_t in_size;
    size_t out_size;
    std::vector<size_t> keep_loop;
    std::vector<size_t> keep_stride;
    std::vector<size_t> reduce_loop;
    std::vector<size_t> reduce_stride;
    DenseReducePlan(const ValueType &type, const ValueType &res_type);
    ~DenseReducePlan();
    template <typename F> void execute_keep(const F &f) const {
        run_nested_loop(0, keep_loop, keep_stride, f);
    }
    template <typename F> void execute_reduce(size_t offset, const F &f) const {
        run_nested_loop(offset, reduce_loop, reduce_stride, f);
    }
    template <typename FIRST, typename NEXT> void execute_reduce(size_t offset, const FIRST &first, const NEXT &next) const {
        run_nested_loop(offset, reduce_loop, reduce_stride, first, next);
    }
};

struct SparseReducePlan {
    size_t num_reduce_dims;
    std::vector<size_t> keep_dims;
    SparseReducePlan(const ValueType &type, const ValueType &res_type);
    ~SparseReducePlan();
};

//-----------------------------------------------------------------------------

struct GenericReduce {
    static InterpretedFunction::Instruction
    make_instruction(const ValueType &type, Aggr aggr,
                     const std::vector<vespalib::string> &dimensions,
                     const ValueBuilderFactory &factory, Stash &stash);

    static Value::UP
    perform_reduce(const Value &a, Aggr aggr,
                   const std::vector<vespalib::string> &dimensions,
                   const ValueBuilderFactory &factory);
};

//-----------------------------------------------------------------------------

} // namespace
