// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nearest_neighbor_blueprint.h"
#include "emptysearch.h"
#include "exact_nearest_neighbor_iterator.h"
#include "nns_index_iterator.h"
#include "queryeval_stats.h"
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/tensor/distance_function_factory.h>
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.queryeval.nearest_neighbor_blueprint");

using vespalib::eval::Value;

namespace vespalib { class Doom; }

namespace search::queryeval {

namespace {

std::string
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

double
NearestNeighborBlueprint::convert_distance_threshold(double distance_threshold,
                                                     const search::tensor::DistanceCalculator& distance_calc)
{
    if (distance_threshold < std::numeric_limits<double>::max()) {
        return distance_calc.function().convert_threshold(distance_threshold);
    }
    return std::numeric_limits<double>::max();
}

NearestNeighborBlueprint::NearestNeighborBlueprint(const queryeval::FieldSpec& field,
                                                   std::unique_ptr<search::tensor::DistanceCalculator> distance_calc,
                                                   uint32_t target_hits,
                                                   bool approximate,
                                                   const HnswParams& hnsw_params,
                                                   const vespalib::Doom& doom)
    : ComplexLeafBlueprint(field),
      _distance_calc(std::move(distance_calc)),
      _attr_tensor(_distance_calc->attribute_tensor()),
      _query_tensor(_distance_calc->query_tensor()),
      _target_hits(target_hits),
      _adjusted_target_hits(target_hits),
      _approximate(approximate),
      _hnsw_params{.explore_additional_hits = hnsw_params.explore_additional_hits,
                   .distance_threshold = convert_distance_threshold(hnsw_params.distance_threshold, *_distance_calc),
                   .global_filter_lower_limit = hnsw_params.global_filter_lower_limit,
                   .global_filter_upper_limit = hnsw_params.global_filter_upper_limit,
                   .filter_first_upper_limit = hnsw_params.filter_first_upper_limit,
                   .filter_first_exploration = hnsw_params.filter_first_exploration,
                   .exploration_slack = hnsw_params.exploration_slack,
                   .prefetch_tensors = hnsw_params.prefetch_tensors,
                   .target_hits_max_adjustment_factor = hnsw_params.target_hits_max_adjustment_factor},
      _distance_heap(target_hits),
      _found_hits(),
      _algorithm(Algorithm::EXACT),
      _global_filter(GlobalFilter::create()),
      _global_filter_set(false),
      _global_filter_hits(),
      _global_filter_hit_ratio(),
      _lazy_filter(),
      _doom(doom),
      _matching_phase(MatchingPhase::FIRST_PHASE),
      _nni_stats(),
      _stats()
{
    _distance_heap.set_distance_threshold(_hnsw_params.distance_threshold);
    uint32_t est_hits = _attr_tensor.get_num_docs();
    setEstimate(HitEstimate(est_hits, false));
}

NearestNeighborBlueprint::~NearestNeighborBlueprint() {
    if (_stats) {
        _stats->add_to_approximate_nns_distances_computed(_nni_stats.distances_computed());
        _stats->add_to_approximate_nns_nodes_visited(_nni_stats.nodes_visited());
    }
}

bool
NearestNeighborBlueprint::want_global_filter(GlobalFilterLimits& limits) const
{
    auto nns_index = _attr_tensor.nearest_neighbor_index();
    if (nns_index && _approximate) {
        limits.lower_limit = _hnsw_params.global_filter_lower_limit;
        limits.upper_limit = _hnsw_params.global_filter_upper_limit;
        return true;
    }
    return false;
}

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
            est_hits = std::min(est_hits, _global_filter_hits.value());
            if (_global_filter_hit_ratio.value() < _hnsw_params.global_filter_lower_limit) {
                _algorithm = Algorithm::EXACT_FALLBACK;
                setEstimate(HitEstimate(est_hits, false));
            }
        } else { // post-filtering case
            // The goal is to expose 'targetHits' hits to first-phase ranking.
            // We try to achieve this by adjusting targetHits based on the estimated hit ratio of the query before post-filtering.
            // However, this is bound by 'target-hits-max-adjustment-factor' to limit the cost of searching the HNSW index.
            if (estimated_hit_ratio > 0.0) {
                _adjusted_target_hits = std::min(static_cast<double>(_target_hits) / estimated_hit_ratio,
                                                 static_cast<double>(_target_hits) * _hnsw_params.target_hits_max_adjustment_factor);
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
NearestNeighborBlueprint::set_lazy_filter(const GlobalFilter &lazy_filter) {
    _lazy_filter = lazy_filter.shared_from_this();
}

void
NearestNeighborBlueprint::perform_top_k(const search::tensor::NearestNeighborIndex* nns_index)
{
    uint32_t k = _adjusted_target_hits;
    const auto &df = _distance_calc->function();
    if (_lazy_filter && _lazy_filter->is_active()) { // Global filter might or might not be active
        std::shared_ptr<const GlobalFilter> use_filter = _lazy_filter;
        if (_global_filter->is_active()) { // Both global filter and lazy filter
            use_filter = FallbackFilter::create(*_global_filter, *_lazy_filter);
        }
        bool low_hit_ratio = (static_cast<double>(use_filter->count()) / _attr_tensor.get_num_docs()) < _hnsw_params.filter_first_upper_limit;
        _found_hits = nns_index->find_top_k_with_filter(_nni_stats, k, df, *use_filter, low_hit_ratio, _hnsw_params.filter_first_exploration,
                                                        k + _hnsw_params.explore_additional_hits, _hnsw_params.exploration_slack, _doom, _hnsw_params.distance_threshold);
        _algorithm = Algorithm::INDEX_TOP_K_WITH_FILTER;
    } else if (_global_filter->is_active()) {
        _found_hits = nns_index->find_top_k_with_filter(_nni_stats, k, df, *_global_filter, _global_filter_hit_ratio.value() < _hnsw_params.filter_first_upper_limit, _hnsw_params.filter_first_exploration,
                                                        k + _hnsw_params.explore_additional_hits, _hnsw_params.exploration_slack, _hnsw_params.prefetch_tensors, _doom, _hnsw_params.distance_threshold);
        _algorithm = Algorithm::INDEX_TOP_K_WITH_FILTER;
    } else {
        _found_hits = nns_index->find_top_k(_nni_stats, k, df, k + _hnsw_params.explore_additional_hits, _hnsw_params.exploration_slack, _hnsw_params.prefetch_tensors, _doom, _hnsw_params.distance_threshold);
        _algorithm = Algorithm::INDEX_TOP_K;
    }
}

void
NearestNeighborBlueprint::sort(InFlow in_flow)
{
    resolve_strict(in_flow);
}

std::unique_ptr<SearchIterator>
NearestNeighborBlueprint::createLeafSearch(const search::fef::TermFieldMatchDataArray& tfmda) const
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
    return ExactNearestNeighborIterator::create(_stats, strict(), tfmd,
                                                std::make_unique<search::tensor::DistanceCalculator>(_attr_tensor, _query_tensor),
                                                _distance_heap, *_global_filter,
                                                _matching_phase != MatchingPhase::FIRST_PHASE);
}

void NearestNeighborBlueprint::install_stats(QueryEvalStats &stats) {
    _stats = stats.shared_from_this();
}

void
NearestNeighborBlueprint::visitMembers(vespalib::ObjectVisitor& visitor) const
{
    ComplexLeafBlueprint::visitMembers(visitor);
    visitor.visitString("attribute_tensor", _attr_tensor.getTensorType().to_spec());
    visitor.visitString("query_tensor", _query_tensor.type().to_spec());
    visitor.visitInt("target_hits", _target_hits);
    visitor.visitInt("adjusted_target_hits", _adjusted_target_hits);
    visitor.visitInt("explore_additional_hits", _hnsw_params.explore_additional_hits);
    visitor.visitBool("wanted_approximate", _approximate);
    visitor.visitBool("has_index", _attr_tensor.nearest_neighbor_index());
    visitor.visitString("algorithm", to_string(_algorithm));
    if (_algorithm == Algorithm::INDEX_TOP_K || _algorithm == Algorithm::INDEX_TOP_K_WITH_FILTER) {
        visitor.visitInt("top_k_hits", _found_hits.size());
    }

    visitor.openStruct("global_filter", "GlobalFilter");
    GlobalFilterLimits limits;
    visitor.visitBool("wanted", want_global_filter(limits));
    visitor.visitBool("set", _global_filter_set);
    visitor.visitBool("calculated", _global_filter->is_active());
    visitor.visitFloat("lower_limit", _hnsw_params.global_filter_lower_limit);
    visitor.visitFloat("upper_limit", _hnsw_params.global_filter_upper_limit);
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

void
NearestNeighborBlueprint::set_matching_phase(MatchingPhase matching_phase) noexcept
{
    _matching_phase = matching_phase;
    if (matching_phase != MatchingPhase::FIRST_PHASE) {
        /*
         * During first phase matching, the distance heap is adjusted
         * by the iterators. The distance threshold is lowered when
         * the distance heap is full while handling a matching
         * document with a lower distance than the worst existing one.
         *
         * During later matching phases, only the original distance
         * threshold is used, and the heap is not updated by the
         * iterators. This ensures that all documents considered a hit
         * by the first phase matching will also be considered as hits
         * by the later matching phases.
         */
        _distance_heap.set_distance_threshold(_hnsw_params.distance_threshold);
    }
}

std::ostream&
operator<<(std::ostream& out, NearestNeighborBlueprint::Algorithm algorithm)
{
    out << to_string(algorithm);
    return out;
}

}
