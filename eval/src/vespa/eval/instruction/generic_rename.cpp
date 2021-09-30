// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_rename.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <cassert>

using namespace vespalib::eval::tensor_function;

namespace vespalib::eval::instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

namespace {

const vespalib::string &
find_rename(const vespalib::string & original,
            const std::vector<vespalib::string> &from,
            const std::vector<vespalib::string> &to)
{
    for (size_t i = 0; i < from.size(); ++i) {
        if (original == from[i]) {
            return to[i];
        }
    }
    return original;
}

size_t
find_index_of(const vespalib::string & name,
              const std::vector<ValueType::Dimension> & dims)
{
    for (size_t i = 0; i < dims.size(); ++i) {
        if (name == dims[i].name) {
            return i;
        }
    }
    abort(); // should not happen
}

struct RenameParam {
    ValueType res_type;
    SparseRenamePlan sparse_plan;
    DenseRenamePlan dense_plan;
    const ValueBuilderFactory &factory;
    RenameParam(const ValueType &lhs_type,
                const std::vector<vespalib::string> &rename_dimension_from,
                const std::vector<vespalib::string> &rename_dimension_to,
                const ValueBuilderFactory &factory_in)
        : res_type(lhs_type.rename(rename_dimension_from, rename_dimension_to)),
          sparse_plan(lhs_type, res_type, rename_dimension_from, rename_dimension_to),
          dense_plan(lhs_type, res_type, rename_dimension_from, rename_dimension_to),
          factory(factory_in)
    {
        assert(!res_type.is_error());
        assert(lhs_type.cell_type() == res_type.cell_type());
    }
    ~RenameParam();
};
RenameParam::~RenameParam() = default;

template <typename CT>
std::unique_ptr<Value>
generic_rename(const Value &a,
               const SparseRenamePlan &sparse_plan, const DenseRenamePlan &dense_plan,
               const ValueType &res_type, const ValueBuilderFactory &factory)
{
    auto cells = a.cells().typify<CT>();
    SmallVector<string_id> output_address(sparse_plan.mapped_dims);
    SmallVector<string_id*> input_address;
    for (size_t maps_to : sparse_plan.output_dimensions) {
        input_address.emplace_back(&output_address[maps_to]);
    }
    auto builder = factory.create_transient_value_builder<CT>(res_type,
                                                              sparse_plan.mapped_dims,
                                                              dense_plan.subspace_size,
                                                              a.index().size());
    auto view = a.index().create_view({});
    view->lookup({});
    size_t subspace;
    while (view->next_result(input_address, subspace)) {
        CT *dst = builder->add_subspace(output_address).begin();
        size_t input_offset = dense_plan.subspace_size * subspace;
        auto copy_cells = [&](size_t input_idx) { *dst++ = cells[input_idx]; };
        dense_plan.execute(input_offset, copy_cells);
    }
    return builder->build(std::move(builder));
}

template <typename CT>
void my_generic_rename_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<RenameParam>(param_in);
    const Value &a = state.peek(0);
    auto res_value = generic_rename<CT>(a, param.sparse_plan, param.dense_plan,
                                        param.res_type, param.factory);
    auto &result = state.stash.create<std::unique_ptr<Value>>(std::move(res_value));
    const Value &result_ref = *(result.get());
    state.pop_push(result_ref);
}

template <typename CT>
void my_mixed_rename_dense_only_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<RenameParam>(param_in);
    const DenseRenamePlan &dense_plan = param.dense_plan;
    const auto &index = state.peek(0).index();
    auto lhs_cells = state.peek(0).cells().typify<CT>();
    size_t num_subspaces = index.size();
    size_t num_out_cells = dense_plan.subspace_size * num_subspaces;
    ArrayRef<CT> out_cells = state.stash.create_uninitialized_array<CT>(num_out_cells);
    CT *dst = out_cells.begin();
    const CT *lhs = lhs_cells.begin();
    auto copy_cells = [&](size_t input_idx) { *dst++ = lhs[input_idx]; };
    for (size_t i = 0; i < num_subspaces; ++i) {
        dense_plan.execute(0, copy_cells);
        lhs += dense_plan.subspace_size;
    }
    assert(lhs == lhs_cells.end());
    assert(dst == out_cells.end());
    state.pop_push(state.stash.create<ValueView>(param.res_type, index, TypedCells(out_cells)));
}

