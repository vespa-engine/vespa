// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "i_direct_posting_store.h"
#include <vespa/searchlib/queryeval/searchiterator.h>

namespace search::fef { class MatchData; }
namespace search::attribute {

/**
 * Filter iterator on top of low-level posting list iterators or regular search iterators with OR semantics.
 *
 * Used during calculation of global filter for InTerm, WeightedSetTerm, DotProduct and WandTerm,
 * or when ranking is not needed for InTerm and WeightedSetTerm.
 */
class MultiTermOrFilterSearch : public queryeval::SearchIterator
{
protected:
    MultiTermOrFilterSearch() = default;
public:
    static std::unique_ptr<SearchIterator> create(std::vector<DocidIterator>&& children);
    static std::unique_ptr<SearchIterator> create(std::vector<DocidIterator>&& children, fef::TermFieldMatchData& tfmd);
    static std::unique_ptr<SearchIterator> create(std::vector<DocidWithWeightIterator>&& children);
    static std::unique_ptr<SearchIterator> create(std::vector<DocidWithWeightIterator>&& children, fef::TermFieldMatchData& tfmd);
    static std::unique_ptr<SearchIterator> create(const std::vector<SearchIterator *>& children,
                                                  std::unique_ptr<fef::MatchData> md);
};

}
