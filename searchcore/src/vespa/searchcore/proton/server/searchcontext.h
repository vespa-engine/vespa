// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/attribute/attribute_blueprint_factory.h>
#include <vespa/searchcore/proton/matching/isearchcontext.h>

namespace proton {

/**
 * Defines the context for a search within the document type owned by
 * this database. SearchContext contains the context for a search for
 * the documenttype.  First create, search and rank, then group/sort,
 * collect hits.
 */
class SearchContext : public matching::ISearchContext
{
private:
    /// Snapshot of the indexes used.
    Searchable::SP                    _indexSearchable;
    search::AttributeBlueprintFactory _attributeBlueprintFactory;
    uint32_t                          _docIdLimit;

    Searchable &getIndexes() override;
    Searchable &getAttributes() override;
    uint32_t getDocIdLimit() override;

public:
    SearchContext(const Searchable::SP &indexSearchable, uint32_t docIdLimit);
};

} // namespace proton

