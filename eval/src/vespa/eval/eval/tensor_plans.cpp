// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_plans.h"
#include <vespa/vespalib/util/visit_ranges.h>
#include <vespa/vespalib/util/overload.h>
#include <cassert>

namespace vespalib::eval {

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

}
