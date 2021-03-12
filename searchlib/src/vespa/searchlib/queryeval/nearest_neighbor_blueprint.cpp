// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "emptysearch.h"
#include "nearest_neighbor_blueprint.h"
#include "nearest_neighbor_iterator.h"
#include "nns_index_iterator.h"
#include <vespa/eval/eval/dense_cells_value.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/tensor/distance_function_factory.h>
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.queryeval.nearest_neighbor_blueprint");

using vespalib::eval::DenseCellsValue;
using vespalib::eval::Value;

namespace search::queryeval {

namespace {

template<typename LCT, typename RCT>
void
convert_cells(std::unique_ptr<Value> &original, const vespalib::eval::ValueType &want_type)
{
    if constexpr (std::is_same<LCT,RCT>::value) {
        return;
    } else {
        auto old_cells = original->cells().typify<LCT>();
        std::vector<RCT> new_cells;
        new_cells.reserve(old_cells.size());
        for (LCT value : old_cells) {
            RCT conv(value);
            new_cells.push_back(conv);
        }
        original = std::make_unique<DenseCellsValue<RCT>>(want_type, std::move(new_cells));
    }
}

struct ConvertCellsSelector
{
    template <typename LCT, typename RCT>
    static auto invoke() { return convert_cells<LCT, RCT>; }
};

} // namespace <unnamed>

NearestNeighborBlueprint::NearestNeighborBlueprint(const queryeval::FieldSpec& field,
                                                   const tensor::DenseTensorAttribute& attr_tensor,
                                                   std::unique_ptr<Value> query_tensor,
                                                   uint32_t target_num_hits, bool approximate, uint32_t explore_additional_hits,
                                                   double distance_threshold, double brute_force_limit)
    : ComplexLeafBlueprint(field),
      _attr_tensor(attr_tensor),
      _query_tensor(std::move(query_tensor)),
      _target_num_hits(target_num_hits),
      _approximate(approximate),
      _explore_additional_hits(explore_additional_hits),
      _distance_threshold(std::numeric_limits<double>::max()),
      _brute_force_limit(brute_force_limit),
      _fallback_dist_fun(),
      _distance_heap(target_num_hits),
      _found_hits(),
      _global_filter(GlobalFilter::create())
{
    auto lct = _query_tensor->cells().type;
    auto rct = _attr_tensor.getTensorType().cell_type();
    using MyTypify = vespalib::eval::TypifyCellType;
    auto fixup_fun = vespalib::typify_invoke<2,MyTypify,ConvertCellsSelector>(lct, rct);
    fixup_fun(_query_tensor, _attr_tensor.getTensorType());
    _fallback_dist_fun = search::tensor::make_distance_function(_attr_tensor.getConfig().distance_metric(), rct);
    _dist_fun = _fallback_dist_fun.get();
    assert(_dist_fun);
    auto nns_index = _attr_tensor.nearest_neighbor_index();
    if (nns_index) {
        _dist_fun = nns_index->distance_function();
        assert(_dist_fun);
    }
    if (distance_threshold < std::numeric_limits<double>::max()) {
        _distance_threshold = _dist_fun->convert_threshold(distance_threshold);
        _distance_heap.set_distance_threshold(_distance_threshold);
    }
    uint32_t est_hits = _attr_tensor.getNumDocs();
    setEstimate(HitEstimate(est_hits, false));
    set_want_global_filter(true);
}

NearestNeighborBlueprint::~NearestNeighborBlueprint() = default;

void
NearestNeighborBlueprint::set_global_filter(const GlobalFilter &global_filter)
{
    _global_filter = global_filter.shared_from_this();
    auto nns_index = _attr_tensor.nearest_neighbor_index();
    LOG(debug, "set_global_filter with: %s / %s / %s",
        (_approximate ? "approximate" : "exact"),
        (nns_index ? "nns_index" : "no_index"),
        (_global_filter->has_filter() ? "has_filter" : "no_filter"));
    if (_approximate && nns_index) {
        uint32_t est_hits = _attr_tensor.getNumDocs();
        if (_global_filter->has_filter()) {
            uint32_t max_hits = _global_filter->filter()->countTrueBits();
            LOG(debug, "set_global_filter getNumDocs: %u / max_hits %u", est_hits, max_hits);
            double max_hit_ratio = static_cast<double>(max_hits) / est_hits;
            if (max_hit_ratio < _brute_force_limit) {
                _approximate = false;
                LOG(debug, "too many hits filtered out, using brute force implementation");
            } else {
                est_hits = std::min(est_hits, max_hits);
            }
        }
        if (_approximate) {
            est_hits = std::min(est_hits, _target_num_hits);
            setEstimate(HitEstimate(est_hits, false));
            perform_top_k();
            LOG(debug, "perform_top_k found %zu hits", _found_hits.size());
        }
    }
}

void
NearestNeighborBlueprint::perform_top_k()
{
    auto nns_index = _attr_tensor.nearest_neighbor_index();
    if (_approximate && nns_index) {
        auto lhs_type = _query_tensor->type();
        auto rhs_type = _attr_tensor.getTensorType();
        // different cell types should be converted already
        if (lhs_type == rhs_type) {
            auto lhs = _query_tensor->cells();
            uint32_t k = _target_num_hits;
            if (_global_filter->has_filter()) {
                auto filter = _global_filter->filter();
                _found_hits = nns_index->find_top_k_with_filter(k, lhs, *filter, k + _explore_additional_hits, _distance_threshold);
            } else {
                _found_hits = nns_index->find_top_k(k, lhs, k + _explore_additional_hits, _distance_threshold);
            }
        }
    }
}

std::unique_ptr<SearchIterator>
NearestNeighborBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray& tfmda, bool strict) const
{
    assert(tfmda.size() == 1);
    fef::TermFieldMatchData &tfmd = *tfmda[0]; // always search in only one field
    if (! _found_hits.empty()) {
        return NnsIndexIterator::create(tfmd, _found_hits, _dist_fun);
    }
    const Value &qT = *_query_tensor;
    return NearestNeighborIterator::create(strict, tfmd, qT, _attr_tensor,
                                           _distance_heap, _global_filter->filter(), _dist_fun);
}

void
NearestNeighborBlueprint::visitMembers(vespalib::ObjectVisitor& visitor) const
{
    ComplexLeafBlueprint::visitMembers(visitor);
    visitor.visitString("attribute_tensor", _attr_tensor.getTensorType().to_spec());
    visitor.visitString("query_tensor", _query_tensor->type().to_spec());
    visitor.visitInt("target_num_hits", _target_num_hits);
    visitor.visitBool("approximate", _approximate);
    visitor.visitInt("explore_additional_hits", _explore_additional_hits);
}

bool
NearestNeighborBlueprint::always_needs_unpack() const
{
    return true;
}

}
