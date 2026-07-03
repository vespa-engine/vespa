// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchcontext.h"

using search::queryeval::Searchable;
using searchcorespi::IndexSearchable;

namespace proton {

IndexSearchable& SearchContext::getIndexes() {
    return *_indexSearchable;
}

Searchable& SearchContext::getAttributes() {
    return _attributeBlueprintFactory;
}

uint32_t SearchContext::getDocIdLimit() {
    return _docIdLimit;
}

const std::string_view SearchContext::get_document_type_name() {
    return _doc_type_name.getName();
}

SearchContext::SearchContext(const std::shared_ptr<IndexSearchable>& indexSearchable, uint32_t docIdLimit,
                             const search::engine::Request& req)
    : _indexSearchable(indexSearchable), _attributeBlueprintFactory(), _docIdLimit(docIdLimit), _doc_type_name(req) {
}

SearchContext::~SearchContext() = default;

} // namespace proton
