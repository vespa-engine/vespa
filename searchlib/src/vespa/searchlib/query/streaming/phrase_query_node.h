// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_term.h"

namespace search::streaming {

/**
   N-ary phrase operator. All terms must be satisfied and have the correct order
   with distance to next term equal to 1.
*/
class PhraseQueryNode : public MultiTerm
{
public:
    PhraseQueryNode(std::unique_ptr<QueryNodeResultBase> result_base, const string& index, uint32_t num_terms);
    ~PhraseQueryNode() override;
    bool evaluate() const override;
    const HitList & evaluateHits(HitList & hl) const override;
    void getPhrases(QueryNodeRefList & tl) override;
    void getPhrases(ConstQueryNodeRefList & tl) const override;
    const QueryTerm::FieldInfo & getFieldInfo(size_t fid) const { return _fieldInfo[fid]; }
    size_t getFieldInfoSize() const { return _fieldInfo.size(); }
    void unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data) override;
    void getLeaves(QueryTermList & tl) override;
    void getLeaves(ConstQueryTermList & tl) const override;
    size_t width() const override;
    MultiTerm* as_multi_term() noexcept override;
private:
    mutable std::vector<QueryTerm::FieldInfo> _fieldInfo;
    void updateFieldInfo(size_t fid, size_t offset, size_t fieldLength) const;
};

}
