// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/engine/searchapi.h>
#include <vespa/searchcore/fdispatch/common/appcontext.h>
#include <vespa/searchcore/fdispatch/common/search.h>

namespace fdispatch {

/**
 * Implementation of the common search api for the fdispatch server
 * application.
 **/
class SearchAdapter : public FastOS_Runnable
{
public:
    typedef search::engine::SearchRequest SearchRequest;
    typedef search::engine::SearchReply   SearchReply;
    typedef search::engine::SearchClient  SearchClient;

private:
    FastS_AppContext          *_appCtx;
    SearchRequest::Source      _request;
    SearchClient              &_client;

    // internal search related state
    FastS_DataSetCollection   *_dsc;
    FastS_ISearch             *_search;
    FastS_SearchInfo          *_searchInfo;
    FastS_QueryResult         *_queryResult;

    void handleRequest();
    SearchReply::UP createReply();
    void writeLog();
    void cleanup();

    void Run(FastOS_ThreadInterface *, void *) override;

public:
    SearchAdapter(FastS_AppContext *appCtx,
                  SearchRequest::Source request,
                  SearchClient &client);
};

} // namespace fdispatch

