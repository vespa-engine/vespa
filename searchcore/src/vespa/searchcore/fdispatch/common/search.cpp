// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "search.h"

//---------------------------------------------------------------------

FastS_SearchAdapter::FastS_SearchAdapter(FastS_ISearch *search)
    : _search(search)
{
}


FastS_SearchAdapter::~FastS_SearchAdapter()
{
}


bool
FastS_SearchAdapter::IsAsync()
{
    return _search->IsAsync();
}


uint32_t
FastS_SearchAdapter::GetDataSetID()
{
    return _search->GetDataSetID();
}


FastS_SearchInfo *
FastS_SearchAdapter::GetSearchInfo()
{
    return _search->GetSearchInfo();
}


FastS_ISearch::RetCode
FastS_SearchAdapter::SetAsyncArgs(FastS_ISearchOwner *owner,
                                  FastS_SearchContext context)
{
    return _search->SetAsyncArgs(owner, context);
}


FastS_ISearch::RetCode
FastS_SearchAdapter::setSearchRequest(const search::engine::SearchRequest * request)
{
    return _search->setSearchRequest(request);
}


FastS_ISearch::RetCode
FastS_SearchAdapter::SetGetDocsumArgs(search::docsummary::GetDocsumArgs *docsumArgs)
{
    return _search->SetGetDocsumArgs(docsumArgs);
}


FastS_ISearch::RetCode
FastS_SearchAdapter::Search(uint32_t searchOffset,
                            uint32_t maxhits, uint32_t minhits)
{
    return _search->Search(searchOffset, maxhits, minhits);
}


FastS_ISearch::RetCode
FastS_SearchAdapter::ProcessQueryDone()
{
    return _search->ProcessQueryDone();
}


FastS_QueryResult *
FastS_SearchAdapter::GetQueryResult()
{
    return _search->GetQueryResult();
}


FastS_ISearch::RetCode
FastS_SearchAdapter::GetDocsums(const FastS_hitresult *hits, uint32_t hitcnt)
{
    return _search->GetDocsums(hits, hitcnt);
}


FastS_ISearch::RetCode
FastS_SearchAdapter::ProcessDocsumsDone()
{
    return _search->ProcessDocsumsDone();
}


FastS_DocsumsResult *
FastS_SearchAdapter::GetDocsumsResult()
{
    return _search->GetDocsumsResult();
}


search::engine::ErrorCode
FastS_SearchAdapter::GetErrorCode()
{
    return _search->GetErrorCode();
}


const char *
FastS_SearchAdapter::GetErrorMessage()
{
    return _search->GetErrorMessage();
}


void
FastS_SearchAdapter::Interrupt()
{
    _search->Interrupt();
}


void
FastS_SearchAdapter::Free()
{
    _search->Free();
    delete this;
}

//---------------------------------------------------------------------

FastS_SyncSearchAdapter::FastS_SyncSearchAdapter(FastS_ISearch *search)
    : FastS_SearchAdapter(search),
      _cond(),
      _waitQuery(false),
      _queryDone(false),
      _waitDocsums(false),
      _docsumsDone(false)
{
}


FastS_SyncSearchAdapter::~FastS_SyncSearchAdapter()
{
}



FastS_ISearch *
FastS_SyncSearchAdapter::Adapt(FastS_ISearch *search)
{
    if (!search->IsAsync())
        return search;

    FastS_SyncSearchAdapter *ret = new FastS_SyncSearchAdapter(search);
    search->SetAsyncArgs(ret, FastS_SearchContext());
    return ret;
}



void
FastS_SyncSearchAdapter::DoneQuery(FastS_ISearch *,
                                   FastS_SearchContext)
{
    Lock();
    _queryDone = true;
    if (_waitQuery)
        Signal();
    Unlock();
}


void
FastS_SyncSearchAdapter::DoneDocsums(FastS_ISearch *,
                                     FastS_SearchContext)
{
    Lock();
    _docsumsDone = true;
    if (_waitDocsums)
        Signal();
    Unlock();
}


void
FastS_SyncSearchAdapter::WaitQueryDone()
{
    Lock();
    _waitQuery = true;
    while (!_queryDone)
        Wait();
    Unlock();
}


void
FastS_SyncSearchAdapter::WaitDocsumsDone()
{
    Lock();
    _waitDocsums = true;
    while (!_docsumsDone)
        Wait();
    Unlock();
}


bool
FastS_SyncSearchAdapter::IsAsync()
{
    return false;
}


FastS_ISearch::RetCode
FastS_SyncSearchAdapter::SetAsyncArgs(FastS_ISearchOwner *,
                                      FastS_SearchContext)
{
    return RET_ERROR;
}


FastS_ISearch::RetCode
FastS_SyncSearchAdapter::Search(uint32_t searchOffset,
                                uint32_t maxhits, uint32_t minhits)
{
    RetCode res = _search->Search(searchOffset, maxhits, minhits);
    if (res == RET_INPROGRESS) {
        WaitQueryDone();
    }
    return (res == RET_ERROR) ? RET_ERROR : RET_OK;
}


FastS_ISearch::RetCode
FastS_SyncSearchAdapter::GetDocsums(const FastS_hitresult *hits, uint32_t hitcnt)
{
    RetCode res = _search->GetDocsums(hits, hitcnt);
    if (res == RET_INPROGRESS) {
        WaitDocsumsDone();
    }
    return (res == RET_ERROR) ? RET_ERROR : RET_OK;
}
