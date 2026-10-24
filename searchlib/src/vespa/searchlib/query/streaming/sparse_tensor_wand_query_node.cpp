// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_wand_query_node.h"

#include <vespa/check_require.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/matchdata.h>

#include <algorithm>
#include <span>

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
      _heap(target_hits),
      _field_scores(),
      _last_hit_docid() {
    _heap.set_min_score(score_threshold);
    setUniqueId(unique_id);
    setWeight(weight);
}

SparseTensorWandQueryNode::~SparseTensorWandQueryNode() = default;

bool SparseTensorWandQueryNode::evaluate() {
    return !_field_scores.empty();
}

void SparseTensorWandQueryNode::reset() {
    _field_scores.clear();
}

SparseTensorWandQueryNode* SparseTensorWandQueryNode::as_sparse_tensor_wand_query_node() noexcept {
    return this;
}

void SparseTensorWandQueryNode::unpack_match_data(uint32_t docid, const fef::ITermData& td,
                                                  fef::MatchData& match_data, const fef::IIndexEnvironment&,
                                                  ElementIds) {
    if (_field_scores.empty()) {
        return;
    }
    // Unpacking signals that the document became a hit
    if (_last_hit_docid != docid) {
        _last_hit_docid = docid;
        double best = std::ranges::max(_field_scores, {}, &std::pair<uint32_t, double>::second).second;
        _heap.adjust(std::span<double>(&best, 1));
    }
    auto num_fields = td.numFields();
    for (uint32_t field_idx = 0; field_idx < num_fields; ++field_idx) {
        auto& tfd = td.field(field_idx);
        auto  field_id = tfd.getFieldId();
        auto  itr = std::ranges::find(_field_scores, field_id, &std::pair<uint32_t, double>::first);
        if (itr != _field_scores.end()) {
            auto tmd = match_data.resolveTermField(tfd.getHandle());
            CHECK(tmd != nullptr);
            tmd->setRawScore(docid, itr->second);
        }
    }
}

} // namespace search::streaming
