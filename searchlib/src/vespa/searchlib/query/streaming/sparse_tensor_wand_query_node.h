// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "queryterm.h"

#include <vespa/searchlib/queryeval/wand/weak_and_heap.h>

#include <optional>
#include <utility>
#include <vector>

namespace search::streaming {

/*
 * Sparse tensor WAND query node for streaming search.
 *
 * The score (dot product between the document tensor and the query tensor)
 * is added per field by the field searchers when it exceeds the current score
 * threshold. The node owns the heap of hit scores used to raise the threshold,
 * so it is shared by all fields searched by the term. The best score among
 * the fields is added to the heap once per document that became a hit.
 */
class SparseTensorWandQueryNode : public QueryTerm {
private:
    using ScoreHeap = search::queryeval::WeakAndPriorityQueueT<double>;
    uint32_t  _target_hits;
    double    _score_threshold;
    ScoreHeap _heap;
    // Score per field id. A non-empty vector also indicates a match for this query node.
    std::vector<std::pair<uint32_t, double>> _field_scores;
    // Last document added to the heap, as unpacking can happen more than once per document.
    std::optional<uint32_t> _last_hit_docid;

public:
    using QueryTerm::unpack_match_data;
    SparseTensorWandQueryNode(std::unique_ptr<QueryNodeResultBase> resultBase, std::string_view query_tensor_name,
                              string field_name, uint32_t target_hits, double score_threshold, int32_t unique_id,
                              search::query::Weight weight);
    SparseTensorWandQueryNode(const SparseTensorWandQueryNode&) = delete;
    SparseTensorWandQueryNode& operator=(const SparseTensorWandQueryNode&) = delete;
    SparseTensorWandQueryNode(SparseTensorWandQueryNode&&) = delete;
    SparseTensorWandQueryNode& operator=(SparseTensorWandQueryNode&&) = delete;
    ~SparseTensorWandQueryNode() override;
    bool evaluate() override;
    void reset() override;
    SparseTensorWandQueryNode* as_sparse_tensor_wand_query_node() noexcept override;
    const std::string& get_query_tensor_name() const { return getTermString(); }
    uint32_t get_target_hits() const { return _target_hits; }
    double get_score_threshold() const { return _score_threshold; }
    // The current score threshold, raised by the heap of hit scores.
    double get_min_score() const noexcept { return _heap.getMinScore(); }
    void add_field_score(uint32_t field_id, double score) { _field_scores.emplace_back(field_id, score); }
    const std::vector<std::pair<uint32_t, double>>& get_field_scores() const { return _field_scores; }
    void unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data,
                           const fef::IIndexEnvironment& index_env, search::common::ElementIds element_ids) override;
};

} // namespace search::streaming
