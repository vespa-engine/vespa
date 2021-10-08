// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "i_document_weight_attribute.h"
#include <vespa/searchlib/queryeval/searchiterator.h>

namespace search::attribute {

/**
 * Filter iterator on top of document weight iterators with OR semantics used during
 * calculation of global filter for weighted set terms, wand terms and dot product terms.
 */
class DocumentWeightOrFilterSearch : public search::queryeval::SearchIterator
{
protected:
    DocumentWeightOrFilterSearch()
        : search::queryeval::SearchIterator()
    {
    }
public:
    static std::unique_ptr<search::queryeval::SearchIterator> create(std::vector<DocumentWeightIterator>&& children);
};

}
