// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchcontext.h"

using search::queryeval::Searchable;

namespace proton {

Searchable &
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

SearchContext::SearchContext(const Searchable::SP &indexSearchable, uint32_t docIdLimit)
    : _indexSearchable(indexSearchable),
      _attributeBlueprintFactory(),
      _docIdLimit(docIdLimit)
{
}

} // namespace proton
