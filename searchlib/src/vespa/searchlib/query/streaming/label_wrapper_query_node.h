// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "query.h"

namespace search::streaming {

/**
 * A rank-like node with a single child: matching is decided by the child alone,
 * while the label score is exposed as the raw score of this node's own term
 * field handle, so that rank features such as itemRawScore can pick it up.
 *
 * The unique id is what a "vespa.label.<label>.id" rank property maps a label to.
 */
class LabelWrapperQueryNode : public RankWithQueryNode {
private:
    std::unique_ptr<QueryNodeResultBase> _query_item;
    uint32_t                             _unique_id;
    double                               _score;

    void unpack_wrapper(uint32_t docid, fef::MatchData& match_data) const;

public:
    explicit LabelWrapperQueryNode(std::unique_ptr<QueryNodeResultBase> query_item) noexcept;
    ~LabelWrapperQueryNode() override;

    void set_unique_id(uint32_t unique_id) noexcept { _unique_id = unique_id; }
    [[nodiscard]] uint32_t unique_id() const noexcept { return _unique_id; }
    void set_label_score(double score) noexcept { _score = score; }
    [[nodiscard]] double label_score() const noexcept { return _score; }

    [[nodiscard]] QueryNodeResultBase& getQueryItem() noexcept { return *_query_item; }
    [[nodiscard]] const QueryNodeResultBase& getQueryItem() const noexcept { return *_query_item; }

    void unpack_match_data(uint32_t docid, fef::MatchData& match_data, const fef::IIndexEnvironment& index_env,
                           search::common::ElementIds element_ids) override;
    void unpack_match_data(uint32_t docid, fef::MatchData& match_data, const fef::IIndexEnvironment& index_env,
                           std::span<const queryeval::MatchSpan> match_spans) override;

    /** Collects every label wrapper node in the tree rooted at the given node. */
    static void collect(QueryNode& node, std::vector<LabelWrapperQueryNode*>& wrappers);
};

} // namespace search::streaming
