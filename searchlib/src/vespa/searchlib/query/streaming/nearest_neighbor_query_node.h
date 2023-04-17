// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "queryterm.h"

namespace search::streaming {

/*
 * Nearest neighbor query node.
 */
class NearestNeighborQueryNode: public QueryTerm
{
    double                _distance_threshold;
public:
    NearestNeighborQueryNode(std::unique_ptr<QueryNodeResultBase> resultBase, const string& term, const string& index, int32_t id, search::query::Weight weight, double distance_threshold);
    NearestNeighborQueryNode(const NearestNeighborQueryNode &) = delete;
    NearestNeighborQueryNode & operator = (const NearestNeighborQueryNode &) = delete;
    NearestNeighborQueryNode(NearestNeighborQueryNode &&) = delete;
    NearestNeighborQueryNode & operator = (NearestNeighborQueryNode &&) = delete;
    ~NearestNeighborQueryNode() override;
    NearestNeighborQueryNode* as_nearest_neighbor_query_node() noexcept override;
    const vespalib::string& get_query_tensor_name() const { return getTermString(); }
    double get_distance_threshold() const { return _distance_threshold; }
};

}
