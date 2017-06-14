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

EmptySearchView::EmptySearchView()
    : ISearchHandler()
{
}


DocsumReply::UP
EmptySearchView::getDocsums(const DocsumRequest &req)
{
    LOG(debug, "getDocsums(): resultClass(%s), numHits(%zu)",
        req.resultClassName.c_str(), req.hits.size());
    DocsumReply::UP reply(new DocsumReply());
    for (size_t i = 0; i < req.hits.size(); ++i) {
        reply->docsums.push_back(DocsumReply::Docsum());
        reply->docsums.back().gid = req.hits[i].gid;
    }
    return reply;
}

SearchReply::UP
EmptySearchView::match(const ISearchHandler::SP &,
                       const SearchRequest &,
                       vespalib::ThreadBundle &) const {
    SearchReply::UP reply(new SearchReply);
    return reply;
}


} // namespace proton
