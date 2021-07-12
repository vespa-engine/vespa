// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "unpack_bits_function.h"
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/node_tools.h>
#include <vespa/eval/eval/basic_nodes.h>
#include <vespa/eval/eval/operator_nodes.h>
#include <vespa/eval/eval/call_nodes.h>
#include <vespa/eval/eval/tensor_nodes.h>
#include <vespa/eval/eval/wrap_param.h>

namespace vespalib::eval {

using namespace vespalib::eval::nodes;
using tensor_function::Lambda;
using tensor_function::wrap_param;
using tensor_function::unwrap_param;
using tensor_function::inject;

namespace {

//-----------------------------------------------------------------------------

template <typename OCT, bool big>
void my_unpack_bits_op(InterpretedFunction::State &state, uint64_t param) {
    const ValueType &res_type = unwrap_param<ValueType>(param);
    auto packed_cells = state.peek(0).cells().typify<Int8Float>();
    auto unpacked_cells = state.stash.create_uninitialized_array<OCT>(packed_cells.size() * 8);
    OCT *dst = unpacked_cells.begin();
    for (Int8Float cell: packed_cells) {
        if constexpr (big) {
            for (int n = 7; n >= 0; --n) {
                *dst++ = (OCT) bool(cell.get_bits() & (1 << n));
            }
        } else {
            for (int n = 0; n <= 7; ++n) {
                *dst++ = (OCT) bool(cell.get_bits() & (1 << n));
            }
        }
    }
    Value &result_ref = state.stash.create<DenseValueView>(res_type, TypedCells(unpacked_cells));
    state.pop_push(result_ref);
}

//-----------------------------------------------------------------------------

struct MyGetFun {
    template <typename OCT, typename BIG> static auto invoke() {
        return my_unpack_bits_op<OCT, BIG::value>;
    }
};

using MyTypify = TypifyValue<TypifyCellType,TypifyBool>;

//-----------------------------------------------------------------------------

bool valid_lambda_params(const Lambda &lambda) {
    return ((lambda.lambda().num_params() == 2) &&
            (lambda.bindings().size() == 1));
}

bool valid_type(const ValueType &type, bool must_be_int8) {
    return ((type.is_dense()) &&
            (type.dimensions().size() == 1) &&
            (!must_be_int8 || (type.cell_type() == CellType::INT8)));
}

bool compatible_types(const ValueType &packed, const ValueType &unpacked) {
    return (valid_type(packed, true) && valid_type(unpacked, false) &&
            (unpacked.dimensions()[0].size == (packed.dimensions()[0].size * 8)));
}

bool is_little_bit_expr(const Node &node) {
    // 'x%8'
    if (auto mod = as<Mod>(node)) {
        if (auto param = as<Symbol>(mod->lhs())) {
            if (auto eight = as<Number>(mod->rhs())) {
                return ((param->id() == 0) && (eight->value() == 8.0));
            }
        }
    }
    return false;
}

bool is_big_bit_expr(const Node &node) {
    // '7-(x%8)'
    if (auto sub = as<Sub>(node)) {
        if (auto seven = as<Number>(sub->lhs())) {
            return ((seven->value() == 7.0) && is_little_bit_expr(sub->rhs()));
        }
    }
    return false;
}

bool is_byte_expr(const Node &node) {
    // 'x/8'
    if (auto div = as<Div>(node)) {
        if (auto param = as<Symbol>(div->lhs())) {
            if (auto eight = as<Number>(div->rhs())) {
                return ((param->id() == 0) && (eight->value() == 8.0));
            }
        }
    }
    return false;
}

bool is_byte_peek(const TensorPeek &peek) {
    if (auto param = as<Symbol>(peek.param())) {
        if ((param->id() == 1) &&
            (peek.dim_list().size() == 1) &&
            (peek.num_children() == 2))
        {
            return is_byte_expr(peek.get_child(1));
        }
    }
    return false;
}

//-----------------------------------------------------------------------------

} // namespace <unnamed>

UnpackBitsFunction::UnpackBitsFunction(const ValueType &res_type_in,
                                       const TensorFunction &packed,
                                       bool big_bitorder)
  : Op1(res_type_in, packed),
    _big_bitorder(big_bitorder)
{
}

InterpretedFunction::Instruction
UnpackBitsFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    const ValueType &res_type = result_type();
    auto op = typify_invoke<2,MyTypify,MyGetFun>(res_type.cell_type(), _big_bitorder);
    return InterpretedFunction::Instruction(op, wrap_param<ValueType>(res_type));
}

const TensorFunction &
UnpackBitsFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto lambda = as<Lambda>(expr)) {
        const ValueType &dst_type = lambda->result_type();
        if (auto bit = as<Bit>(lambda->lambda().root())) {
            if (auto peek = as<TensorPeek>(bit->get_child(0))) {
                const ValueType &src_type = lambda->types().get_type(peek->param());
                if (compatible_types(src_type, dst_type) &&
                    valid_lambda_params(*lambda) &&
                    is_byte_peek(*peek))
                {
                    size_t param_idx = lambda->bindings()[0];
                    if (is_big_bit_expr(bit->get_child(1))) {
                        return stash.create<UnpackBitsFunction>(dst_type, inject(src_type, param_idx, stash), true);
                    } else if (is_little_bit_expr(bit->get_child(1))) {
                        return stash.create<UnpackBitsFunction>(dst_type, inject(src_type, param_idx, stash), false);
                    }
                }
            }
        }
    }
    return expr;
}

} // namespace
