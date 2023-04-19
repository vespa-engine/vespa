// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nearest_neighbor_query_node.h"

namespace search::streaming {

NearestNeighborQueryNode::NearestNeighborQueryNode(std::unique_ptr<QueryNodeResultBase> resultBase, const string& term, const string& index, int32_t id, search::query::Weight weight, double distance_threshold)
    : QueryTerm(std::move(resultBase), term, index, Type::NEAREST_NEIGHBOR),
      _distance_threshold(distance_threshold),
      _raw_score()
{
    setUniqueId(id);
    setWeight(weight);
}

NearestNeighborQueryNode::~NearestNeighborQueryNode() = default;

bool
NearestNeighborQueryNode::evaluate() const
{
    return _raw_score.has_value();
}

void
NearestNeighborQueryNode::reset()
{
    _raw_score.reset();
}

NearestNeighborQueryNode*
NearestNeighborQueryNode::as_nearest_neighbor_query_node() noexcept
{
    return this;
}

}
