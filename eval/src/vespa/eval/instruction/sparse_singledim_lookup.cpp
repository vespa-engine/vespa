// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_singledim_lookup.h"
#include <vespa/eval/eval/fast_value.hpp>
#include <vespa/vespalib/util/typify.h>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;
using namespace instruction;
using Handle = SharedStringRepo::Handle;

namespace {

template <typename CT>
double my_sparse_singledim_lookup_fallback(const Value::Index &idx, const CT *cells, string_id key) __attribute__((noinline));
template <typename CT>
double my_sparse_singledim_lookup_fallback(const Value::Index &idx, const CT *cells, string_id key) {
    size_t subspace = 0;
    const string_id *key_ref = &key;
    auto view = idx.create_view(ConstArrayRef<size_t>{&subspace, 1});
    view->lookup(ConstArrayRef<const string_id *>{&key_ref, 1});
    if (!view->next_result({}, subspace)) {
        return 0.0;
    }
    return cells[subspace];
}

template <typename CT>
double my_fast_sparse_singledim_lookup(const FastAddrMap *map, const CT *cells, string_id key)
{
    auto subspace = map->lookup_singledim(key);
    return (subspace != FastAddrMap::npos()) ? double(cells[subspace]) : 0.0;
}

template <typename CT>
void my_sparse_singledim_lookup_op(InterpretedFunction::State &state, uint64_t) {
    const auto &idx = state.peek(1).index();
    const CT *cells = state.peek(1).cells().typify<CT>().cbegin();
    int64_t number(state.peek(0).as_double());
    Handle handle = Handle::handle_from_number(number);
    double result = __builtin_expect(is_fast(idx), true)
        ? my_fast_sparse_singledim_lookup<CT>(&as_fast(idx).map, cells, handle.id())
        : my_sparse_singledim_lookup_fallback<CT>(idx, cells, handle.id());
    state.pop_pop_push(state.stash.create<DoubleValue>(result));
}

struct MyGetFun {
    template <typename CT>
    static auto invoke() { return my_sparse_singledim_lookup_op<CT>; }
};

} // namespace <unnamed>

SparseSingledimLookup::SparseSingledimLookup(const TensorFunction &tensor,
                                             const TensorFunction &expr)
    : tensor_function::Op2(ValueType::double_type(), tensor, expr)
{
}

InterpretedFunction::Instruction
SparseSingledimLookup::compile_self(const ValueBuilderFactory &, Stash &) const
{
    auto op = typify_invoke<1,TypifyCellType,MyGetFun>(lhs().result_type().cell_type());
    return InterpretedFunction::Instruction(op);
}

const TensorFunction &
SparseSingledimLookup::optimize(const TensorFunction &expr, Stash &stash)
{
    auto peek = as<Peek>(expr);
    if (peek && peek->result_type().is_double() &&
        peek->param_type().is_sparse() &&
        (peek->param_type().dimensions().size() == 1) &&
        (peek->map().size() == 1) &&
        (std::holds_alternative<TensorFunction::Child>(peek->map().begin()->second)))
    {
        return stash.create<SparseSingledimLookup>(peek->param(), std::get<TensorFunction::Child>(peek->map().begin()->second).get());
    }
    return expr;
}

} // namespace
