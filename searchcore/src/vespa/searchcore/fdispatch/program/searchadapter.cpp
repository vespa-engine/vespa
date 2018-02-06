// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchadapter.h"
#include <vespa/searchcore/fdispatch/search/datasetcollection.h>
#include <vespa/searchcore/fdispatch/search/dataset_base.h>
#include <vespa/searchcore/fdispatch/search/nodemanager.h>

namespace fdispatch {

void
SearchAdapter::handleRequest()
{
    _dsc = _appCtx->GetDataSetCollection();
    FastS_assert(_dsc != NULL);

    uint32_t dataset = _dsc->SuggestDataSet();

    _search = _dsc->CreateSearch(dataset, _appCtx->GetTimeKeeper());
    FastS_assert(_search != NULL);

    _searchInfo = _search->GetSearchInfo();
    _queryResult = _search->GetQueryResult();
    _search->setSearchRequest(_request.get());
    _search->Search(_request->offset, _request->maxhits, /* minhits */ 0);
    _search->ProcessQueryDone();
}

SearchAdapter::SearchReply::UP
SearchAdapter::createReply()
{
    SearchReply::UP reply(new SearchReply());
    SearchReply &r = *reply;
    r.useWideHits = true; // mld
    if (_search->GetErrorCode() != search::engine::ECODE_NO_ERROR) {
        r.errorCode = _search->GetErrorCode();
        r.errorMessage = _search->GetErrorMessage();
        return reply;
    }

    uint32_t hitcnt = _queryResult->_hitCount;
    r.offset = _searchInfo->_searchOffset;
    r.totalHitCount = _queryResult->_totalHitCount;
    r.maxRank = _queryResult->_maxRank;
    r.setDistributionKey(_appCtx->GetNodeManager()->GetMldDocstamp());

    if (_queryResult->_sortIndex != NULL && hitcnt > 0) {
        r.sortIndex.assign(_queryResult->_sortIndex, _queryResult->_sortIndex + hitcnt + 1);
        r.sortData.assign(_queryResult->_sortData, _queryResult->_sortData + _queryResult->_sortIndex[hitcnt]);
    }

    if (_queryResult->_groupResultLen > 0) {
        r.groupResult.assign(_queryResult->_groupResult,
                             _queryResult->_groupResult + _queryResult->_groupResultLen);
    }

    r.coverage = SearchReply::Coverage(_searchInfo->_activeDocs, _searchInfo->_coverageDocs);
    r.coverage.setSoonActive(_searchInfo->_soonActiveDocs);
    r.coverage.setDegradeReason(_searchInfo->_degradeReason);
    r.coverage.setNodesQueried(_searchInfo->_nodesQueried);
    r.coverage.setNodesReplied(_searchInfo->_nodesReplied);

    FastS_hitresult *hitbuf = _queryResult->_hitbuf;
    r.hits.resize(hitcnt);

    for (uint32_t cur = 0; cur < hitcnt; cur++) {
        r.hits[cur].gid      = hitbuf[cur]._gid;
        r.hits[cur].metric   = hitbuf[cur]._metric;
        r.hits[cur].path     = hitbuf[cur]._partition;
        r.hits[cur].setDistributionKey(hitbuf[cur].getDistributionKey());
    }
    r.request = _request.release();
    return reply;
}

void
SearchAdapter::cleanup()
{
    if (_search != NULL) {
        _search->Free();
    }
    if (_dsc != NULL) {
        _dsc->subRef();
    }
}

void
SearchAdapter::Run(FastOS_ThreadInterface *, void *)
{
    handleRequest();
    SearchReply::UP reply = createReply();
    cleanup();
    _client.searchDone(std::move(reply));
    delete this;
}

SearchAdapter::SearchAdapter(FastS_AppContext *appCtx,
                             SearchRequest::Source request,
                             SearchClient &client)
    : _appCtx(appCtx),
      _request(std::move(request)),
      _client(client),
      _dsc(0),
      _search(0),
      _searchInfo(0),
      _queryResult(0)
{
}

} // namespace fdispatch
