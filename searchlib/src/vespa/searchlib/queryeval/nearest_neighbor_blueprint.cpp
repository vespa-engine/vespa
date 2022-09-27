// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nearest_neighbor_blueprint.h"
#include "emptysearch.h"
#include "nearest_neighbor_iterator.h"
#include "nns_index_iterator.h"
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/tensor/distance_function_factory.h>
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.queryeval.nearest_neighbor_blueprint");

using vespalib::eval::Value;

namespace search::queryeval {

namespace {

vespalib::string
to_string(NearestNeighborBlueprint::Algorithm algorithm)
{
    using NNBA = NearestNeighborBlueprint::Algorithm;
    switch (algorithm) {
        case NNBA::EXACT: return "exact";
        case NNBA::EXACT_FALLBACK: return "exact fallback";
        case NNBA::INDEX_TOP_K: return "index top k";
        case NNBA::INDEX_TOP_K_WITH_FILTER: return "index top k using filter";
    }
    return "unknown";
}

} // namespace <unnamed>

NearestNeighborBlueprint::NearestNeighborBlueprint(const queryeval::FieldSpec& field,
                                                   std::unique_ptr<search::tensor::DistanceCalculator> distance_calc,
                                                   uint32_t target_hits,
                                                   bool approximate,
                                                   uint32_t explore_additional_hits,
                                                   double distance_threshold,
                                                   double global_filter_lower_limit,
                                                   double global_filter_upper_limit)
    : ComplexLeafBlueprint(field),
      _distance_calc(std::move(distance_calc)),
      _attr_tensor(_distance_calc->attribute_tensor()),
      _query_tensor(_distance_calc->query_tensor()),
      _target_hits(target_hits),
      _adjusted_target_hits(target_hits),
      _approximate(approximate),
      _explore_additional_hits(explore_additional_hits),
      _distance_threshold(std::numeric_limits<double>::max()),
      _global_filter_lower_limit(global_filter_lower_limit),
      _global_filter_upper_limit(global_filter_upper_limit),
      _distance_heap(target_hits),
      _found_hits(),
      _algorithm(Algorithm::EXACT),
      _global_filter(GlobalFilter::create()),
      _global_filter_set(false),
      _global_filter_hits(),
      _global_filter_hit_ratio()
{
    if (distance_threshold < std::numeric_limits<double>::max()) {
        _distance_threshold = _distance_calc->function().convert_threshold(distance_threshold);
        _distance_heap.set_distance_threshold(_distance_threshold);
    }
    uint32_t est_hits = _attr_tensor.get_num_docs();
    setEstimate(HitEstimate(est_hits, false));
    auto nns_index = _attr_tensor.nearest_neighbor_index();
    set_want_global_filter(nns_index && _approximate);
}

NearestNeighborBlueprint::~NearestNeighborBlueprint() = default;

void
NearestNeighborBlueprint::set_global_filter(const GlobalFilter &global_filter, double estimated_hit_ratio)
{
    _global_filter = global_filter.shared_from_this();
    _global_filter_set = true;
    auto nns_index = _attr_tensor.nearest_neighbor_index();
    if (_approximate && nns_index) {
        uint32_t est_hits = _attr_tensor.get_num_docs();
        if (_global_filter->is_active()) { // pre-filtering case
            _global_filter_hits = _global_filter->count();
            _global_filter_hit_ratio = static_cast<double>(_global_filter_hits.value()) / est_hits;
            if (_global_filter_hit_ratio.value() < _global_filter_lower_limit) {
                _algorithm = Algorithm::EXACT_FALLBACK;
            } else {
                est_hits = std::min(est_hits, _global_filter_hits.value());
            }
        } else { // post-filtering case
            // The goal is to expose 'targetHits' hits to first-phase ranking.
            // We try to achieve this by adjusting targetHits based on the estimated hit ratio of the query before post-filtering.
            if (estimated_hit_ratio > 0.0) {
                _adjusted_target_hits = static_cast<double>(_target_hits) / estimated_hit_ratio;
            }
        }
        if (_algorithm != Algorithm::EXACT_FALLBACK) {
            est_hits = std::min(est_hits, _adjusted_target_hits);
            setEstimate(HitEstimate(est_hits, false));
            perform_top_k(nns_index);
        }
    }
}

void
NearestNeighborBlueprint::perform_top_k(const search::tensor::NearestNeighborIndex* nns_index)
{
    auto lhs = _query_tensor.cells();
    uint32_t k = _adjusted_target_hits;
    if (_global_filter->is_active()) {
        _found_hits = nns_index->find_top_k_with_filter(k, lhs, *_global_filter, k + _explore_additional_hits, _distance_threshold);
        _algorithm = Algorithm::INDEX_TOP_K_WITH_FILTER;
    } else {
        _found_hits = nns_index->find_top_k(k, lhs, k + _explore_additional_hits, _distance_threshold);
        _algorithm = Algorithm::INDEX_TOP_K;
    }
}

std::unique_ptr<SearchIterator>
NearestNeighborBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray& tfmda, bool strict) const
{
    assert(tfmda.size() == 1);
    fef::TermFieldMatchData &tfmd = *tfmda[0]; // always search in only one field
    switch (_algorithm) {
    case Algorithm::INDEX_TOP_K_WITH_FILTER:
    case Algorithm::INDEX_TOP_K:
        return NnsIndexIterator::create(tfmd, _found_hits, _distance_calc->function());
    default:
        ;
    }
    return NearestNeighborIterator::create(strict, tfmd, *_distance_calc,
                                           _distance_heap, *_global_filter);
}

void
NearestNeighborBlueprint::visitMembers(vespalib::ObjectVisitor& visitor) const
{
    ComplexLeafBlueprint::visitMembers(visitor);
    visitor.visitString("attribute_tensor", _attr_tensor.getTensorType().to_spec());
    visitor.visitString("query_tensor", _query_tensor.type().to_spec());
    visitor.visitInt("target_hits", _target_hits);
    visitor.visitInt("adjusted_target_hits", _adjusted_target_hits);
    visitor.visitInt("explore_additional_hits", _explore_additional_hits);
    visitor.visitBool("wanted_approximate", _approximate);
    visitor.visitBool("has_index", _attr_tensor.nearest_neighbor_index());
    visitor.visitString("algorithm", to_string(_algorithm));
    visitor.visitInt("top_k_hits", _found_hits.size());

    visitor.openStruct("global_filter", "GlobalFilter");
    visitor.visitBool("wanted", getState().want_global_filter());
    visitor.visitBool("set", _global_filter_set);
    visitor.visitBool("calculated", _global_filter->is_active());
    visitor.visitFloat("lower_limit", _global_filter_lower_limit);
    visitor.visitFloat("upper_limit", _global_filter_upper_limit);
    if (_global_filter_hits.has_value()) {
        visitor.visitInt("hits", _global_filter_hits.value());
    }
    if (_global_filter_hit_ratio.has_value()) {
        visitor.visitFloat("hit_ratio", _global_filter_hit_ratio.value());
    }
    visitor.closeStruct();
}

bool
NearestNeighborBlueprint::always_needs_unpack() const
{
    return true;
}

std::ostream&
operator<<(std::ostream& out, NearestNeighborBlueprint::Algorithm algorithm)
{
    out << to_string(algorithm);
    return out;
}

}
