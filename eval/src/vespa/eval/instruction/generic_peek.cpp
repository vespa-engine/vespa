// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_peek.h"
#include <vespa/eval/eval/nested_loop.h>
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <cassert>

using namespace vespalib::eval::tensor_function;

namespace vespalib::eval::instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

namespace {

static constexpr size_t npos = -1;

enum class Source { COPY_INPUT, FROM_CHILD, VERBATIM };

struct DenseSizes {
    std::vector<size_t> loop_cnt;
    std::vector<size_t> stride;

    DenseSizes(const ValueType &input_type)
        : loop_cnt(), stride()
    {
        for (const auto &dim : input_type.dimensions()) {
            if (dim.is_indexed()) {
                loop_cnt.push_back(dim.size);
            }
        }
        stride.resize(loop_cnt.size());
        size_t out_size = 1;
        for (size_t i = loop_cnt.size(); i-- > 0; ) {
            stride[i] = out_size;
            out_size *= loop_cnt[i];
        }
        assert(out_size == input_type.dense_subspace_size());
    }
};

/** Compute input offsets for all output cells */
struct DensePlan {
    std::vector<size_t> loop_cnt;
    std::vector<size_t> in_stride;
    size_t verbatim_offset = 0;
    std::vector<size_t> child_idx;
    std::vector<size_t> child_stride;
    std::vector<size_t> child_limit;

    DensePlan() = default;

    void init_from(const DenseSizes &sizes,
                   const std::vector<std::pair<Source,size_t>> &where)
    {
        for (size_t i = 0; i < where.size(); ++i) {
            auto [from, idx] = where[i];
            switch (from) {
            case Source::COPY_INPUT:
                loop_cnt.push_back(sizes.loop_cnt[i]);
                in_stride.push_back(sizes.stride[i]);
                break;
            case Source::VERBATIM:
                assert(idx < sizes.loop_cnt[i]);
                verbatim_offset += idx * sizes.stride[i];
                break;
            case Source::FROM_CHILD:
                child_stride.push_back(sizes.stride[i]);
                child_idx.push_back(idx);
                child_limit.push_back(sizes.loop_cnt[i]);
                break;
            }
        }
    }

