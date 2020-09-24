// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace vespalib::eval { class ValueBuilderFactory; }

namespace vespalib::eval::instruction {
 
struct DenseRenamePlan {
    std::vector<size_t> loop_cnt;
    std::vector<size_t> stride;
    const size_t subspace_size;
    DenseRenamePlan(const ValueType &lhs_type,
                    const ValueType &output_type,
                    const std::vector<vespalib::string> &from,
                    const std::vector<vespalib::string> &to);
    ~DenseRenamePlan();
    template <typename F> void execute(size_t offset, F &&f) const {
        switch(loops_left(0)) {
        case 0: return execute_few<F, 0>(0, offset, std::forward<F>(f));
        case 1: return execute_few<F, 1>(0, offset, std::forward<F>(f));
        case 2: return execute_few<F, 2>(0, offset, std::forward<F>(f));
        case 3: return execute_few<F, 3>(0, offset, std::forward<F>(f));
        default: return execute_many<F>(0, offset, std::forward<F>(f));
        }
    }
private:
    size_t loops_left(size_t idx) const { return (loop_cnt.size() - idx); }

    template <typename F, size_t N> void execute_few(size_t idx, size_t offset, F &&f) const {
        if constexpr (N == 0) {
            f(offset);
        } else {
            for (size_t i = 0; i < loop_cnt[idx]; ++i) {
                execute_few<F, N - 1>(idx + 1, offset, std::forward<F>(f));
                offset += stride[idx];
            }
        }
    }
    template <typename F> void execute_many(size_t idx, size_t offset, F &&f) const {
        for (size_t i = 0; i < loop_cnt[idx]; ++i) {
            if (loops_left(idx + 1) == 3) {
                execute_few<F, 3>(idx + 1, offset, std::forward<F>(f));
            } else {
                execute_many<F>(idx + 1, offset, std::forward<F>(f));
            }
            offset += stride[idx];
        }
    }
};

struct SparseRenamePlan {
    size_t mapped_dims;
    std::vector<size_t> output_dimensions;
    SparseRenamePlan(const ValueType &input_type,
                     const ValueType &output_type,
                     const std::vector<vespalib::string> &from,
                     const std::vector<vespalib::string> &to);
    ~SparseRenamePlan();
};

//-----------------------------------------------------------------------------

struct GenericRename {
    static InterpretedFunction::Instruction
    make_instruction(const ValueType &lhs_type, const ValueType &output_type,
                     const std::vector<vespalib::string> &rename_dimension_from,
                     const std::vector<vespalib::string> &rename_dimension_to,
                     const ValueBuilderFactory &factory, Stash &stash);
};

} // namespace
