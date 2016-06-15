// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.emptysearchview");

#include "emptysearchview.h"

using search::engine::DocsumReply;
using search::engine::DocsumRequest;
using search::engine::SearchReply;
using search::engine::SearchRequest;

namespace proton
{

EmptySearchView::EmptySearchView(void)
    : boost::noncopyable(),
      ISearchHandler()
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
    reply->useCoverage = true;
    return reply;
}


} // namespace proton
