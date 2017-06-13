// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/engine/docsumapi.h>
#include <vespa/searchcore/fdispatch/common/appcontext.h>
#include <vespa/searchcore/fdispatch/common/search.h>
#include <vespa/searchsummary/docsummary/getdocsumargs.h>

namespace fdispatch {

/**
 * Implementation of the common search api for the fdispatch server
 * application.
 **/
class DocsumAdapter : public FastOS_Runnable
{
public:
    typedef search::engine::DocsumRequest DocsumRequest;
    typedef search::engine::DocsumReply   DocsumReply;
    typedef search::engine::DocsumClient  DocsumClient;

private:
    FastS_AppContext          *_appCtx;
    DocsumRequest::Source      _request;
    DocsumClient              &_client;

    // internal docsum related state
    search::docsummary::GetDocsumArgs  _args;
    uint32_t                           _hitcnt;
    FastS_hitresult                   *_hitbuf;
    FastS_DataSetCollection           *_dsc;
    FastS_ISearch                     *_search;
    FastS_DocsumsResult               *_docsumsResult;

    void setupRequest();
    void handleRequest();
    void createReply();
    void writeLog();
    void cleanup();

    virtual void Run(FastOS_ThreadInterface *, void *) override;

public:
    DocsumAdapter(FastS_AppContext *appCtx,
                  DocsumRequest::Source request,
                  DocsumClient &client);
};

} // namespace fdispatch

