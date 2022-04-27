// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "blueprint.h"
#include "nearest_neighbor_distance_heap.h"
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
        BRUTE_FORCE,
        BRUTE_FORCE_FALLBACK,
        INDEX_TOP_K,
        INDEX_TOP_K_WITH_FILTER
    };
private:
    const tensor::ITensorAttribute& _attr_tensor;
    std::unique_ptr<vespalib::eval::Value> _query_tensor;
    uint32_t _target_num_hits;
    bool _approximate;
    uint32_t _explore_additional_hits;
    double _distance_threshold;
    double _global_filter_lower_limit;
    double _global_filter_upper_limit;
    search::tensor::DistanceFunction::UP _fallback_dist_fun;
    const search::tensor::DistanceFunction *_dist_fun;
    mutable NearestNeighborDistanceHeap _distance_heap;
    std::vector<search::tensor::NearestNeighborIndex::Neighbor> _found_hits;
    Algorithm _algorithm;
    std::shared_ptr<const GlobalFilter> _global_filter;
    bool _global_filter_set;
    std::optional<uint32_t> _global_filter_hits;
    std::optional<double> _global_filter_hit_ratio;

    void perform_top_k(const search::tensor::NearestNeighborIndex* nns_index);
public:
    NearestNeighborBlueprint(const queryeval::FieldSpec& field,
                             const tensor::ITensorAttribute& attr_tensor,
                             std::unique_ptr<vespalib::eval::Value> query_tensor,
                             uint32_t target_num_hits, bool approximate, uint32_t explore_additional_hits,
                             double distance_threshold,
                             double global_filter_lower_limit,
                             double global_filter_upper_limit);
    NearestNeighborBlueprint(const NearestNeighborBlueprint&) = delete;
    NearestNeighborBlueprint& operator=(const NearestNeighborBlueprint&) = delete;
    ~NearestNeighborBlueprint();
    const tensor::ITensorAttribute& get_attribute_tensor() const { return _attr_tensor; }
    const vespalib::eval::Value& get_query_tensor() const { return *_query_tensor; }
    uint32_t get_target_num_hits() const { return _target_num_hits; }
    void set_global_filter(const GlobalFilter &global_filter) override;
    Algorithm get_algorithm() const { return _algorithm; }
    double get_distance_threshold() const { return _distance_threshold; }

    std::unique_ptr<SearchIterator> createLeafSearch(const search::fef::TermFieldMatchDataArray& tfmda,
                                                     bool strict) const override;
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    bool always_needs_unpack() const override;
};

std::ostream&
operator<<(std::ostream& out, NearestNeighborBlueprint::Algorithm algorithm);

}
