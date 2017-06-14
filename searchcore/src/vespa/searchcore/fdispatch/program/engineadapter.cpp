// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "engineadapter.h"
#include "searchadapter.h"
#include "docsumadapter.h"
#include <vespa/searchcore/fdispatch/search/child_info.h>
#include <vespa/searchcore/fdispatch/search/nodemanager.h>
#include <vespa/searchcore/fdispatch/search/dataset_base.h>

#include <vespa/log/log.h>
LOG_SETUP(".fdispatch.engineadapter");

namespace fdispatch {

EngineAdapter::
EngineAdapter(FastS_AppContext *appCtx, FastOS_ThreadPool *threadPool)
    : _appCtx(appCtx),
      _mypool(threadPool)
{
}

EngineAdapter::SearchReply::UP
EngineAdapter::search(SearchRequest::Source request, SearchClient &client)
{
    SearchAdapter *sa = new SearchAdapter(_appCtx, std::move(request), client);
    if ((_mypool == 0) || (_mypool->NewThread(sa) == 0)) {
        delete sa;
        LOG(error, "could not allocate thread for incoming search request");
        SearchReply::UP reply(new SearchReply());
        reply->useWideHits = true; // mld
        reply->errorCode = search::engine::ECODE_OVERLOADED;
        reply->errorMessage = "could not allocate thread for query";
        return reply;
    }
    return SearchReply::UP();
}

EngineAdapter::DocsumReply::UP
EngineAdapter::getDocsums(DocsumRequest::Source request, DocsumClient &client)
{
    DocsumAdapter *da = new DocsumAdapter(_appCtx, std::move(request), client);
    if ((_mypool == 0) || (_mypool->NewThread(da) == 0)) {
        delete da;
        LOG(error, "could not allocate thread for incoming docsum request");
        return DocsumReply::UP(new DocsumReply());
    }
    return DocsumReply::UP();
}

EngineAdapter::MonitorReply::UP
EngineAdapter::ping(MonitorRequest::UP request, MonitorClient &)
{
    MonitorReply::UP reply(new MonitorReply());
    MonitorReply &mr = *reply;

    uint32_t timeStamp = 0;
    FastS_NodeManager *nm = _appCtx->GetNodeManager();

    ChildInfo ci = nm->getChildInfo();
    timeStamp = nm->GetMldDocstamp();
    // TODO: Report softoffline upwards when fdispatch has been requested
    // to go down in a controlled manner (along with zero docstamp).
    mr.partid      = nm->GetMldPartition();
    mr.timestamp   = timeStamp;
    mr.mld         = true;
    mr.totalNodes  = ci.maxNodes;
    mr.activeNodes = ci.activeNodes;
    mr.totalParts  = ci.maxParts;
    mr.activeParts = ci.activeParts;
    if (ci.activeDocs.valid) {
        mr.activeDocs = ci.activeDocs.count;
        mr.activeDocsRequested = request->reportActiveDocs;
    }
    return reply;
}

} // namespace fdispatch
