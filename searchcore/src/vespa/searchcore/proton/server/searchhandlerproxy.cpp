// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchhandlerproxy.h"
#include "documentdb.h"
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/searchlib/engine/docsumreply.h>

namespace proton {

SearchHandlerProxy::SearchHandlerProxy(DocumentDB::SP documentDB)
    : _documentDB(std::move(documentDB))
{
    _documentDB->retain();
}

SearchHandlerProxy::~SearchHandlerProxy()
{
    _documentDB->release();
}

std::unique_ptr<search::engine::DocsumReply>
SearchHandlerProxy::getDocsums(const DocsumRequest & request)
{
    return _documentDB->getDocsums(request);
}

std::unique_ptr<search::engine::SearchReply>
SearchHandlerProxy::match(const SearchRequest &req, vespalib::ThreadBundle &threadBundle) const
{
    return _documentDB->match(req, threadBundle);
}

} // namespace proton
