// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcrequestwrapper.h"
#include <vespa/fnet/frt/rpcrequest.h>
#include <cassert>

namespace storage {

RPCRequestWrapper::RPCRequestWrapper(FRT_RPCRequest *req)
    : _req(req)
{
}

RPCRequestWrapper::~RPCRequestWrapper()
{
    if (_req) {
        _req->SetError(ERR_REQUEST_DELETED, "Request deleted without having been replied to");
        _req->Return();
    }
}

const char *
RPCRequestWrapper::getParam() const
{
    assert(_req);
    return _req->GetParams()->GetValue(0)._data._buf;
}


uint32_t
RPCRequestWrapper::getParamLen() const
{
    assert(_req);
    return _req->GetParams()->GetValue(0)._data._len;
}


void
RPCRequestWrapper::returnData(const char *pt, uint32_t len)
{
    assert(_req);
    _req->GetReturn()->AddData(pt, len);
    _req->Return();
    _req = nullptr;
}


void
RPCRequestWrapper::returnError(uint32_t errorCode, const char *errorMessage)
{
    assert(_req);
    _req->SetError(errorCode, errorMessage);
    _req->Return();
    _req = nullptr;
}

void
RPCRequestWrapper::addReturnString(const char *str, uint32_t len)
{
    assert(_req);
    if (len !=0) {
        _req->GetReturn()->AddString(str, len);
    } else {
        _req->GetReturn()->AddString(str);
    }
}

void
RPCRequestWrapper::addReturnInt(uint32_t value)
{
    assert(_req);
    _req->GetReturn()->AddInt32(value);
}

void
RPCRequestWrapper::returnRequest()
{
    assert(_req);
    _req->Return();
    _req = nullptr;

}

const char *
RPCRequestWrapper::getMethodName() const {
    return _req->GetMethodName();
}

void
RPCRequestWrapper::discardBlobs()
{
    if (_req) {
        _req->DiscardBlobs();
    }
}

} // namespace storage
