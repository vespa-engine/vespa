// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/matching/isearchcontext.h>
#include <vespa/searchlib/attribute/attribute_blueprint_factory.h>

namespace proton {

/**
 * Defines the context for a search within the document type owned by
 * this database. SearchContext contains the context for a search for
 * the documenttype.  First create, search and rank, then group/sort,
 * collect hits.
 */
class SearchContext final : public matching::ISearchContext {
private:
    /// Snapshot of the indexes used.
    std::shared_ptr<IndexSearchable>  _indexSearchable;
    search::AttributeBlueprintFactory _attributeBlueprintFactory;
    uint32_t                          _docIdLimit;
    DocTypeName                       _doc_type_name;

    IndexSearchable& getIndexes() override;
    Searchable& getAttributes() override;
    uint32_t getDocIdLimit() override;
    const std::string_view get_document_type_name() override;

public:
    SearchContext(const std::shared_ptr<IndexSearchable>& indexSearchable, uint32_t docIdLimit,
                  const search::engine::Request& req);
    ~SearchContext() override;
};

} // namespace proton
