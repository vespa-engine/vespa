// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_wand_query_node.h"

#include <vespa/check_require.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/matchdata.h>

using search::common::ElementIds;

namespace search::streaming {

SparseTensorWandQueryNode::SparseTensorWandQueryNode(std::unique_ptr<QueryNodeResultBase> resultBase,
                                                     std::string_view query_tensor_name, string field_name,
                                                     uint32_t target_hits, double score_threshold, int32_t unique_id,
                                                     search::query::Weight weight)
    : QueryTerm(std::move(resultBase), query_tensor_name, std::move(field_name), Type::SPARSE_TENSOR_WAND,
                Normalizing::NONE),
      _target_hits(target_hits),
      _score_threshold(score_threshold),
      _score(),
      _observer(nullptr) {
    setUniqueId(unique_id);
    setWeight(weight);
}

SparseTensorWandQueryNode::~SparseTensorWandQueryNode() = default;

bool SparseTensorWandQueryNode::evaluate() {
    return _score.has_value();
}

void SparseTensorWandQueryNode::reset() {
    _score.reset();
}

SparseTensorWandQueryNode* SparseTensorWandQueryNode::as_sparse_tensor_wand_query_node() noexcept {
    return this;
}

void SparseTensorWandQueryNode::unpack_match_data(uint32_t docid, const fef::ITermData& td,
                                                  fef::MatchData& match_data, const fef::IIndexEnvironment&,
                                                  ElementIds) {
    if (_score.has_value()) {
        if (_observer != nullptr) {
            _observer->on_hit(_score.value());
        }
        if (td.numFields() == 1u) {
            auto& tfd = td.field(0u);
            auto  tmd = match_data.resolveTermField(tfd.getHandle());
            CHECK(tmd != nullptr);
            tmd->setRawScore(docid, _score.value());
        }
    }
}

} // namespace search::streaming
