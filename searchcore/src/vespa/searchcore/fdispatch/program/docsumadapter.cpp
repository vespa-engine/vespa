// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumadapter.h"
#include <vespa/searchcore/fdispatch/search/datasetcollection.h>

#include <vespa/log/log.h>
LOG_SETUP(".fdispatch.docsumadapter");

namespace fdispatch {

void
DocsumAdapter::setupRequest()
{
    const DocsumRequest &req = *_request.get();
    _args.initFromDocsumRequest(req);
    _hitcnt = req.hits.size();
    LOG(debug, "DocsumAdapter::setupRequest : hitcnt=%d", _hitcnt);
    if (_hitcnt > 0) {
        _hitbuf = (FastS_hitresult *) malloc(req.hits.size() * sizeof(FastS_hitresult));
    }
    for (uint32_t i = 0; i < _hitcnt; i++) {
        _hitbuf[i]._gid       = req.hits[i].gid;
        _hitbuf[i]._partition = req.hits[i].path;
        LOG(debug, "DocsumAdapter::setupRequest : hit[%d] (gid=%s,part=%d)",
            i, _hitbuf[i]._gid.toString().c_str(), _hitbuf[i]._partition);
    }
}

void
DocsumAdapter::handleRequest()
{
    _dsc = _appCtx->GetDataSetCollection();
    assert(_dsc != NULL);
    _search = _dsc->CreateSearch(FastS_NoID32(), _appCtx->GetTimeKeeper());
    assert(_search != NULL);
    _docsumsResult = _search->GetDocsumsResult();
    _search->SetGetDocsumArgs(&_args);
    _search->GetDocsums(_hitbuf, _hitcnt);
    _search->ProcessDocsumsDone();
}

void
DocsumAdapter::createReply()
{
    DocsumReply::UP reply(new DocsumReply());
    DocsumReply &r = *reply;

    FastS_fullresult *hitbuf = _docsumsResult->_fullresult;
    uint32_t          hitcnt = _docsumsResult->_fullResultCount;

    LOG(debug, "DocsumAdapter::createReply : hitcnt=%d", hitcnt);
    r.docsums.reserve(hitcnt);
    for (uint32_t i = 0; i < hitcnt; i++) {
        if ( ! hitbuf[i]._buf.empty() ) {
            r.docsums.push_back(DocsumReply::Docsum());
            DocsumReply::Docsum & d = r.docsums.back();
            d.docid = hitbuf[i]._docid;
            d.gid = hitbuf[i]._gid;
            d.data.swap(hitbuf[i]._buf);
        } else {
            LOG(debug, "DocsumAdapter::createReply : No buf for hit=%d", i);
        }
    }
    r.request = _request.release();
    _client.getDocsumsDone(std::move(reply));
}

void
DocsumAdapter::writeLog()
{
    // no access log for docsums
}

void
DocsumAdapter::cleanup()
{
    if (_search != NULL) {
        _search->Free();
    }
    if (_dsc != NULL) {
        _dsc->subRef();
    }
    free(_hitbuf);
    _hitcnt = 0;
    _hitbuf = 0;
}

void
DocsumAdapter::Run(FastOS_ThreadInterface *, void *)
{
    setupRequest();
    handleRequest();
    createReply();
    writeLog();
    cleanup();
    delete this;
}

DocsumAdapter::DocsumAdapter(FastS_AppContext *appCtx,
                             DocsumRequest::Source request,
                             DocsumClient &client)
    : _appCtx(appCtx),
      _request(std::move(request)),
      _client(client),
      _args(),
      _hitcnt(0),
      _hitbuf(0),
      _dsc(0),
      _search(0),
      _docsumsResult(0)
{
}

} // namespace fdispatch
