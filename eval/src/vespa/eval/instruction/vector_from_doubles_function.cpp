// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "vector_from_doubles_function.h"
#include <vespa/eval/eval/value.h>

namespace vespalib::eval {

using Child = TensorFunction::Child;
using namespace tensor_function;

namespace {

struct CallVectorFromDoubles {
    template <typename CT>
    static TypedCells
    invoke(InterpretedFunction::State &state, size_t numCells) {
        ArrayRef<CT> outputCells = state.stash.create_uninitialized_array<CT>(numCells);
        for (size_t i = numCells; i-- > 0; ) {
            outputCells[i] = (CT) state.peek(0).as_double();
            state.stack.pop_back();
        }
        return TypedCells(outputCells);
    }
};

void my_vector_from_doubles_op(InterpretedFunction::State &state, uint64_t param) {
    const auto &self = unwrap_param<VectorFromDoublesFunction::Self>(param);
    CellType ct = self.resultType.cell_type();
    size_t numCells = self.resultSize;
    using MyTypify = TypifyCellType;
    TypedCells cells = typify_invoke<1,MyTypify,CallVectorFromDoubles>(ct, state, numCells);
    const Value &result = state.stash.create<DenseValueView>(self.resultType, cells);
    state.stack.emplace_back(result);
}

size_t vector_size(const TensorFunction &child, const vespalib::string &dimension) {
    if (child.result_type().is_double()) {
        return 1;
    }
    if (auto vfd = as<VectorFromDoublesFunction>(child)) {
        if (vfd->dimension() == dimension) {
            return vfd->size();
        }
    }
    return 0;
}

void flatten_into(const TensorFunction &child, std::vector<Child> &vec) {
    if (child.result_type().is_double()) {
        vec.emplace_back(child);
    } else {
        std::vector<Child::CREF> tmp;
        child.push_children(tmp);
        for (const Child &c : tmp) {
            assert(c.get().result_type().is_double());
            vec.push_back(c);
        }
    }
}

std::vector<Child> flatten(const TensorFunction &lhs, const TensorFunction &rhs) {
    std::vector<Child> vec;
    flatten_into(lhs, vec);
    flatten_into(rhs, vec);
    return vec;
}

} // namespace vespalib::eval::<unnamed>


VectorFromDoublesFunction::VectorFromDoublesFunction(std::vector<Child> children, const ValueType &res_type)
    : TensorFunction(),
      _self(res_type, children.size()),
      _children(std::move(children))
{
}

VectorFromDoublesFunction::~VectorFromDoublesFunction() = default;

void
VectorFromDoublesFunction::push_children(std::vector<Child::CREF> &target) const
{
    for (const Child &c : _children) {
        target.emplace_back(c);
    }
}

InterpretedFunction::Instruction
VectorFromDoublesFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    return InterpretedFunction::Instruction(my_vector_from_doubles_op, wrap_param<VectorFromDoublesFunction::Self>(_self));
}

const TensorFunction &
VectorFromDoublesFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto concat = as<Concat>(expr)) {
        const vespalib::string &dimension = concat->dimension();
        size_t a_size = vector_size(concat->lhs(), dimension);
        size_t b_size = vector_size(concat->rhs(), dimension);
        if ((a_size > 0) && (b_size > 0)) {
            auto children = flatten(concat->lhs(), concat->rhs());
            assert(children.size() == (a_size + b_size));
            return stash.create<VectorFromDoublesFunction>(std::move(children), expr.result_type());
        }
    }
    return expr;
}

} // namespace vespalib::eval
