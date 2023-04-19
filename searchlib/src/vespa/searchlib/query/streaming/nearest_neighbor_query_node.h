// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "queryterm.h"
#include <optional>

namespace search::streaming {

/*
 * Nearest neighbor query node.
 */
class NearestNeighborQueryNode: public QueryTerm {
private:
    double _distance_threshold;
    // When this value is set it also indicates a match
    std::optional<double> _raw_score;

public:
    NearestNeighborQueryNode(std::unique_ptr<QueryNodeResultBase> resultBase, const string& term, const string& index, int32_t id, search::query::Weight weight, double distance_threshold);
    NearestNeighborQueryNode(const NearestNeighborQueryNode &) = delete;
    NearestNeighborQueryNode & operator = (const NearestNeighborQueryNode &) = delete;
    NearestNeighborQueryNode(NearestNeighborQueryNode &&) = delete;
    NearestNeighborQueryNode & operator = (NearestNeighborQueryNode &&) = delete;
    ~NearestNeighborQueryNode() override;
    bool evaluate() const override;
    void reset() override;
    NearestNeighborQueryNode* as_nearest_neighbor_query_node() noexcept override;
    const vespalib::string& get_query_tensor_name() const { return getTermString(); }
    double get_distance_threshold() const { return _distance_threshold; }
    void set_raw_score(double value) { _raw_score = value; }
    const std::optional<double>& get_raw_score() const noexcept { return _raw_score; }
};

}
