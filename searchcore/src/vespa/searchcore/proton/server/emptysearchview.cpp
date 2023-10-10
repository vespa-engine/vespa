// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "emptysearchview.h"
#include <vespa/searchlib/engine/docsumreply.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/vespalib/data/slime/slime.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.emptysearchview");

using search::engine::DocsumReply;
using search::engine::DocsumRequest;
using search::engine::SearchReply;
using search::engine::SearchRequest;

using vespalib::Slime;
using namespace vespalib::slime;

namespace proton {

EmptySearchView::EmptySearchView() = default;


DocsumReply::UP
EmptySearchView::getDocsums(const DocsumRequest &req)
{
    LOG(debug, "getDocsums(): resultClass(%s), numHits(%zu)",
        req.resultClassName.c_str(), req.hits.size());
    return std::make_unique<DocsumReply>();
}

SearchReply::UP
EmptySearchView::match(const SearchRequest &, vespalib::ThreadBundle &) const {
    return std::make_unique<SearchReply>();
}


} // namespace proton
