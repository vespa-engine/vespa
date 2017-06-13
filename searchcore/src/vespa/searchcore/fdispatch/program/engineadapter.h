// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/engine/searchapi.h>
#include <vespa/searchlib/engine/docsumapi.h>
#include <vespa/searchlib/engine/monitorapi.h>

#include <vespa/searchcore/fdispatch/common/appcontext.h>

namespace fdispatch {

/**
 * Implementation of the common search api for the fdispatch server
 * application.
 **/
class EngineAdapter : public search::engine::SearchServer,
                      public search::engine::DocsumServer,
                      public search::engine::MonitorServer
{
private:
    FastS_AppContext         *_appCtx;
    FastOS_ThreadPool        *_mypool;

public:
    typedef search::engine::SearchRequest SearchRequest;
    typedef search::engine::DocsumRequest DocsumRequest;
    typedef search::engine::MonitorRequest MonitorRequest;

    typedef search::engine::SearchReply SearchReply;
    typedef search::engine::DocsumReply DocsumReply;
    typedef search::engine::MonitorReply MonitorReply;

    typedef search::engine::SearchClient SearchClient;
    typedef search::engine::DocsumClient DocsumClient;
    typedef search::engine::MonitorClient MonitorClient;

    EngineAdapter(FastS_AppContext *appCtx,
                  FastOS_ThreadPool *threadPool);

    virtual SearchReply::UP search(SearchRequest::Source request, SearchClient &client) override;
    virtual DocsumReply::UP getDocsums(DocsumRequest::Source request, DocsumClient &client) override;
    virtual MonitorReply::UP ping(MonitorRequest::UP request, MonitorClient &client) override;
};

} // namespace fdispatch

