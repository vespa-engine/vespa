// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nearest_neighbor_query_node.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <cassert>

namespace search::streaming {

NearestNeighborQueryNode::NearestNeighborQueryNode(std::unique_ptr<QueryNodeResultBase> resultBase,
                                                   std::string_view query_tensor_name, string field_name,
                                                   uint32_t target_hits, double distance_threshold,
                                                   int32_t unique_id, search::query::Weight weight)
    : QueryTerm(std::move(resultBase), query_tensor_name, std::move(field_name), Type::NEAREST_NEIGHBOR, Normalizing::NONE),
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

void
NearestNeighborQueryNode::unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data, const fef::IIndexEnvironment& index_env)
{
    (void) index_env;
    auto raw_score = get_raw_score();
    if (raw_score.has_value()) {
        if (td.numFields() == 1u) {
            auto& tfd = td.field(0u);
            auto tmd = match_data.resolveTermField(tfd.getHandle());
            assert(tmd != nullptr);
            tmd->setRawScore(docid, raw_score.value());
        }
    }
}

}
