// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/nested_loop.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace vespalib::eval { struct ValueBuilderFactory; }

namespace vespalib::eval::instruction {

struct GenericConcat {
    static InterpretedFunction::Instruction
    make_instruction(const ValueType &lhs_type, const ValueType &rhs_type,
                     const vespalib::string &dimension,
                     const ValueBuilderFactory &factory, Stash &stash);

    static Value::UP
    perform_concat(const Value &a, const Value &b,
                   const vespalib::string &dimension,
                   const ValueBuilderFactory &factory);
};

struct DenseConcatPlan {
    size_t right_offset;
    size_t output_size;
    struct InOutLoop {
        size_t input_size;
        std::vector<size_t> in_loop_cnt;
        std::vector<size_t> in_stride;
        std::vector<size_t> out_stride;
        // returns computed concat offset and output size
        std::pair<size_t, size_t> fill_from(const ValueType &in_type,
                                            std::string concat_dimension,
                                            const ValueType &out_type);
        template <typename F> void execute(size_t in_off, size_t out_off, const F &f) const {
            run_nested_loop(in_off, out_off, in_loop_cnt, in_stride, out_stride, f);
        }
        ~InOutLoop();
    };
    InOutLoop left;
    InOutLoop right;
    DenseConcatPlan(const ValueType &lhs_type, const ValueType &rhs_type, std::string concat_dimension, const ValueType &res_type);
    ~DenseConcatPlan();
};

} // namespace
