// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "queryterm.h"

#include <optional>

namespace search::streaming {

/*
 * Sparse tensor WAND query node for streaming search.
 *
 * The score (dot product between the document tensor and the query tensor)
 * is set by the field searcher when it exceeds the current score threshold.
 */
class SparseTensorWandQueryNode : public QueryTerm {
public:
    class HitObserver {
    public:
        virtual ~HitObserver() = default;
        /**
         * Called during unpacking, which signals that the entire document was a match
         * with the given score.
         */
        virtual void on_hit(double score) = 0;
    };

private:
    uint32_t _target_hits;
    double   _score_threshold;
    // When this value is set it also indicates a match for this query node.
    std::optional<double> _score;
    HitObserver*          _observer;

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
    void set_hit_observer(HitObserver* observer) { _observer = observer; }
    void set_score(double value) { _score = value; }
    const std::optional<double>& get_score() const { return _score; }
    void unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data,
                           const fef::IIndexEnvironment& index_env, search::common::ElementIds element_ids) override;
};

} // namespace search::streaming
