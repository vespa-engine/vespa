// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_join_reduce_plan.h"
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

struct Strides {
    size_t lhs;
    size_t rhs;
    size_t res;
    Strides() noexcept : lhs(0), rhs(0), res(0) {}
    Strides(size_t lhs_in, size_t rhs_in, size_t res_in) noexcept
      : lhs(lhs_in), rhs(rhs_in), res(res_in) {}
    bool can_combine_with(const Strides &prev) const noexcept {
        return ((lhs > 0) == (prev.lhs > 0)) &&
               ((rhs > 0) == (prev.rhs > 0)) &&
               ((res > 0) == (prev.res > 0));
    }
};

} // <unnamed>

DenseJoinReducePlan::DenseJoinReducePlan(const ValueType &lhs, const ValueType &rhs, const ValueType &res)
  : lhs_size(lhs.dense_subspace_size()), rhs_size(rhs.dense_subspace_size()), res_size(res.dense_subspace_size()),
    loop_cnt(), lhs_stride(), rhs_stride(), res_stride()
{
    auto dims = merge(lhs.nontrivial_indexed_dimensions(), rhs.nontrivial_indexed_dimensions());
    assert(count_only_in_second(dims, res.nontrivial_indexed_dimensions()) == 0); 
    Strides prev_strides;
    for (const auto &dim: dims) {
        Strides strides(lhs.stride_of(dim.name), rhs.stride_of(dim.name), res.stride_of(dim.name));
        if (strides.can_combine_with(prev_strides)) {
            assert(!loop_cnt.empty());
            loop_cnt.back() *= dim.size;
            lhs_stride.back() = strides.lhs;
            rhs_stride.back() = strides.rhs;
            res_stride.back() = strides.res;
        } else {
            loop_cnt.push_back(dim.size);
            lhs_stride.push_back(strides.lhs);
            rhs_stride.push_back(strides.rhs);
            res_stride.push_back(strides.res);
        }
        prev_strides = strides;
    }
}

DenseJoinReducePlan::~DenseJoinReducePlan() = default;

bool
DenseJoinReducePlan::distinct_result() const
{
    for (size_t stride: res_stride) {
        if (stride == 0) {
            return false;
        }
    }
    return true;
}

} // namespace
