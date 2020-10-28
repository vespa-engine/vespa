// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_peek.h"
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/eval/eval/array_array_map.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <cassert>

using namespace vespalib::eval::tensor_function;

namespace vespalib::eval::instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

namespace {

enum class Source { COPY_INPUT, FROM_CHILD, VERBATIM };

struct DensePlan {
    std::vector<std::pair<Source,size_t>> where;
    std::vector<size_t> loop_cnt;
    std::vector<size_t> stride;

    DensePlan(const ValueType &input_type)
        : where(), loop_cnt(), stride()
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

template <typename Getter>
struct DenseLoop {
    std::vector<size_t> loop_cnt;
    std::vector<size_t> in_stride;
    std::vector<size_t> in_offset;
    bool valid_indexes = true;
    DenseLoop(const DensePlan &spec, const Getter &get_child_value)
        : loop_cnt(spec.loop_cnt),
          in_stride(spec.stride),
          in_offset(loop_cnt.size())
    {
        for (size_t i = 0; i < loop_cnt.size(); ++i) {
            const auto & where = spec.where[i];
            switch (where.first) {
            case Source::COPY_INPUT:
                in_offset[i] = 0;
                break;
            case Source::VERBATIM:
                assert(where.second < loop_cnt[i]);
                in_offset[i] = where.second;
                loop_cnt[i] = 1;
                break;
            case Source::FROM_CHILD:
                in_offset[i] = get_child_value(where.second);
                if (in_offset[i] >= loop_cnt[i]) {
                    valid_indexes = false;
                }
                loop_cnt[i] = 1;
                break;
            }
        }
    }
    ~DenseLoop();
    template<typename F>
    void run_nested(size_t idx,
                    const size_t *loop,
                    const size_t *stride,
                    const size_t *offset,
                    size_t levels,
                    const F &f)
    {
        if (levels == 0) {
            f(idx);
        } else {
            idx += (*offset) * (*stride);
            for (size_t i = 0; i < *loop; ++i, idx += *stride) {
                run_nested(idx, loop + 1, stride + 1, offset + 1, levels - 1, f);
            }
        }
    }
    template<typename F> void execute(const F &f) {
        run_nested(0, &loop_cnt[0], &in_stride[0], &in_offset[0], loop_cnt.size(), f);
    }
};

template <typename Getter>
DenseLoop<Getter>::~DenseLoop() = default;

using SparsePlan = std::vector<std::pair<Source, TensorSpec::Label>>;

struct PeekParam {
    const ValueType res_type;
    size_t out_mapped_dims;
    size_t out_dense_size;
    const ValueType input_type;
    size_t in_dense_size;
    DensePlan dense_plan;
    SparsePlan sparse_plan;
    std::vector<size_t> view_dims;
    size_t num_children;
    const ValueBuilderFactory &factory;

    static constexpr size_t npos = -1;

    PeekParam(const ValueType &input_type_in,
              const ValueType &res_type_in,
              const GenericPeek::SpecMap &spec_in,
              const ValueBuilderFactory &factory_in)
        : res_type(res_type_in),
          out_mapped_dims(res_type.count_mapped_dimensions()),
          out_dense_size(res_type.dense_subspace_size()),
          input_type(input_type_in),
          in_dense_size(input_type.dense_subspace_size()),
          dense_plan(input_type),
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
            size_t n = sparse_plan.size();
            sparse_plan.emplace_back(Source::COPY_INPUT, n);
        } else {
            dense_plan.where.emplace_back(Source::COPY_INPUT, -1);
        }
    }

    void process_spec(const GenericPeek::SpecMap &spec)
    {
        const auto & input_dims = input_type.dimensions();
        const auto & res_dims = res_type.dimensions();
        auto input_pos = input_dims.begin();
        auto res_pos = res_dims.begin();
        for (auto spec_pos = spec.begin(); spec_pos != spec.end(); ++spec_pos) {
            const auto & dim_name = spec_pos->first;
            assert(input_pos != input_dims.end());
            while (input_pos->name < dim_name) {
                copy_dim(*input_pos, *res_pos);
                ++res_pos;
                ++input_pos;
                assert(input_pos != input_dims.end());
            }
            assert(input_pos->name == dim_name);
            const auto & label_or_idx = spec_pos->second;
            if (std::holds_alternative<size_t>(label_or_idx)) {
                auto child_idx = std::get<size_t>(label_or_idx);
                if (input_pos->is_mapped()) {
                    view_dims.push_back(sparse_plan.size());
                    sparse_plan.emplace_back(Source::FROM_CHILD, child_idx);
                } else {
                    dense_plan.where.emplace_back(Source::FROM_CHILD, child_idx);
                }
                ++num_children;
            } else {
                const auto &label = std::get<TensorSpec::Label>(label_or_idx);
                assert(label.is_mapped() == input_pos->is_mapped());
                if (input_pos->is_mapped()) {
                    view_dims.push_back(sparse_plan.size());
                    sparse_plan.emplace_back(Source::VERBATIM, label);
                } else {
                    dense_plan.where.emplace_back(Source::VERBATIM, label.index);
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
        assert(dense_plan.where.size() == dense_plan.loop_cnt.size());
        assert(dense_plan.where.size() == dense_plan.stride.size());
    }

};

template <typename ICT, typename OCT>
void my_generic_peek_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<PeekParam>(param_in);
    std::vector<vespalib::string> sparse_addr;
    const Value & input_value = state.peek(param.num_children);
    const size_t last_child = param.num_children - 1;
    auto get_child_value = [&] (size_t child_idx) {
        size_t stack_idx = last_child - child_idx;
        return int64_t(state.peek(stack_idx).as_double());
    };
    DenseLoop dense_loop{param.dense_plan, get_child_value};

    auto input_cells = input_value.cells().typify<ICT>();
    size_t bad_guess = 1;
    auto builder = param.factory.create_value_builder<OCT>(param.res_type,
                                                           param.out_mapped_dims,
                                                           param.out_dense_size,
                                                           bad_guess);
    for (const auto &where : param.sparse_plan) {
        switch (where.first) {
        case Source::VERBATIM:
            sparse_addr.push_back(where.second.name);
            break;
        case Source::FROM_CHILD:
            sparse_addr.push_back(
                    vespalib::make_string("%" PRId64, get_child_value(where.second.index)));
            break;
        case Source::COPY_INPUT:
            break;
        }
    }
    bool need_fill = (param.out_mapped_dims == 0);
    if (dense_loop.valid_indexes) {
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
            dense_loop.execute([&](size_t idx) { *dst++ = input_cells[offset + idx]; });
            need_fill = false;
        }
    }
    if (need_fill) {
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
