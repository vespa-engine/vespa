// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_search_context.h"
#include "attributeiterators.hpp"
#include "imported_attribute_vector.h"
#include "reference_attribute.h"
#include <vespa/searchcommon/attribute/search_context_params.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/query/queryterm.h>

namespace search {
namespace attribute {

ImportedSearchContext::ImportedSearchContext(
        std::unique_ptr<QueryTermSimple> term,
        const SearchContextParams& params,
        const ImportedAttributeVector& imported_attribute)
    : _imported_attribute(imported_attribute),
      _reference_attribute(*_imported_attribute.getReferenceAttribute()),
      _target_attribute(*_imported_attribute.getTargetAttribute()),
      _target_search_context(_target_attribute.getSearch(std::move(term), params))
{
}

ImportedSearchContext::~ImportedSearchContext() {
}

unsigned int ImportedSearchContext::approximateHits() const {
    return _reference_attribute.getNumDocs();
}

std::unique_ptr<queryeval::SearchIterator>
ImportedSearchContext::createIterator(fef::TermFieldMatchData* matchData, bool strict) {
    if (!strict) {
        return std::make_unique<AttributeIteratorT<ImportedSearchContext>>(*this, matchData);
    } else {
        return std::make_unique<AttributeIteratorStrict<ImportedSearchContext>>(*this, matchData);
    }
}

void ImportedSearchContext::fetchPostings(bool strict) {
    (void)strict;
    // Imported attributes do not have posting lists (at least not currently), so this is a no-op.
}

bool ImportedSearchContext::valid() const {
    return _target_search_context->valid();
}

Int64Range ImportedSearchContext::getAsIntegerTerm() const {
    return _target_search_context->getAsIntegerTerm();
}

const QueryTermBase& ImportedSearchContext::queryTerm() const {
    return _target_search_context->queryTerm();
}

const vespalib::string& ImportedSearchContext::attributeName() const {
    return _imported_attribute.getName();
}

bool ImportedSearchContext::cmp(DocId docId, int32_t& weight) const {
    return _target_search_context->cmp(_reference_attribute.getReferencedLid(docId), weight);
}

bool ImportedSearchContext::cmp(DocId docId) const {
    return _target_search_context->cmp(_reference_attribute.getReferencedLid(docId));
}

} // attribute
} // search
