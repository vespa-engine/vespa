// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchcontext.h"

using search::queryeval::Searchable;
using searchcorespi::IndexSearchable;

namespace proton {

IndexSearchable &
SearchContext::getIndexes()
{
    return *_indexSearchable;
}

Searchable &
SearchContext::getAttributes()
{
    return _attributeBlueprintFactory;
}

uint32_t SearchContext::getDocIdLimit()
{
    return _docIdLimit;
}

SearchContext::SearchContext(const std::shared_ptr<IndexSearchable> &indexSearchable, uint32_t docIdLimit)
    : _indexSearchable(indexSearchable),
      _attributeBlueprintFactory(),
      _docIdLimit(docIdLimit)
{
}

SearchContext::~SearchContext() = default;

} // namespace proton