    /** Get initial offset (from verbatim labels and child values) */
    template <typename Getter>
    size_t get_offset(const Getter &get_child_value) const
    {
        size_t offset = verbatim_offset;
        for (size_t i = 0; i < child_idx.size(); ++i) {
            size_t from_child = get_child_value(child_idx[i]);
            if (from_child < child_limit[i]) {
                offset += from_child * child_stride[i];
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

using SparsePlan = std::vector<std::pair<Source, TensorSpec::Label>>;

struct PeekParam {
    const ValueType res_type;
    size_t out_mapped_dims;
    size_t out_dense_size;
    const ValueType input_type;
    size_t in_dense_size;
    DenseSizes dense_sizes;
    std::vector<std::pair<Source,size_t>> dense_where;
    DensePlan dense_plan;
    SparsePlan sparse_plan;
    std::vector<size_t> view_dims;
    size_t num_children;
    const ValueBuilderFactory &factory;

    PeekParam(const ValueType &input_type_in,
              const ValueType &res_type_in,
              const GenericPeek::SpecMap &spec_in,
              const ValueBuilderFactory &factory_in)
        : res_type(res_type_in),
          out_mapped_dims(res_type.count_mapped_dimensions()),
          out_dense_size(res_type.dense_subspace_size()),
          input_type(input_type_in),
          in_dense_size(input_type.dense_subspace_size()),
          dense_sizes(input_type),
          dense_plan(),
          sparse_plan(),
          view_dims(),
          num_children(0),
          factory(factory_in)
    {
        process_spec(spec_in);
    }

    void copy_dim(const ValueType::Dimension &in_dim,
                  const ValueType::Dimension &res_dim)
    {
        assert(in_dim.name == res_dim.name);
        assert(in_dim.size == res_dim.size);
        if (in_dim.is_mapped()) {
            sparse_plan.emplace_back(Source::COPY_INPUT, npos);
        } else {
            dense_where.emplace_back(Source::COPY_INPUT, npos);
        }
    }

    void process_spec(const GenericPeek::SpecMap &spec)
    {
        const auto & input_dims = input_type.dimensions();
        const auto & res_dims = res_type.dimensions();
        auto input_pos = input_dims.begin();
        auto res_pos = res_dims.begin();
        for (const auto & [dim_name, label_or_idx] : spec) {
            assert(input_pos != input_dims.end());
            while (input_pos->name < dim_name) {
                copy_dim(*input_pos, *res_pos);
                ++res_pos;
                ++input_pos;
                assert(input_pos != input_dims.end());
            }
            assert(input_pos->name == dim_name);
            if (std::holds_alternative<size_t>(label_or_idx)) {
                auto child_idx = std::get<size_t>(label_or_idx);
                if (input_pos->is_mapped()) {
                    view_dims.push_back(sparse_plan.size());
                    sparse_plan.emplace_back(Source::FROM_CHILD, child_idx);
                } else {
                    dense_where.emplace_back(Source::FROM_CHILD, child_idx);
                }
                ++num_children;
            } else {
                const auto &label = std::get<TensorSpec::Label>(label_or_idx);
                assert(label.is_mapped() == input_pos->is_mapped());
                if (input_pos->is_mapped()) {
                    view_dims.push_back(sparse_plan.size());
                    sparse_plan.emplace_back(Source::VERBATIM, label);
                } else {
                    dense_where.emplace_back(Source::VERBATIM, label.index);
                }
            }
            ++input_pos;
        }
        while (input_pos != input_dims.end()) {
            copy_dim(*input_pos, *res_pos);
            ++res_pos;
            ++input_pos;
        }
        assert(res_pos == res_dims.end());
        dense_plan.init_from(dense_sizes, dense_where);
    }

};

template <typename ICT, typename OCT>
void my_generic_peek_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<PeekParam>(param_in);
    const Value & input_value = state.peek(param.num_children);
    const size_t last_child = param.num_children - 1;
    auto get_child_value = [&] (size_t child_idx) {
        size_t stack_idx = last_child - child_idx;
        return int64_t(state.peek(stack_idx).as_double());
    };
    auto input_cells = input_value.cells().typify<ICT>();
    size_t bad_guess = 1;
    auto builder = param.factory.create_value_builder<OCT>(param.res_type,
                                                           param.out_mapped_dims,
                                                           param.out_dense_size,
                                                           bad_guess);
    size_t filled_subspaces = 0;
    size_t dense_offset = param.dense_plan.get_offset(get_child_value);
    if (dense_offset != npos) {
        std::vector<vespalib::string> sparse_addr;
        for (const auto & [from, label] : param.sparse_plan) {
            switch (from) {
            case Source::VERBATIM:
                sparse_addr.push_back(label.name);
                break;
            case Source::FROM_CHILD:
                sparse_addr.push_back(vespalib::make_string("%" PRId64, get_child_value(label.index)));
                break;
            case Source::COPY_INPUT:
                break;
            }
        }
        auto view = input_value.index().create_view(param.view_dims);
        {
            std::vector<vespalib::stringref> sparse_addr_refs(sparse_addr.begin(), sparse_addr.end());
            std::vector<const vespalib::stringref *> sparse_addr_ref_ptrs;
            for (const auto & label : sparse_addr_refs) {
                sparse_addr_ref_ptrs.push_back(&label);
            }
            assert (param.view_dims.size() == sparse_addr_ref_ptrs.size());
            view->lookup(sparse_addr_ref_ptrs);
        }
        std::vector<vespalib::stringref> output_addr(param.out_mapped_dims);
        std::vector<vespalib::stringref*> fetch_addr(param.out_mapped_dims);
        for (size_t i = 0; i < output_addr.size(); ++i) {
            fetch_addr[i] = &output_addr[i];
        }
        size_t input_subspace;
        while (view->next_result(fetch_addr, input_subspace)) {
            auto dst = builder->add_subspace(output_addr).begin();
            auto offset = input_subspace * param.in_dense_size;
            param.dense_plan.execute(dense_offset, [&](size_t idx) { *dst++ = input_cells[offset + idx]; });
            ++filled_subspaces;
        }
    }
    if ((param.out_mapped_dims == 0) && (filled_subspaces == 0)) {
        for (auto & v : builder->add_subspace({})) {
            v = OCT{};
        }
    }
    const Value &result = *state.stash.create<Value::UP>(builder->build(std::move(builder)));
    // num_children does not include the "input" param
    state.pop_n_push(param.num_children + 1, result);
};

struct SelectGenericPeekOp {
    template <typename ICT, typename OCT> static auto invoke() {
        return my_generic_peek_op<ICT,OCT>;
    }
};

//-----------------------------------------------------------------------------

} // namespace <unnamed>

Instruction
GenericPeek::make_instruction(const ValueType &input_type,
                              const ValueType &res_type,
                              const SpecMap &spec,
                              const ValueBuilderFactory &factory,
                              Stash &stash)
{
    const auto &param = stash.create<PeekParam>(input_type, res_type, spec, factory);
    auto fun = typify_invoke<2,TypifyCellType,SelectGenericPeekOp>(input_type.cell_type(), res_type.cell_type());
    return Instruction(fun, wrap_param<PeekParam>(param));
}

} // namespace
