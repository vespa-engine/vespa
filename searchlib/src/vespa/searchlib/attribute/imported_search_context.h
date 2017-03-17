// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributevector.h"
#include <vespa/searchcommon/attribute/i_search_context.h>
#include <memory>

namespace search {

namespace fef {
class TermFieldMatchData;
}

namespace attribute {

class ReferenceAttribute;
class ImportedAttributeVector;
class SearchContextParams;

/**
 * Search context exposing iteraton over an imported attribute vector.
 *
 * Iterator doc id matching is performed via the GID->LID indirection of the
 * associated reference attribute. This means that if the _referenced_ document
 * matches the search term, the doc id of the _referring_ document will be
 * considered a match.
 */
class ImportedSearchContext : public ISearchContext {
    const ImportedAttributeVector&                  _imported_attribute;
    const ReferenceAttribute&                       _reference_attribute;
    const AttributeVector&                          _target_attribute;
    std::unique_ptr<AttributeVector::SearchContext> _target_search_context;
public:
    ImportedSearchContext(std::unique_ptr<QueryTermSimple> term,
                          const SearchContextParams& params,
                          const ImportedAttributeVector& imported_attribute);
    ~ImportedSearchContext();

    unsigned int approximateHits() const override;

    std::unique_ptr<queryeval::SearchIterator>
    createIterator(fef::TermFieldMatchData* matchData, bool strict) override;

    void fetchPostings(bool strict) override;

    bool valid() const override;

    Int64Range getAsIntegerTerm() const override;

    const QueryTermBase& queryTerm() const override;

    const vespalib::string& attributeName() const override;

    using DocId = IAttributeVector::DocId;

    bool cmp(DocId docId, int32_t& weight) const;
    bool cmp(DocId docId) const;

    const ReferenceAttribute& attribute() const noexcept { return _reference_attribute; }

    const AttributeVector::SearchContext& target_search_context() const noexcept {
        return *_target_search_context;
    }
};

} // attribute
} // search



