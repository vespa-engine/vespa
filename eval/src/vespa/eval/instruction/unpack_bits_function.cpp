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

void my_unpack_bits_op(InterpretedFunction::State &state, uint64_t param) {
    const ValueType &res_type = unwrap_param<ValueType>(param);
    auto packed_cells = state.peek(0).cells().typify<Int8Float>();
    auto unpacked_cells = state.stash.create_uninitialized_array<Int8Float>(packed_cells.size() * 8);
    int8_t *dst = reinterpret_cast<int8_t*>(unpacked_cells.begin());
    for (Int8Float cell: packed_cells) {
        for (int n = 7; n >= 0; --n) {
            *dst++ = bool(cell.get_bits() & (1 << n));
        }
    }
    Value &result_ref = state.stash.create<DenseValueView>(res_type, TypedCells(unpacked_cells));
    state.pop_push(result_ref);
}

bool valid_lambda_params(const Lambda &lambda) {
    return ((lambda.lambda().num_params() == 2) &&
            (lambda.bindings().size() == 1));
}

bool valid_type(const ValueType &type) {
    return ((type.is_dense()) &&
            (type.dimensions().size() == 1) &&
            (type.cell_type() == CellType::INT8));
}

bool compatible_types(const ValueType &packed, const ValueType &unpacked) {
    return (valid_type(packed) && valid_type(unpacked) &&
            (unpacked.dimensions()[0].size == (packed.dimensions()[0].size * 8)));
}

bool is_bit_expr(const Node &node) {
    // '7-(x%8)'
    if (auto sub = as<Sub>(node)) {
        if (auto seven = as<Number>(sub->lhs())) {
            if (auto mod = as<Mod>(sub->rhs())) {
                if (auto param = as<Symbol>(mod->lhs())) {
                    if (auto eight = as<Number>(mod->rhs())) {
                        return ((seven->value() == 7.0) &&
                                (eight->value() == 8.0) &&
                                (param->id() == 0));
                    }
                }
            }
        }
    }
    return false;
}

bool is_byte_expr(const Node &node) {
    // 'x/8'
    if (auto div = as<Div>(node)) {
        if (auto param = as<Symbol>(div->lhs())) {
            if (auto eight = as<Number>(div->rhs())) {
                return ((eight->value() == 8.0) &&
                        (param->id() == 0));
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

} // namespace <unnamed>

UnpackBitsFunction::UnpackBitsFunction(const ValueType &res_type_in,
                                       const TensorFunction &packed)
  : Op1(res_type_in, packed)
{
}

InterpretedFunction::Instruction
UnpackBitsFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    return InterpretedFunction::Instruction(my_unpack_bits_op, wrap_param<ValueType>(result_type()));
}

const TensorFunction &
UnpackBitsFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto lambda = as<Lambda>(expr)) {
        // 'tensor<int8>(x[64])(bit(packed{x:(x/8)},7-(x%8)))'
        const ValueType &dst_type = lambda->result_type();
        if (auto bit = as<Bit>(lambda->lambda().root())) {
            if (auto peek = as<TensorPeek>(bit->get_child(0))) {
                const ValueType &src_type = lambda->types().get_type(peek->param());
                if (valid_lambda_params(*lambda) &&
                    compatible_types(src_type, dst_type) &&
                    is_bit_expr(bit->get_child(1)) &&
                    is_byte_peek(*peek))
                {
                    size_t param_idx = lambda->bindings()[0];
                    const auto &packed_param = inject(src_type, param_idx, stash);
                    return stash.create<UnpackBitsFunction>(dst_type, packed_param);
                }
            }
        }
    }
    return expr;
}

} // namespace
