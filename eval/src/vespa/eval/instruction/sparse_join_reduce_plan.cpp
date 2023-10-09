// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

size_t est_1(size_t, size_t) noexcept { return 1; }
size_t est_a_or_0(size_t a, size_t b) noexcept { return (b == 0) ? 0 : a; }
size_t est_b_or_0(size_t a, size_t b) noexcept { return (a == 0) ? 0 : b; }
size_t est_min(size_t a, size_t b) noexcept { return std::min(a, b); }
size_t est_mul(size_t a, size_t b) noexcept { return (a * b); }

bool reduce_all(bool, bool, bool keep) noexcept { return !keep; }
bool keep_a_reduce_b(bool a, bool b, bool keep) noexcept { return (keep == a) && (keep != b); }
bool keep_b_reduce_a(bool a, bool b, bool keep) noexcept { return (keep == b) && (keep != a); }
bool no_overlap_keep_all(bool a, bool b, bool keep) noexcept { return keep && (a != b); }

} // <unnamed>

SparseJoinReducePlan::est_fun_t
SparseJoinReducePlan::select_estimate() const
{
    if (check(reduce_all))          return est_1;
    if (check(no_overlap_keep_all)) return est_mul;
    if (check(keep_a_reduce_b))     return est_a_or_0;
    if (check(keep_b_reduce_a))     return est_b_or_0;
    return est_min;
}

SparseJoinReducePlan::State::State(const bool *in_a, const bool *in_b, const bool *in_res, size_t dims)
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

SparseJoinReducePlan::State::~State() = default;

SparseJoinReducePlan::SparseJoinReducePlan(const ValueType &lhs, const ValueType &rhs, const ValueType &res)
  : _in_lhs(), _in_rhs(), _in_res(), _res_dims(res.count_mapped_dimensions()), _estimate()
{
    auto dims = merge(lhs.mapped_dimensions(), rhs.mapped_dimensions());
    assert(count_only_in_second(dims, res.mapped_dimensions()) == 0); 
    for (const auto &dim: dims) {
        _in_lhs.push_back(lhs.has_dimension(dim.name));
        _in_rhs.push_back(rhs.has_dimension(dim.name));
        _in_res.push_back(res.has_dimension(dim.name));
    }
    _estimate = select_estimate();
}

SparseJoinReducePlan::~SparseJoinReducePlan() = default;

bool
SparseJoinReducePlan::maybe_forward_lhs_index() const
{
    return check(keep_a_reduce_b);
}

bool
SparseJoinReducePlan::maybe_forward_rhs_index() const
{
    return check(keep_b_reduce_a);
}

} // namespace
