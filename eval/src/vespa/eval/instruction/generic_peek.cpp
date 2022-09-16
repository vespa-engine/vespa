// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_peek.h"
#include <vespa/eval/eval/value_builder_factory.h>
#include <vespa/eval/eval/nested_loop.h>
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/util/visit_ranges.h>
#include <vespa/vespalib/util/shared_string_repo.h>
#include <vespa/vespalib/util/small_vector.h>
#include <cassert>
#include <map>

using namespace vespalib::eval::tensor_function;

namespace vespalib::eval::instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

using Handle = SharedStringRepo::Handle;

namespace {

static constexpr size_t npos = -1;

using Spec = GenericPeek::SpecMap;

size_t count_children(const Spec &spec)
{
    // accounts for "input" child:
    size_t num_children = 1;
    for (const auto & [dim_name, child_or_label] : spec) {
        if (std::holds_alternative<size_t>(child_or_label)) {
            ++num_children;
        }
    }
    return num_children;
}

struct DimSpec {
    enum class DimType { CHILD_IDX, LABEL_IDX, LABEL_STR };
    DimType dim_type;
    Handle str;
    size_t idx;
    static DimSpec from_child(size_t child_idx) {
        return {DimType::CHILD_IDX, Handle(), child_idx};
    }
    static DimSpec from_label(const TensorSpec::Label &label) {
        if (label.is_mapped()) {
            return {DimType::LABEL_STR, Handle(label.name), 0};
        } else {
            assert(label.is_indexed());
            return {DimType::LABEL_IDX, Handle(), label.index};
        }
    }
    ~DimSpec();
    bool has_child() const {
        return (dim_type == DimType::CHILD_IDX);
    }
    bool has_label() const {
        return (dim_type != DimType::CHILD_IDX);
    }
    size_t get_child_idx() const {
        assert(dim_type == DimType::CHILD_IDX);
        return idx;
    }
    string_id get_label_name() const {
        assert(dim_type == DimType::LABEL_STR);
        return str.id();
    }
    size_t get_label_index() const {
        assert(dim_type == DimType::LABEL_IDX);
        return idx;
    }
};
DimSpec::~DimSpec() = default;

struct ExtractedSpecs {
    using Dimension = ValueType::Dimension;
    struct MyComp {
        bool operator() (const Dimension &a, const Spec::value_type &b) { return a.name < b.first; }
        bool operator() (const Spec::value_type &a, const Dimension &b) { return a.first < b.name; }
    };
    std::vector<Dimension> dimensions;
    std::map<vespalib::string, DimSpec> specs;

    ExtractedSpecs(bool indexed,
                   const std::vector<Dimension> &input_dims,
                   const Spec &spec)
    {
        auto visitor = overload
        {
            [&](visit_ranges_first, const auto &a) {
                if (a.is_indexed() == indexed) dimensions.push_back(a);
            },
            [&](visit_ranges_second, const auto &) {
                // spec has unknown dimension
                abort();
            },
            [&](visit_ranges_both, const auto &a, const auto &b) {
                if (a.is_indexed() == indexed) {
                    dimensions.push_back(a);
                    const auto & [spec_dim_name, child_or_label] = b;
                    assert(a.name == spec_dim_name);
                    if (std::holds_alternative<size_t>(child_or_label)) {
                        specs[a.name] = DimSpec::from_child(std::get<size_t>(child_or_label));
                    } else {
                        specs[a.name] = DimSpec::from_label(std::get<TensorSpec::Label>(child_or_label));
                    }
                }
            }
        };
        visit_ranges(visitor,
                     input_dims.begin(), input_dims.end(),
                     spec.begin(), spec.end(), MyComp());
    }
    ~ExtractedSpecs();
};
ExtractedSpecs::~ExtractedSpecs() = default;

struct DenseSizes {
    SmallVector<size_t> size;
    SmallVector<size_t> stride;
    size_t cur_size;

    DenseSizes(const std::vector<ValueType::Dimension> &dims)
        : size(), stride(dims.size()), cur_size(1)
    {
        for (const auto &dim : dims) {
            assert(dim.is_indexed());
            size.push_back(dim.size);
        }
        for (size_t i = size.size(); i-- > 0; ) {
            stride[i] = cur_size;
            cur_size *= size[i];
        }
    }
};

/** Compute input offsets for all output cells */
struct DensePlan {
    size_t in_dense_size;
    size_t out_dense_size;
    SmallVector<size_t> loop_cnt;
    SmallVector<size_t> in_stride;
    size_t verbatim_offset = 0;
    struct Child {
        size_t idx;
        size_t stride;
        size_t limit;
    };
    SmallVector<Child,4> children;

