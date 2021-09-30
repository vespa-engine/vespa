// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "generic_join.h"

namespace vespalib::eval::instruction {

struct MergeParam {
    const ValueType res_type;
    const join_fun_t function;
    const size_t num_mapped_dimensions;
    const size_t dense_subspace_size;
    SmallVector<size_t> all_view_dims;
    const ValueBuilderFactory &factory;
    MergeParam(const ValueType &res_type_in,
               const ValueType &lhs_type, const ValueType &rhs_type,
               join_fun_t function_in, const ValueBuilderFactory &factory_in)
        : res_type(res_type_in),
          function(function_in),
          num_mapped_dimensions(lhs_type.count_mapped_dimensions()),
          dense_subspace_size(lhs_type.dense_subspace_size()),
          all_view_dims(num_mapped_dimensions),
          factory(factory_in)
    {
        assert(!res_type.is_error());
        assert(num_mapped_dimensions == rhs_type.count_mapped_dimensions());
        assert(num_mapped_dimensions == res_type.count_mapped_dimensions());
        assert(dense_subspace_size == rhs_type.dense_subspace_size());
        assert(dense_subspace_size == res_type.dense_subspace_size());
        for (size_t i = 0; i < num_mapped_dimensions; ++i) {
            all_view_dims[i] = i;
        }
    }
    ~MergeParam();
};

template <typename LCT, typename RCT, typename OCT, typename Fun>
std::unique_ptr<Value>
generic_mixed_merge(const Value &a, const Value &b,
                    const MergeParam &params);

struct GenericMerge {
    static InterpretedFunction::Instruction
    make_instruction(const ValueType &result_type,
                     const ValueType &lhs_type, const ValueType &rhs_type,
                     join_fun_t function,
                     const ValueBuilderFactory &factory, Stash &stash);
};

} // namespace
