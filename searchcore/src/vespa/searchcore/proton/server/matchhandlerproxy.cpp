// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.matchhandlerproxy");
#include "matchhandlerproxy.h"

namespace proton {

MatchHandlerProxy::MatchHandlerProxy(const DocumentDB::SP &documentDB)
    : _documentDB(documentDB)
{
    _documentDB->retain();
}


MatchHandlerProxy::~MatchHandlerProxy(void)
{
    _documentDB->release();
}


search::engine::SearchReply::UP
MatchHandlerProxy::match(const ISearchHandler::SP &searchHandler,
                         const search::engine::SearchRequest &req,
                         vespalib::ThreadBundle &threadBundle) const
{
    return _documentDB->match(searchHandler, req, threadBundle);
}


} // namespace proton
