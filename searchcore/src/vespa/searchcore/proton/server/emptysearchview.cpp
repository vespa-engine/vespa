// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "emptysearchview.h"
#include <vespa/searchlib/engine/docsumreply.h>
#include <vespa/searchlib/engine/searchreply.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.emptysearchview");

using search::engine::DocsumReply;
using search::engine::DocsumRequest;
using search::engine::SearchReply;
using search::engine::SearchRequest;

namespace proton {

EmptySearchView::EmptySearchView() = default;


DocsumReply::UP
EmptySearchView::getDocsums(const DocsumRequest &req)
{
    LOG(debug, "getDocsums(): resultClass(%s), numHits(%zu)",
        req.resultClassName.c_str(), req.hits.size());
    auto reply = std::make_unique<DocsumReply>();
    for (const auto & hit : req.hits) {
        reply->docsums.emplace_back(hit.gid);
    }
    return reply;
}

SearchReply::UP
EmptySearchView::match(const SearchRequest &, vespalib::ThreadBundle &) const {
    return std::make_unique<SearchReply>();
}


} // namespace proton
