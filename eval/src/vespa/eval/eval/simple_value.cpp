// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_value.h"
#include "tensor_spec.h"
#include "inline_operation.h"
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/util/visit_ranges.h>
#include <vespa/vespalib/util/overload.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.simple_value");

namespace vespalib::eval {

//-----------------------------------------------------------------------------

namespace {

struct CreateSimpleValueBuilderBase {
    template <typename T> static std::unique_ptr<ValueBuilderBase> invoke(const ValueType &type,
            size_t num_mapped_dims_in, size_t subspace_size_in)
    {
        assert(check_cell_type<T>(type.cell_type()));
        return std::make_unique<SimpleValueT<T>>(type, num_mapped_dims_in, subspace_size_in);
    }
};

struct CreateValueFromTensorSpec {
    template <typename T> static std::unique_ptr<Value> invoke(const ValueType &type, const TensorSpec &spec, const ValueBuilderFactory &factory) {
        using SparseKey = std::vector<vespalib::stringref>;
        using DenseMap = std::map<size_t,T>;
        std::map<SparseKey,DenseMap> map;
        for (const auto &entry: spec.cells()) {
            SparseKey sparse_key;
            size_t dense_key = 0;
            for (const auto &dim: type.dimensions()) {
                auto pos = entry.first.find(dim.name);
                assert(pos != entry.first.end());
                assert(pos->second.is_mapped() == dim.is_mapped());
                if (dim.is_mapped()) {
                    sparse_key.emplace_back(pos->second.name);
                } else {
                    dense_key = (dense_key * dim.size) + pos->second.index;
                }
            }
            map[sparse_key][dense_key] = entry.second;
        }
        auto builder = factory.create_value_builder<T>(type, type.count_mapped_dimensions(), type.dense_subspace_size(), map.size());
        for (const auto &entry: map) {
            auto subspace = builder->add_subspace(entry.first);
            for (const auto &cell: entry.second) {
                subspace[cell.first] = cell.second;
            }
        }
        return builder->build(std::move(builder));
    }
};

struct CreateTensorSpecFromValue {
    template <typename T> static TensorSpec invoke(const Value &value) {
        auto cells = value.cells().typify<T>();
        TensorSpec spec(value.type().to_spec());
        size_t subspace_id = 0;
        size_t subspace_size = value.type().dense_subspace_size();
        std::vector<vespalib::stringref> labels(value.type().count_mapped_dimensions());
        std::vector<vespalib::stringref*> label_refs;
        for (auto &label: labels) {
            label_refs.push_back(&label);
        }
        auto view = value.index().create_view({});
        view->lookup({});
        while (view->next_result(label_refs, subspace_id)) {
            size_t label_idx = 0;
            TensorSpec::Address addr;
            for (const auto &dim: value.type().dimensions()) {
                if (dim.is_mapped()) {
                    addr.emplace(dim.name, labels[label_idx++]);
                }
            }
            for (size_t i = 0; i < subspace_size; ++i) {
                size_t dense_key = i;
                for (auto dim = value.type().dimensions().rbegin();
                     dim != value.type().dimensions().rend(); ++dim)
                {
                    if (dim->is_indexed()) {
                        size_t label = dense_key % dim->size;
                        addr.emplace(dim->name, label).first->second = TensorSpec::Label(label);
                        dense_key /= dim->size;
                    }
                }
                spec.add(addr, cells[(subspace_size * subspace_id) + i]);
            }
        }
        return spec;
    }
};

class SimpleValueView : public Value::Index::View {
private:
    using Addr = std::vector<vespalib::string>;
    using Map = std::map<Addr,size_t>;
    using Itr = Map::const_iterator;

    const Map          &_index;
    size_t              _num_mapped_dims;
    std::vector<size_t> _match_dims;
    std::vector<size_t> _extract_dims;
    Addr                _query;
    Itr                 _pos;