    DensePlan(const ValueType &input_type, const Spec &spec)
    {
        const ExtractedSpecs mine(true, input_type.dimensions(), spec);
        DenseSizes sizes(mine.dimensions);
        in_dense_size = sizes.cur_size;
        out_dense_size = 1;
        for (size_t i = 0; i < mine.dimensions.size(); ++i) {
            const auto &dim = mine.dimensions[i];
            auto pos = mine.specs.find(dim.name);
            if (pos == mine.specs.end()) {
                loop_cnt.push_back(sizes.size[i]);
                in_stride.push_back(sizes.stride[i]);
                out_dense_size *= sizes.size[i];
            } else {
                if (pos->second.has_child()) {
                    children.push_back(Child{pos->second.get_child_idx(), sizes.stride[i], sizes.size[i]});
                } else {
                    assert(pos->second.has_label());
                    size_t label_index = pos->second.get_label_index();
                    assert(label_index < sizes.size[i]);
                    verbatim_offset += label_index * sizes.stride[i];
                }
            }
        }
    }

    /** Get initial offset (from verbatim labels and child values) */
    template <typename Getter>
    size_t get_offset(const Getter &get_child_value) const {
        size_t offset = verbatim_offset;
        for (size_t i = 0; i < children.size(); ++i) {
            size_t from_child = get_child_value(children[i].idx);
            if (from_child < children[i].limit) {
                offset += from_child * children[i].stride;
            } else {
                return npos;
            }
        }
        return offset;
    }

    template<typename F> void execute(size_t offset, const F &f) const {
        run_nested_loop<F>(offset, loop_cnt, in_stride, f);
    }
};

struct SparseState {
    SmallVector<Handle> handles;
    SmallVector<string_id> view_addr;
    SmallVector<const string_id *> lookup_refs;
    SmallVector<string_id> output_addr;
    SmallVector<string_id *> fetch_addr;

    SparseState(SmallVector<Handle> handles_in, SmallVector<string_id> view_addr_in, size_t out_dims)
        : handles(std::move(handles_in)),
          view_addr(std::move(view_addr_in)),
          lookup_refs(view_addr.size()),
          output_addr(out_dims),
          fetch_addr(out_dims)
    {
        for (size_t i = 0; i < view_addr.size(); ++i) {
            lookup_refs[i] = &view_addr[i];
        }
        for (size_t i = 0; i < out_dims; ++i) {
            fetch_addr[i] = &output_addr[i];
        }
    }
    ~SparseState();
};
SparseState::~SparseState() = default;

struct SparsePlan {
    size_t out_mapped_dims;
    SmallVector<DimSpec> lookup_specs;
    SmallVector<size_t> view_dims;

    SparsePlan(const ValueType &input_type,
               const GenericPeek::SpecMap &spec)
        : out_mapped_dims(0),
          view_dims()
    {
        ExtractedSpecs mine(false, input_type.dimensions(), spec);
        for (size_t dim_idx = 0; dim_idx < mine.dimensions.size(); ++dim_idx) {
            const auto & dim = mine.dimensions[dim_idx];
            auto pos = mine.specs.find(dim.name);
            if (pos == mine.specs.end()) {
                ++out_mapped_dims;
            } else {
                view_dims.push_back(dim_idx);
                lookup_specs.push_back(pos->second);
            }
        }
    }

    ~SparsePlan();

    template <typename Getter>
    SparseState make_state(const Getter &get_child_value) const {
        SmallVector<Handle> handles;
        SmallVector<string_id> view_addr;
        for (const auto & dim : lookup_specs) {
            if (dim.has_child()) {
                int64_t child_value = get_child_value(dim.get_child_idx());
                handles.push_back(Handle::handle_from_number(child_value));
                view_addr.push_back(handles.back().id());
            } else {
                view_addr.push_back(dim.get_label_name());
            }
        }
        assert(view_addr.size() == view_dims.size());
        return SparseState(std::move(handles), std::move(view_addr), out_mapped_dims);
    }
};
SparsePlan::~SparsePlan() = default;

struct PeekParam {
    const ValueType res_type;
    DensePlan dense_plan;
    SparsePlan sparse_plan;
    size_t num_children;
    const ValueBuilderFactory &factory;

