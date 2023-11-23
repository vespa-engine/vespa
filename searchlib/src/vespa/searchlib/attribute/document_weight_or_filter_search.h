// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "i_direct_posting_store.h"
#include <vespa/searchlib/queryeval/searchiterator.h>

namespace search::fef { class MatchData; }
namespace search::attribute {

/**
 * Filter iterator on top of document weight iterators with OR semantics used during
 * calculation of global filter for weighted set terms, wand terms and dot product terms.
 */
class DocumentWeightOrFilterSearch : public queryeval::SearchIterator
{
protected:
    DocumentWeightOrFilterSearch() = default;
public:
    static std::unique_ptr<SearchIterator> create(std::vector<DocidWithWeightIterator>&& children);
    static std::unique_ptr<SearchIterator> create(const std::vector<SearchIterator *>& children,
                                                  std::unique_ptr<fef::MatchData> md);
};

}
