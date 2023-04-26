// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nearest_neighbor_query_node.h"
#include <cassert>

namespace search::streaming {

NearestNeighborQueryNode::NearestNeighborQueryNode(std::unique_ptr<QueryNodeResultBase> resultBase,
                                                   const string& query_tensor_name, const string& field_name,
                                                   uint32_t target_hits, double distance_threshold,
                                                   int32_t unique_id, search::query::Weight weight)
    : QueryTerm(std::move(resultBase), query_tensor_name, field_name, Type::NEAREST_NEIGHBOR),
      _target_hits(target_hits),
      _distance_threshold(distance_threshold),
      _distance(),
      _calc()
{
    setUniqueId(unique_id);
    setWeight(weight);
}

NearestNeighborQueryNode::~NearestNeighborQueryNode() = default;

bool
NearestNeighborQueryNode::evaluate() const
{
    return _distance.has_value();
}

void
NearestNeighborQueryNode::reset()
{
    _distance.reset();
}

NearestNeighborQueryNode*
NearestNeighborQueryNode::as_nearest_neighbor_query_node() noexcept
{
    return this;
}

std::optional<double>
NearestNeighborQueryNode::get_raw_score() const
{
    if (_distance.has_value()) {
        assert(_calc != nullptr);
        return _calc->to_raw_score(_distance.value());
    }
    return std::nullopt;
}

}