    bool is_direct_lookup() const { return (_match_dims.size() == _num_mapped_dims); }
    bool is_match() const {
        assert(_pos->first.size() == _num_mapped_dims);
        for (size_t idx: _match_dims) {
            if (_query[idx] != _pos->first[idx]) {
                return false;
            }
        }
        return true;
    }

public:
    SimpleValueView(const Map &index, const std::vector<size_t> &match_dims, size_t num_mapped_dims)
        : _index(index), _num_mapped_dims(num_mapped_dims), _match_dims(match_dims), _extract_dims(), _query(num_mapped_dims, ""), _pos(_index.end())
    {
        auto pos = _match_dims.begin();
        for (size_t i = 0; i < _num_mapped_dims; ++i) {
            if ((pos == _match_dims.end()) || (*pos != i)) {
                _extract_dims.push_back(i);
            } else {
                ++pos;
            }
        }
        assert(pos == _match_dims.end());
        assert((_match_dims.size() + _extract_dims.size()) == _num_mapped_dims);
    }

    void lookup(const std::vector<const vespalib::stringref*> &addr) override {
        assert(addr.size() == _match_dims.size());
        for (size_t i = 0; i < _match_dims.size(); ++i) {
            _query[_match_dims[i]] = *addr[i];
        }
        if (is_direct_lookup()) {
            _pos = _index.find(_query);
        } else {
            _pos = _index.begin();
        }
    }

    bool next_result(const std::vector<vespalib::stringref*> &addr_out, size_t &idx_out) override {
        assert(addr_out.size() == _extract_dims.size());
        while (_pos != _index.end()) {
            if (is_match()) {
                for (size_t i = 0; i < _extract_dims.size(); ++i) {
                    *addr_out[i] = _pos->first[_extract_dims[i]];
                }
                idx_out = _pos->second;
                if (is_direct_lookup()) {
                    _pos = _index.end();
                } else {
                    ++_pos;
                }
                return true;
            }
            ++_pos;
        }
        return false;
    }
};

// Contains various state needed to perform the sparse part (all
// mapped dimensions) of the join operation. Performs swapping of
// sparse indexes to ensure that we look up entries from the smallest
// index in the largest index.
struct SparseJoinState {
    bool                                    swapped;
    const Value::Index                     &first_index;
    const Value::Index                     &second_index;
    const std::vector<size_t>              &second_view_dims;
    std::vector<vespalib::stringref>        full_address;
    std::vector<vespalib::stringref*>       first_address;
    std::vector<const vespalib::stringref*> address_overlap;
    std::vector<vespalib::stringref*>       second_only_address;
    size_t                                  lhs_subspace;
    size_t                                  rhs_subspace;
    size_t                                 &first_subspace;
    size_t                                 &second_subspace;

