// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.searchhandlerproxy");
#include "searchhandlerproxy.h"

namespace proton {

SearchHandlerProxy::SearchHandlerProxy(const DocumentDB::SP &documentDB)
    : _documentDB(documentDB)
{
    _documentDB->retain();
}


SearchHandlerProxy::~SearchHandlerProxy(void)
{
    _documentDB->release();
}


search::engine::DocsumReply::UP
SearchHandlerProxy::getDocsums(const search::engine::DocsumRequest & request)
{
    return _documentDB->getDocsums(request);
}

search::engine::SearchReply::UP
SearchHandlerProxy::match(const ISearchHandler::SP &searchHandler,
                          const search::engine::SearchRequest &req,
                          vespalib::ThreadBundle &threadBundle) const
{
    return _documentDB->match(searchHandler, req, threadBundle);
}

} // namespace proton