    PeekParam(const ValueType &res_type_in,
              const ValueType &input_type,
              const GenericPeek::SpecMap &spec_in,
              const ValueBuilderFactory &factory_in)
        : res_type(res_type_in),
          dense_plan(input_type, spec_in),
          sparse_plan(input_type, spec_in),
          num_children(count_children(spec_in)),
          factory(factory_in)
    {
        assert(dense_plan.in_dense_size == input_type.dense_subspace_size());
        assert(dense_plan.out_dense_size == res_type.dense_subspace_size());
    }
};

template <typename ICT, typename OCT, typename Getter>
Value::UP
generic_mixed_peek(const ValueType &res_type,
                   const Value &input_value,
                   const SparsePlan &sparse_plan,
                   const DensePlan &dense_plan,
                   const ValueBuilderFactory &factory,
                   const Getter &get_child_value)
{
    auto input_cells = input_value.cells().typify<ICT>();
    size_t bad_guess = 1;
    auto builder = factory.create_transient_value_builder<OCT>(res_type,
                                                               sparse_plan.out_mapped_dims,
                                                               dense_plan.out_dense_size,
                                                               bad_guess);
    size_t filled_subspaces = 0;
    size_t dense_offset = dense_plan.get_offset(get_child_value);
    if (dense_offset != npos) {
        SparseState state = sparse_plan.make_state(get_child_value);
        auto view = input_value.index().create_view(sparse_plan.view_dims);
        view->lookup(state.lookup_refs);
        size_t input_subspace;
        while (view->next_result(state.fetch_addr, input_subspace)) {
            auto dst = builder->add_subspace(state.output_addr).begin();
            auto input_offset = input_subspace * dense_plan.in_dense_size;
            dense_plan.execute(dense_offset + input_offset,
                               [&](size_t idx) { *dst++ = input_cells[idx]; });
            ++filled_subspaces;
        }
    }
    if ((sparse_plan.out_mapped_dims == 0) && (filled_subspaces == 0)) {
        for (auto & v : builder->add_subspace()) {
            v = OCT{};
        }
    }
    return builder->build(std::move(builder));
}

template <typename ICT, typename OCT>
void my_generic_peek_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<PeekParam>(param_in);
    // stack index for children are in range [0, num_children>
    size_t last_valid_stack_idx = param.num_children - 1;
    const Value & input_value = state.peek(last_valid_stack_idx);
    auto get_child_value = [&] (size_t child_idx) {
        size_t stack_idx = last_valid_stack_idx - child_idx;
        return int64_t(state.peek(stack_idx).as_double());
    };
    auto up = generic_mixed_peek<ICT,OCT>(param.res_type, input_value,
                                          param.sparse_plan, param.dense_plan,
                                          param.factory, get_child_value);
    const Value &result = *state.stash.create<Value::UP>(std::move(up));
    // num_children includes the "input" param
    state.pop_n_push(param.num_children, result);
}

struct SelectGenericPeekOp {
    template <typename ICM, typename OutIsScalar> static auto invoke() {
        using ICT = CellValueType<ICM::value.cell_type>;
        constexpr CellMeta ocm = ICM::value.peek(OutIsScalar::value);
        using OCT = CellValueType<ocm.cell_type>;
        return my_generic_peek_op<ICT,OCT>;
    }
};

//-----------------------------------------------------------------------------

} // namespace <unnamed>

Instruction
GenericPeek::make_instruction(const ValueType &result_type,
                              const ValueType &input_type,
                              const SpecMap &spec,
                              const ValueBuilderFactory &factory,
                              Stash &stash)
{
    using PeekTypify = TypifyValue<TypifyCellMeta,TypifyBool>;
    const auto &param = stash.create<PeekParam>(result_type, input_type, spec, factory);
    auto fun = typify_invoke<2,PeekTypify,SelectGenericPeekOp>(input_type.cell_meta().not_scalar(), result_type.is_double());
    return Instruction(fun, wrap_param<PeekParam>(param));
}

} // namespace
