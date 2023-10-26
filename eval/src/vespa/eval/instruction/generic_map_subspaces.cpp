// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_map_subspaces.h"

using namespace vespalib::eval::tensor_function;

namespace vespalib::eval::instruction {

using Instruction = InterpretedFunction::Instruction;
using State = InterpretedFunction::State;

namespace {

//-----------------------------------------------------------------------------

struct InterpretedParams {
    const ValueType &result_type;
    const ValueType &inner_type;
    InterpretedFunction fun;
    size_t in_size;
    size_t out_size;
    bool direct_in;
    bool direct_out;
    InterpretedParams(const MapSubspaces &map_subspaces, const ValueBuilderFactory &factory)
      : result_type(map_subspaces.result_type()),
        inner_type(map_subspaces.inner_type()),
        fun(factory, map_subspaces.lambda().root(), map_subspaces.types()),
        in_size(inner_type.dense_subspace_size()),
        out_size(result_type.dense_subspace_size()),
        direct_in(map_subspaces.child().result_type().cell_type() == inner_type.cell_type()),
        direct_out(map_subspaces.types().get_type(map_subspaces.lambda().root()).cell_type() == result_type.cell_type())        
    {
        assert(direct_in || (in_size == 1));
        assert(direct_out || (out_size == 1));
    }
};

struct ParamView final : Value, LazyParams {
    const ValueType &my_type;
    TypedCells my_cells;
    double value;
    bool direct;
public:
    ParamView(const ValueType &type_in, bool direct_in)
      : my_type(type_in), my_cells(), value(0.0), direct(direct_in) {}
    const ValueType &type() const final override { return my_type; }
    template <typename ICT>
    void adjust(const ICT *cells, size_t size) {
        if (direct) {
            my_cells = TypedCells(cells, get_cell_type<ICT>(), size);
        } else {
            value = cells[0];
            my_cells = TypedCells(&value, CellType::DOUBLE, 1);
        }
    }
    TypedCells cells() const final override { return my_cells; }
    const Index &index() const final override { return TrivialIndex::get(); }
    MemoryUsage get_memory_usage() const final override { return self_memory_usage<ParamView>(); }
    const Value &resolve(size_t, Stash &) const final override { return *this; }
};

template <typename OCT>
struct ResultFiller {
    OCT *dst;
    bool direct;
public:
    ResultFiller(OCT *dst_in, bool direct_out)
      : dst(dst_in), direct(direct_out) {}
    void fill(const Value &value) {
        if (direct) {
            auto cells = value.cells();
            memcpy(dst, cells.data, sizeof(OCT) * cells.size);
            dst += cells.size;
        } else {
            *dst++ = value.as_double();
        }
    }
};

template <typename ICT, typename OCT>
void my_generic_map_subspaces_op(InterpretedFunction::State &state, uint64_t param) {
    const InterpretedParams &params = unwrap_param<InterpretedParams>(param);
    InterpretedFunction::Context ctx(params.fun);
    const Value &input = state.peek(0);
    const ICT *src = input.cells().typify<ICT>().data();
    size_t num_subspaces = input.index().size();
    auto res_cells = state.stash.create_uninitialized_array<OCT>(num_subspaces * params.out_size);
    ResultFiller result_filler(res_cells.data(), params.direct_out);
    ParamView param_view(params.inner_type, params.direct_in);
    for (size_t i = 0; i < num_subspaces; ++i) {
        param_view.adjust(src, params.in_size);
        src += params.in_size;
        result_filler.fill(params.fun.eval(ctx, param_view));
    }
    state.pop_push(state.stash.create<ValueView>(params.result_type, input.index(), TypedCells(res_cells)));
}

struct SelectGenericMapSubspacesOp {
    template <typename ICT, typename OCT> static auto invoke() {
        return my_generic_map_subspaces_op<ICT,OCT>;
    }
};

//-----------------------------------------------------------------------------

} // namespace <unnamed>

Instruction
GenericMapSubspaces::make_instruction(const tensor_function::MapSubspaces &map_subspaces_in,
                                      const ValueBuilderFactory &factory, Stash &stash)
{
    InterpretedParams &params = stash.create<InterpretedParams>(map_subspaces_in, factory);
    auto op = typify_invoke<2,TypifyCellType,SelectGenericMapSubspacesOp>(map_subspaces_in.child().result_type().cell_type(),
                                                                          params.result_type.cell_type());
    return Instruction(op, wrap_param<InterpretedParams>(params));
}

} // namespace
