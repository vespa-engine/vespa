// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_tensor_peek_function.h"
#include <vespa/eval/eval/value.h>

namespace vespalib::eval {

using Child = TensorFunction::Child;
using SpecVector = SmallVector<std::pair<int64_t,size_t>>;
using namespace tensor_function;

namespace {

template <typename CT>
void my_tensor_peek_op(InterpretedFunction::State &state, uint64_t param) {
    const SpecVector &spec = unwrap_param<SpecVector>(param);
    size_t idx = 0;
    size_t factor = 1;
    bool valid = true;
    for (const auto &dim: spec) {
        if (dim.first >= 0) {
            idx += (dim.first * factor);
        } else {
            size_t dim_idx = (int64_t) state.peek(0).as_double();
            state.stack.pop_back();
            valid &= (dim_idx < dim.second);
            idx += (dim_idx * factor);
        }
        factor *= dim.second;
    }
    auto cells = state.peek(0).cells().typify<CT>();
    state.stack.pop_back();
    const Value &result = state.stash.create<DoubleValue>(valid ? double(cells[idx]) : 0.0);
    state.stack.emplace_back(result);
}

struct MyTensorPeekOp {
    template <typename CT>
    static auto invoke() { return my_tensor_peek_op<CT>; }
};

} // namespace <unnamed>

DenseTensorPeekFunction::DenseTensorPeekFunction(std::vector<Child> children,
                                                 SmallVector<std::pair<int64_t,size_t>> spec)
    : TensorFunction(),
      _children(std::move(children)),
      _spec(std::move(spec))
{
}

DenseTensorPeekFunction::~DenseTensorPeekFunction() = default;

void
DenseTensorPeekFunction::push_children(std::vector<Child::CREF> &target) const
{
    for (const Child &c: _children) {
        target.emplace_back(c);
    }
}

InterpretedFunction::Instruction
DenseTensorPeekFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    using MyTypify = TypifyCellType;
    auto op = typify_invoke<1,MyTypify,MyTensorPeekOp>(_children[0].get().result_type().cell_type());
    return InterpretedFunction::Instruction(op, wrap_param<SpecVector>(_spec));
}

const TensorFunction &
DenseTensorPeekFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto peek = as<Peek>(expr)) {
        const ValueType &peek_type = peek->param_type();
        if (expr.result_type().is_double() && peek_type.is_dense()) {
            SmallVector<std::pair<int64_t,size_t>> spec;
            assert(peek_type.dimensions().size() == peek->map().size());
            for (auto dim = peek_type.dimensions().rbegin(); dim != peek_type.dimensions().rend(); ++dim) {
                auto dim_spec = peek->map().find(dim->name);
                assert(dim_spec != peek->map().end());

                std::visit(vespalib::overload
                           {
                               [&](const TensorSpec::Label &label) {
                                   assert(label.is_indexed());
                                   spec.emplace_back(label.index, dim->size);
                               },
                               [&](const TensorFunction::Child &) {
                                   spec.emplace_back(-1, dim->size);
                               }
                           }, dim_spec->second);
            }
            return stash.create<DenseTensorPeekFunction>(peek->copy_children(), std::move(spec));
        }
    }
    return expr;
}

} // namespace
