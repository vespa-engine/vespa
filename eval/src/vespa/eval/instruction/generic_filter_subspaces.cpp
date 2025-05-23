// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/wrap_param.h>
#include "generic_filter_subspaces.h"
#include <vespa/eval/eval/value_builder_factory.h>

using namespace vespalib::eval::tensor_function;

namespace vespalib::eval::instruction {

using Instruction = InterpretedFunction::Instruction;
using State = InterpretedFunction::State;

namespace {

//-----------------------------------------------------------------------------

struct InterpretedParams {
    const ValueBuilderFactory &factory;
    const ValueType &result_type;
    const ValueType &inner_type;
    InterpretedFunction fun;
    size_t num_mapped;
    size_t dense_size;
    bool direct;
    InterpretedParams(const ValueType &result_type_in,
                      const ValueType &inner_type_in,
                      const Function &lambda, const NodeTypes &types,
                      const ValueBuilderFactory &factory_in)
      : factory(factory_in),
        result_type(result_type_in),
        inner_type(inner_type_in),
        fun(factory, lambda.root(), types),
        num_mapped(result_type.count_mapped_dimensions()),
        dense_size(result_type.dense_subspace_size()),
        direct(result_type.cell_type() == inner_type.cell_type())
    {
        assert(num_mapped > 0);
        assert(dense_size == inner_type.dense_subspace_size());
        assert(direct || (dense_size == 1 && inner_type.cell_type() == CellType::DOUBLE));
    }
};

struct ParamView final : Value, LazyParams {
    const ValueType &my_type;
    TypedCells my_cells;
    double value;
    bool direct;
public:
    ParamView(const ValueType &type_in, bool direct_in)
      : my_type(type_in), my_cells(), value(0.0), direct(direct_in)
    {
        if (!direct) {
            my_cells = TypedCells(&value, CellType::DOUBLE, 1);            
        }
    }
    const ValueType &type() const final override { return my_type; }
    template <typename CT>
    void adjust(const CT *cells, size_t size) {
        if (direct) {
            my_cells = TypedCells(cells, get_cell_type<CT>(), size);
        } else {
            value = cells[0];
        }
    }
    TypedCells cells() const final override { return my_cells; }
    const Index &index() const final override { return TrivialIndex::get(); }
    MemoryUsage get_memory_usage() const final override { return self_memory_usage<ParamView>(); }
    const Value &resolve(size_t, Stash &) const final override { return *this; }
};

template <typename CT>
void my_generic_filter_subspaces_op(InterpretedFunction::State &state, uint64_t param) {
    const InterpretedParams &params = unwrap_param<InterpretedParams>(param);
    InterpretedFunction::Context ctx(params.fun);
    const Value &input = state.peek(0);
    const auto &idx = input.index();
    auto input_cells = input.cells().typify<CT>();
    auto builder = params.factory.create_value_builder<CT>(params.result_type, params.num_mapped, params.dense_size, idx.size());
    std::vector<string_id> addr(params.num_mapped);
    auto view = idx.create_view({});
    view->lookup({});
    std::vector<string_id*> addr_fetch;
    addr_fetch.reserve(params.num_mapped);
    for (auto &label: addr) {
        addr_fetch.push_back(&label);
    }
    size_t subspace_idx;
    ParamView param_view(params.inner_type, params.direct);
    while (view->next_result(addr_fetch, subspace_idx)) {
        const CT *src = input_cells.data() + subspace_idx * params.dense_size;
        param_view.adjust(src, params.dense_size);
        if (params.fun.eval(ctx, param_view).as_bool()) {
            auto array_ref = builder->add_subspace(addr);
            memcpy(array_ref.data(), src, sizeof(CT) * params.dense_size);
        }
    }
    auto up = builder->build(std::move(builder));
    auto &result = state.stash.create<std::unique_ptr<Value>>(std::move(up));
    const Value &result_ref = *(result.get());
    state.pop_push(result_ref);
}

struct SelectGenericFilterSubspacesOp {
    template <typename CT> static auto invoke() {
        return my_generic_filter_subspaces_op<CT>;
    }
};

//-----------------------------------------------------------------------------

} // namespace <unnamed>

Instruction
GenericFilterSubspaces::make_instruction(const ValueType &result_type,
                                         const ValueType &inner_type,
                                         const Function &lambda, const NodeTypes &types,
                                         const ValueBuilderFactory &factory, Stash &stash)
{
    InterpretedParams &params = stash.create<InterpretedParams>(result_type, inner_type, lambda, types, factory);
    auto op = typify_invoke<1, TypifyCellType, SelectGenericFilterSubspacesOp>(params.result_type.cell_type());
    return Instruction(op, wrap_param<InterpretedParams>(params));
}

} // namespace
