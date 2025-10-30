// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "blueprint.h"
#include "nearest_neighbor_distance_heap.h"
#include <vespa/searchlib/tensor/distance_calculator.h>
#include <vespa/searchlib/tensor/distance_function.h>
#include <vespa/searchlib/tensor/nearest_neighbor_index.h>
#include <optional>

namespace search::tensor { class ITensorAttribute; }
namespace vespalib::eval { struct Value; }

namespace search::queryeval {

/**
 * Blueprint for nearest neighbor search iterator.
 *
 * The search iterator matches the K nearest neighbors in a multi-dimensional vector space,
 * where the query point and document points are dense tensors of order 1.
 */
class NearestNeighborBlueprint : public ComplexLeafBlueprint {
public:
    enum class Algorithm {
        EXACT,
        EXACT_FALLBACK,
        INDEX_TOP_K,
        INDEX_TOP_K_WITH_FILTER
    };
    struct HnswParams {
        uint32_t explore_additional_hits;
        double distance_threshold;
        double global_filter_lower_limit;
        double global_filter_upper_limit;
        double filter_first_upper_limit;
        double filter_first_exploration;
        double exploration_slack;
        double target_hits_max_adjustment_factor;
    };
private:
    std::unique_ptr<search::tensor::DistanceCalculator> _distance_calc;
    const tensor::ITensorAttribute& _attr_tensor;
    const vespalib::eval::Value& _query_tensor;
    uint32_t _target_hits;
    uint32_t _adjusted_target_hits;
    bool _approximate;
    const HnswParams _hnsw_params;
    mutable NearestNeighborDistanceHeap _distance_heap;
    std::vector<search::tensor::NearestNeighborIndex::Neighbor> _found_hits;
    Algorithm _algorithm;
    std::shared_ptr<const GlobalFilter> _global_filter;
    bool _global_filter_set;
    std::optional<uint32_t> _global_filter_hits;
    std::optional<double> _global_filter_hit_ratio;
    const vespalib::Doom& _doom;
    MatchingPhase _matching_phase;

    static double convert_distance_threshold(double distance_threshold,
                                             const search::tensor::DistanceCalculator& distance_calc);
    void perform_top_k(const search::tensor::NearestNeighborIndex* nns_index);
public:
    NearestNeighborBlueprint(const queryeval::FieldSpec& field,
                             std::unique_ptr<search::tensor::DistanceCalculator> distance_calc,
                             uint32_t target_hits, bool approximate,
                             const HnswParams& hnsw_params,
                             const vespalib::Doom& doom);
    NearestNeighborBlueprint(const NearestNeighborBlueprint&) = delete;
    NearestNeighborBlueprint& operator=(const NearestNeighborBlueprint&) = delete;
    ~NearestNeighborBlueprint() override;
    const tensor::ITensorAttribute& get_attribute_tensor() const { return _attr_tensor; }
    const vespalib::eval::Value& get_query_tensor() const { return _query_tensor; }
    uint32_t get_target_hits() const { return _target_hits; }
    uint32_t get_adjusted_target_hits() const { return _adjusted_target_hits; }
    bool want_global_filter(GlobalFilterLimits& limits) const override;
    void set_global_filter(const GlobalFilter &global_filter, double estimated_hit_ratio) override;
    Algorithm get_algorithm() const { return _algorithm; }
    double get_distance_threshold() const { return _hnsw_params.distance_threshold; }
    const HnswParams& get_hnsw_params() const { return _hnsw_params; }

    void sort(InFlow in_flow) override;
    FlowStats calculate_flow_stats(uint32_t docid_limit) const override {
        return default_flow_stats(docid_limit, getState().estimate().estHits, 0);
    }

    std::unique_ptr<SearchIterator> createLeafSearch(const search::fef::TermFieldMatchDataArray& tfmda) const override;
    SearchIteratorUP createFilterSearchImpl(FilterConstraint constraint) const override {
        return create_default_filter(constraint);
    }
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    bool always_needs_unpack() const override;
    void set_matching_phase(MatchingPhase matching_phase) noexcept override;
};

std::ostream&
operator<<(std::ostream& out, NearestNeighborBlueprint::Algorithm algorithm);

}
