// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/aggr.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/nested_loop.h>
#include <vespa/vespalib/util/small_vector.h>

namespace vespalib { class Stash; }
namespace vespalib::eval { struct ValueBuilderFactory; }

namespace vespalib::eval::instruction {

//-----------------------------------------------------------------------------

struct DenseReducePlan {
    size_t in_size;
    size_t out_size;
    SmallVector<size_t> loop_cnt;
    SmallVector<size_t> in_stride;
    SmallVector<size_t> out_stride;
    DenseReducePlan(const ValueType &type, const ValueType &res_type);
    ~DenseReducePlan();
    template <typename F> void execute(size_t in_idx, const F &f) const {
        run_nested_loop(in_idx, 0, loop_cnt, in_stride, out_stride, f);
    }
};

struct SparseReducePlan {
    size_t num_reduce_dims;
    SmallVector<size_t> keep_dims;
    bool should_forward_index() const;
    SparseReducePlan(const ValueType &type, const ValueType &res_type);
    ~SparseReducePlan();
};

//-----------------------------------------------------------------------------

struct GenericReduce {
    static InterpretedFunction::Instruction
    make_instruction(const ValueType &result_type,
                     const ValueType &input_type, Aggr aggr,
                     const std::vector<vespalib::string> &dimensions,
                     const ValueBuilderFactory &factory, Stash &stash);
};

//-----------------------------------------------------------------------------

} // namespace
