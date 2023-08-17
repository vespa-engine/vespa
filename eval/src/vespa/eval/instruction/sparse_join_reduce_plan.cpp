// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_join_reduce_plan.h"
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/visit_ranges.h>
#include <cassert>

namespace vespalib::eval::instruction {

namespace {

using Dim = ValueType::Dimension;
using Dims = std::vector<ValueType::Dimension>;

void visit(auto &v, const Dims &a, const Dims &b) {
    visit_ranges(v, a.begin(), a.end(), b.begin(), b.end(),
                 [](const auto &x, const auto &y){ return (x.name < y.name); });
}

Dims merge(const Dims &first, const Dims &second) {
    Dims result;
    auto visitor = overload {
        [&result](visit_ranges_either, const Dim &dim) { result.push_back(dim); },
        [&result](visit_ranges_both, const Dim &dim, const Dim &) { result.push_back(dim); }
    };
    visit(visitor, first, second);
    return result;
}

size_t count_only_in_second(const Dims &first, const Dims &second) {
    size_t result = 0;
    auto visitor = overload {
        [](visit_ranges_first, const Dim &) {},
        [&result](visit_ranges_second, const Dim &) { ++result; },
        [](visit_ranges_both, const Dim &, const Dim &) {}
    };
    visit(visitor, first, second);
    return result;
}

struct SparseJoinReduceState {
    SmallVector<string_id,4>        addr_space;
    SmallVector<string_id*,4>       a_addr;
    SmallVector<const string_id*,4> overlap;
    SmallVector<string_id*,4>       b_only;
    SmallVector<size_t,4>           b_view;
    size_t                          a_subspace;
    size_t                          b_subspace;
    uint32_t                        res_dims;
    SparseJoinReduceState(const bool *in_a, const bool *in_b, const bool *in_res, size_t dims)
      : addr_space(dims), a_addr(), overlap(), b_only(), b_view(), a_subspace(), b_subspace(), res_dims(0)
    {
        size_t b_idx = 0;
        uint32_t dims_end = addr_space.size();
        for (size_t i = 0; i < dims; ++i) {
            string_id *id = in_res[i] ? &addr_space[res_dims++] : &addr_space[--dims_end];
            if (in_a[i]) {
                a_addr.push_back(id);
                if (in_b[i]) {
                    overlap.push_back(id);
                    b_view.push_back(b_idx++);
                }
            } else if (in_b[i]) {
                b_only.push_back(id);
                ++b_idx;
            }
        }
        // Kept dimensions are allocated from the start and dropped
        // dimensions are allocated from the end. Make sure they
        // combine to exactly cover the complete address space.
        assert(res_dims == dims_end);
    }
    ~SparseJoinReduceState();
};
SparseJoinReduceState::~SparseJoinReduceState() = default;

void execute_plan(const Value::Index &a, const Value::Index &b,
                  const bool *in_a, const bool *in_b, const bool *in_res,
                  size_t dims, auto &&f)
{
    SparseJoinReduceState state(in_a, in_b, in_res, dims);
    auto outer = a.create_view({});
    auto inner = b.create_view(state.b_view);
    outer->lookup({});
    while (outer->next_result(state.a_addr, state.a_subspace)) {
        inner->lookup(state.overlap);
        while (inner->next_result(state.b_only, state.b_subspace)) {
            f(state.a_subspace, state.b_subspace, ConstArrayRef<string_id>{state.addr_space.begin(), state.res_dims});
        }
    }
}

using est_fun = SparseJoinReducePlan::est_fun_t;
using est_filter = std::function<bool(bool, bool, bool)>;

struct Est {
    est_filter filter;
    est_fun estimate;
    bool can_use;
    Est(est_filter filter_in, est_fun estimate_in)
      : filter(filter_in), estimate(estimate_in), can_use(true) {}
    ~Est();
};
Est::~Est() = default;

size_t est_1(size_t, size_t) noexcept { return 1; }
size_t est_a_or_0(size_t a, size_t b) noexcept { return (b == 0) ? 0 : a; }
size_t est_b_or_0(size_t a, size_t b) noexcept { return (a == 0) ? 0 : b; }
size_t est_min(size_t a, size_t b) noexcept { return std::min(a, b); }
size_t est_mul(size_t a, size_t b) noexcept { return (a * b); }

bool no_dims(bool, bool, bool) noexcept { return false; }
bool reduce_all(bool, bool, bool keep) noexcept { return !keep; }
bool keep_a_reduce_b(bool a, bool b, bool keep) noexcept {
    if (keep) {
        return (a && !b);
    } else {
        return (!a && b);
    }
}
bool keep_b_reduce_a(bool a, bool b, bool keep) noexcept { return keep_a_reduce_b(b, a, keep); }
bool full_overlap(bool a, bool b, bool) noexcept { return (a == b); }
bool no_overlap_keep_all(bool a, bool b, bool keep) noexcept { return keep && (a != b); }

std::vector<Est> make_est_list() {
    return {
        { no_dims, est_1 },
        { reduce_all, est_1 },
        { keep_a_reduce_b, est_a_or_0 },
        { keep_b_reduce_a, est_b_or_0 },
        { full_overlap, est_min },
        { no_overlap_keep_all, est_mul }
    };
}

void update_est_list(std::vector<Est> &est_list, bool in_lhs, bool in_rhs, bool in_res) {
    for (Est &est: est_list) {
        if (est.can_use && !est.filter(in_lhs, in_rhs, in_res)) {
            est.can_use = false;
        }
    }
}

est_fun select_estimate(const std::vector<Est> &est_list) {
    for (const Est &est: est_list) {
        if (est.can_use) {
            return est.estimate;
        }
    }
    return est_min;
}

} // <unnamed>

SparseJoinReducePlan::SparseJoinReducePlan(const ValueType &lhs, const ValueType &rhs, const ValueType &res)
  : _in_lhs(), _in_rhs(), _in_res(), _estimate()
{
    auto dims = merge(lhs.mapped_dimensions(), rhs.mapped_dimensions());
    assert(count_only_in_second(dims, res.mapped_dimensions()) == 0); 
    auto est_list = make_est_list();
    for (const auto &dim: dims) {
        _in_lhs.push_back(lhs.has_dimension(dim.name));
        _in_rhs.push_back(rhs.has_dimension(dim.name));
        _in_res.push_back(res.has_dimension(dim.name));
        update_est_list(est_list, _in_lhs.back(), _in_rhs.back(), _in_res.back());
    }
    _estimate = select_estimate(est_list);
    assert(bool(_estimate));
}

SparseJoinReducePlan::~SparseJoinReducePlan() = default;

void
SparseJoinReducePlan::execute(const Value::Index &lhs, const Value::Index &rhs, F f) const {
    if (rhs.size() < lhs.size()) {
        auto swap = [&](auto a, auto b, auto addr) { f(b, a, addr); };
        execute_plan(rhs, lhs, _in_rhs.data(), _in_lhs.data(), _in_res.data(), _in_res.size(), swap);
    } else {
        execute_plan(lhs, rhs, _in_lhs.data(), _in_rhs.data(), _in_res.data(), _in_res.size(), f);
    }
}

} // namespace
