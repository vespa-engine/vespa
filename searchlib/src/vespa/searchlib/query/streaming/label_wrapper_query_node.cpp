// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "label_wrapper_query_node.h"

#include "query_term_data.h"

#include <vespa/searchlib/fef/matchdata.h>

#include <cassert>

namespace search::streaming {

LabelWrapperQueryNode::LabelWrapperQueryNode(std::unique_ptr<QueryNodeResultBase> query_item) noexcept
    : RankWithQueryNode("LABEL_WRAPPER"), _query_item(std::move(query_item)), _unique_id(0), _score(0.0) {
}

LabelWrapperQueryNode::~LabelWrapperQueryNode() = default;

void LabelWrapperQueryNode::unpack_wrapper(uint32_t docid, fef::MatchData& match_data) const {
    if (!_query_item) {
        return;
    }
    const auto& td = static_cast<const QueryTermData&>(*_query_item).getTermData();
    // Holds the reserved "no field" only, and only once set up for ranking.
    if (td.numFields() == 1u) {
        auto* tmd = match_data.resolveTermField(td.field(0u).getHandle());
        assert(tmd != nullptr);
        tmd->setRawScore(docid, _score);
    }
}

void LabelWrapperQueryNode::unpack_match_data(uint32_t docid, fef::MatchData& match_data,
                                              const fef::IIndexEnvironment& index_env,
                                              search::common::ElementIds    element_ids) {
    RankWithQueryNode::unpack_match_data(docid, match_data, index_env, element_ids);
    if (evaluate()) {
        unpack_wrapper(docid, match_data);
    }
}

void LabelWrapperQueryNode::unpack_match_data(uint32_t docid, fef::MatchData& match_data,
                                              const fef::IIndexEnvironment&         index_env,
                                              std::span<const queryeval::MatchSpan> match_spans) {
    RankWithQueryNode::unpack_match_data(docid, match_data, index_env, match_spans);
    if (evaluate()) {
        unpack_wrapper(docid, match_data);
    }
}

void LabelWrapperQueryNode::collect(QueryNode& node, std::vector<LabelWrapperQueryNode*>& wrappers) {
    if (auto* wrapper = dynamic_cast<LabelWrapperQueryNode*>(&node)) {
        wrappers.push_back(wrapper);
    }
    if (auto* connector = dynamic_cast<QueryConnector*>(&node)) {
        for (const auto& child : connector->getChildren()) {
            collect(*child, wrappers);
        }
    }
}

} // namespace search::streaming