    SparseJoinState(const SparseJoinPlan &plan, const Value::Index &lhs, const Value::Index &rhs)
        : swapped(rhs.size() < lhs.size()),
          first_index(swapped ? rhs : lhs), second_index(swapped ? lhs : rhs),
          second_view_dims(swapped ? plan.lhs_overlap : plan.rhs_overlap),
          full_address(plan.sources.size()),
          first_address(), address_overlap(), second_only_address(),
          lhs_subspace(), rhs_subspace(),
          first_subspace(swapped ? rhs_subspace : lhs_subspace),
          second_subspace(swapped ? lhs_subspace : rhs_subspace)
    {
        auto first_source = swapped ? SparseJoinPlan::Source::RHS : SparseJoinPlan::Source::LHS;
        for (size_t i = 0; i < full_address.size(); ++i) {
            if (plan.sources[i] == SparseJoinPlan::Source::BOTH) {
                first_address.push_back(&full_address[i]);
                address_overlap.push_back(&full_address[i]);
            } else if (plan.sources[i] == first_source) {
                first_address.push_back(&full_address[i]);
            } else {
                second_only_address.push_back(&full_address[i]);
            }
        }
    }
    ~SparseJoinState();
};
SparseJoinState::~SparseJoinState() = default;

// Treats all values as mixed tensors. Needs output cell type as well
// as input cell types since output cell type cannot always be
// directly inferred.
struct GenericJoin {
    template <typename LCT, typename RCT, typename OCT, typename Fun> static std::unique_ptr<Value>
    invoke(const Value &lhs, const Value &rhs, join_fun_t function,
           const SparseJoinPlan &sparse_plan, const DenseJoinPlan &dense_plan,
           const ValueType &res_type, const ValueBuilderFactory &factory)
    {
        Fun fun(function);
        auto lhs_cells = lhs.cells().typify<LCT>();
        auto rhs_cells = rhs.cells().typify<RCT>();
        SparseJoinState state(sparse_plan, lhs.index(), rhs.index());
        auto builder = factory.create_value_builder<OCT>(res_type, sparse_plan.sources.size(), dense_plan.out_size, state.first_index.size());
        auto outer = state.first_index.create_view({});
        auto inner = state.second_index.create_view(state.second_view_dims);
        outer->lookup({});
        while (outer->next_result(state.first_address, state.first_subspace)) {
            inner->lookup(state.address_overlap);
            while (inner->next_result(state.second_only_address, state.second_subspace)) {
                OCT *dst = builder->add_subspace(state.full_address).begin();
                auto join_cells = [&](size_t lhs_idx, size_t rhs_idx) { *dst++ = fun(lhs_cells[lhs_idx], rhs_cells[rhs_idx]); };
                dense_plan.execute(dense_plan.lhs_size * state.lhs_subspace, dense_plan.rhs_size * state.rhs_subspace, join_cells);
            }
        }
        return builder->build(std::move(builder));
    }
};

} // namespace <unnamed>

//-----------------------------------------------------------------------------

void
SimpleValue::add_mapping(const std::vector<vespalib::stringref> &addr)
{
    size_t id = _index.size();
    std::vector<vespalib::string> my_addr;
    for (const auto &label: addr) {
        my_addr.push_back(label);
    }
    auto res = _index.emplace(std::move(my_addr), id);
    assert(res.second);
}

SimpleValue::SimpleValue(const ValueType &type, size_t num_mapped_dims_in, size_t subspace_size_in)
    : _type(type),
      _num_mapped_dims(num_mapped_dims_in),
      _subspace_size(subspace_size_in),
      _index()
{
    assert(_type.count_mapped_dimensions() == _num_mapped_dims);
    assert(_type.dense_subspace_size() == _subspace_size);
}

SimpleValue::~SimpleValue() = default;

std::unique_ptr<Value::Index::View>
SimpleValue::create_view(const std::vector<size_t> &dims) const
{
    return std::make_unique<SimpleValueView>(_index, dims, _num_mapped_dims);
}

//-----------------------------------------------------------------------------

template <typename T>
SimpleValueT<T>::SimpleValueT(const ValueType &type, size_t num_mapped_dims_in, size_t subspace_size_in)
    : SimpleValue(type, num_mapped_dims_in, subspace_size_in),
      _cells()
{
}

template <typename T>
SimpleValueT<T>::~SimpleValueT() = default;

template <typename T>
ArrayRef<T>
SimpleValueT<T>::add_subspace(const std::vector<vespalib::stringref> &addr)
{
    size_t old_size = _cells.size();
    assert(old_size == (index().size() * subspace_size()));
    add_mapping(addr);
    _cells.resize(old_size + subspace_size());
    return ArrayRef<T>(&_cells[old_size], subspace_size());
}

//-----------------------------------------------------------------------------

std::unique_ptr<ValueBuilderBase>
SimpleValueBuilderFactory::create_value_builder_base(const ValueType &type,
                                                     size_t num_mapped_dims_in, size_t subspace_size_in, size_t) const
{
    return typify_invoke<1,TypifyCellType,CreateSimpleValueBuilderBase>(type.cell_type(), type, num_mapped_dims_in, subspace_size_in);
}

//-----------------------------------------------------------------------------

DenseJoinPlan::DenseJoinPlan(const ValueType &lhs_type, const ValueType &rhs_type)
    : lhs_size(1), rhs_size(1), out_size(1), loop_cnt(), lhs_stride(), rhs_stride()
{
    enum class Case { NONE, LHS, RHS, BOTH };
    Case prev_case = Case::NONE;
    auto update_plan = [&](Case my_case, size_t my_size, size_t in_lhs, size_t in_rhs) {
        if (my_case == prev_case) {
            assert(!loop_cnt.empty());
            loop_cnt.back() *= my_size;
        } else {
            loop_cnt.push_back(my_size);
            lhs_stride.push_back(in_lhs);
            rhs_stride.push_back(in_rhs);
            prev_case = my_case;
        }
    };
    auto visitor = overload
                   {
                       [&](visit_ranges_first, const auto &a) { update_plan(Case::LHS, a.size, 1, 0); },
                       [&](visit_ranges_second, const auto &b) { update_plan(Case::RHS, b.size, 0, 1); },
                       [&](visit_ranges_both, const auto &a, const auto &) { update_plan(Case::BOTH, a.size, 1, 1); }
                   };
    auto lhs_dims = lhs_type.nontrivial_indexed_dimensions();
    auto rhs_dims = rhs_type.nontrivial_indexed_dimensions();
    visit_ranges(visitor, lhs_dims.begin(), lhs_dims.end(), rhs_dims.begin(), rhs_dims.end(),
                 [](const auto &a, const auto &b){ return (a.name < b.name); });
    for (size_t i = loop_cnt.size(); i-- > 0; ) {
        out_size *= loop_cnt[i];
        if (lhs_stride[i] != 0) {
            lhs_stride[i] = lhs_size;
            lhs_size *= loop_cnt[i];
        }
        if (rhs_stride[i] != 0) {
            rhs_stride[i] = rhs_size;
            rhs_size *= loop_cnt[i];
        }
    }
}

DenseJoinPlan::~DenseJoinPlan() = default;

//-----------------------------------------------------------------------------

SparseJoinPlan::SparseJoinPlan(const ValueType &lhs_type, const ValueType &rhs_type)
    : sources(), lhs_overlap(), rhs_overlap()
{
    size_t lhs_idx = 0;
    size_t rhs_idx = 0;
    auto visitor = overload
                   {
                       [&](visit_ranges_first, const auto &) {
                           sources.push_back(Source::LHS);
                           ++lhs_idx;
                       },
                       [&](visit_ranges_second, const auto &) {
                           sources.push_back(Source::RHS);
                           ++rhs_idx;
                       },
                       [&](visit_ranges_both, const auto &, const auto &) {
                           sources.push_back(Source::BOTH);
                           lhs_overlap.push_back(lhs_idx++);
                           rhs_overlap.push_back(rhs_idx++);
                       }
                   };
    auto lhs_dims = lhs_type.mapped_dimensions();
    auto rhs_dims = rhs_type.mapped_dimensions();
    visit_ranges(visitor, lhs_dims.begin(), lhs_dims.end(), rhs_dims.begin(), rhs_dims.end(),
                 [](const auto &a, const auto &b){ return (a.name < b.name); });
}

SparseJoinPlan::~SparseJoinPlan() = default;

//-----------------------------------------------------------------------------

using JoinTypify = TypifyValue<TypifyCellType,operation::TypifyOp2>;

std::unique_ptr<Value> new_join(const Value &a, const Value &b, join_fun_t function, const ValueBuilderFactory &factory) {
    auto res_type = ValueType::join(a.type(), b.type());
    assert(!res_type.is_error());
    SparseJoinPlan sparse_plan(a.type(), b.type());
    DenseJoinPlan dense_plan(a.type(), b.type());
    return typify_invoke<4,JoinTypify,GenericJoin>(a.type().cell_type(), b.type().cell_type(), res_type.cell_type(), function,
                                                   a, b, function, sparse_plan, dense_plan, res_type, factory);
}

//-----------------------------------------------------------------------------

std::unique_ptr<Value> value_from_spec(const TensorSpec &spec, const ValueBuilderFactory &factory) {
    ValueType type = ValueType::from_spec(spec.type());
    assert(!type.is_error());
    return typify_invoke<1,TypifyCellType,CreateValueFromTensorSpec>(type.cell_type(), type, spec, factory);
}

//-----------------------------------------------------------------------------

TensorSpec spec_from_value(const Value &value) {
    return typify_invoke<1,TypifyCellType,CreateTensorSpecFromValue>(value.type().cell_type(), value);
}

//-----------------------------------------------------------------------------

}
