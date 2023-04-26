// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "queryterm.h"
#include <optional>

namespace search::streaming {

/*
 * Nearest neighbor query node for streaming search.
 */
class NearestNeighborQueryNode: public QueryTerm {
public:
    class RawScoreCalculator {
    public:
        virtual ~RawScoreCalculator() = default;
        /**
         * Convert the given distance to a raw score.
         *
         * This is used during unpacking, and also signals that the entire document was a match.
         */
        virtual double to_raw_score(double distance) = 0;
    };

private:
    uint32_t _target_hits;
    double _distance_threshold;
    // When this value is set it also indicates a match for this query node.
    std::optional<double> _distance;
    RawScoreCalculator* _calc;


public:
    NearestNeighborQueryNode(std::unique_ptr<QueryNodeResultBase> resultBase,
                             const string& query_tensor_name, const string& field_name,
                             uint32_t target_hits, double distance_threshold,
                             int32_t unique_id, search::query::Weight weight);
    NearestNeighborQueryNode(const NearestNeighborQueryNode &) = delete;
    NearestNeighborQueryNode & operator = (const NearestNeighborQueryNode &) = delete;
    NearestNeighborQueryNode(NearestNeighborQueryNode &&) = delete;
    NearestNeighborQueryNode & operator = (NearestNeighborQueryNode &&) = delete;
    ~NearestNeighborQueryNode() override;
    bool evaluate() const override;
    void reset() override;
    NearestNeighborQueryNode* as_nearest_neighbor_query_node() noexcept override;
    const vespalib::string& get_query_tensor_name() const { return getTermString(); }
    uint32_t get_target_hits() const { return _target_hits; }
    double get_distance_threshold() const { return _distance_threshold; }
    void set_raw_score_calc(RawScoreCalculator* calc_in) { _calc = calc_in; }
    void set_distance(double value) { _distance = value; }
    const std::optional<double>& get_distance() const { return _distance; }
    // This is used during unpacking, and also signals to the RawScoreCalculator that the entire document was a match.
    std::optional<double> get_raw_score() const;
};

}