struct SelectGenericRenameOp {
    template <typename CM> static auto invoke(const RenameParam &param) {
        using CT = CellValueType<CM::value.cell_type>;
        if (param.sparse_plan.can_forward_index) {
            return my_mixed_rename_dense_only_op<CT>;
        }
        return my_generic_rename_op<CT>;
    }
};

} // namespace <unnamed>

//-----------------------------------------------------------------------------
 
SparseRenamePlan::SparseRenamePlan(const ValueType &input_type,
                                   const ValueType &output_type,
                                   const std::vector<vespalib::string> &from,
                                   const std::vector<vespalib::string> &to)
  : output_dimensions(), can_forward_index(true)
{
    const auto in_dims = input_type.mapped_dimensions();
    const auto out_dims = output_type.mapped_dimensions();
    mapped_dims = in_dims.size();
    assert(mapped_dims == out_dims.size());
    for (const auto & dim : in_dims) {
        const auto & renamed_to = find_rename(dim.name, from, to);
        size_t index = find_index_of(renamed_to, out_dims);
        assert(index < mapped_dims);
        if (index != output_dimensions.size()) {
            can_forward_index = false;
        }
        output_dimensions.emplace_back(index);
    }
    assert(output_dimensions.size() == mapped_dims);
}

SparseRenamePlan::~SparseRenamePlan() = default;

DenseRenamePlan::DenseRenamePlan(const ValueType &lhs_type,
                                 const ValueType &output_type,
                                 const std::vector<vespalib::string> &from,
                                 const std::vector<vespalib::string> &to)
    : loop_cnt(),
      stride(),
      subspace_size(output_type.dense_subspace_size())
{
    assert (subspace_size == lhs_type.dense_subspace_size());
    const auto lhs_dims = lhs_type.nontrivial_indexed_dimensions();
    const auto out_dims = output_type.nontrivial_indexed_dimensions();
    size_t num_dense_dims = lhs_dims.size();
    assert(num_dense_dims == out_dims.size());
    SmallVector<size_t> lhs_loopcnt(num_dense_dims);
    SmallVector<size_t> lhs_stride(num_dense_dims, 1);
    size_t lhs_size = 1;
    for (size_t i = num_dense_dims; i-- > 0; ) {
        lhs_stride[i] = lhs_size;
        lhs_loopcnt[i] = lhs_dims[i].size;
        lhs_size *= lhs_loopcnt[i];
    }
    assert(lhs_size == subspace_size);
    size_t prev_index = num_dense_dims;
    for (const auto & dim : out_dims) {
        const auto & renamed_from = find_rename(dim.name, to, from);
        size_t index = find_index_of(renamed_from, lhs_dims);
        assert(index < num_dense_dims);
        if (prev_index + 1 == index) {
            assert(stride.back() == lhs_stride[index] * lhs_loopcnt[index]);
            loop_cnt.back() *= lhs_loopcnt[index];
            stride.back() = lhs_stride[index];
        } else {
            loop_cnt.emplace_back(lhs_loopcnt[index]);
            stride.emplace_back(lhs_stride[index]);
        }
        prev_index = index;
    }
}

DenseRenamePlan::~DenseRenamePlan() = default;

InterpretedFunction::Instruction
GenericRename::make_instruction(const ValueType &result_type,
                                const ValueType &input_type,
                                const std::vector<vespalib::string> &rename_dimension_from,
                                const std::vector<vespalib::string> &rename_dimension_to,
                                const ValueBuilderFactory &factory, Stash &stash)
{
    auto &param = stash.create<RenameParam>(input_type,
                                            rename_dimension_from, rename_dimension_to,
                                            factory);
    assert(result_type == param.res_type);
    assert(result_type.cell_meta().eq(input_type.cell_meta()));
    auto fun = typify_invoke<1,TypifyCellMeta,SelectGenericRenameOp>(param.res_type.cell_meta().not_scalar(), param);
    return Instruction(fun, wrap_param<RenameParam>(param));
}

} // namespace
