// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcrequestwrapper.h"
#include <vespa/fnet/frt/rpcrequest.h>
#include <cassert>

namespace storage {

RPCRequestWrapper::RPCRequestWrapper(FRT_RPCRequest *req)
    :       _req(req)
{
}

RPCRequestWrapper::~RPCRequestWrapper()
{
    if (_req != 0) {
        _req->SetError(ERR_REQUEST_DELETED, "Request deleted without having been replied to");
        _req->Return();
    }
}

const char *
RPCRequestWrapper::getParam() const
{
    assert(_req != 0);
    return _req->GetParams()->GetValue(0)._data._buf;
}


uint32_t
RPCRequestWrapper::getParamLen() const
{
    assert(_req != 0);
    return _req->GetParams()->GetValue(0)._data._len;
}


void
RPCRequestWrapper::returnData(const char *pt, uint32_t len)
{
    assert(_req != 0);
    _req->GetReturn()->AddData(pt, len);
    _req->Return();
    _req = 0;
}


void
RPCRequestWrapper::returnError(uint32_t errorCode, const char *errorMessage)
{
    assert(_req != 0);
    _req->SetError(errorCode, errorMessage);
    _req->Return();
    _req = 0;
}

void
RPCRequestWrapper::addReturnString(const char *str, uint32_t len)
{
    assert(_req != 0);
    if (len !=0) {
        _req->GetReturn()->AddString(str, len);
    } else {
        _req->GetReturn()->AddString(str);
    }
}

void
RPCRequestWrapper::addReturnInt(uint32_t value)
{
    assert(_req != 0);
    _req->GetReturn()->AddInt32(value);
}

void
RPCRequestWrapper::returnRequest()
{
    assert(_req != 0);
    _req->Return();
    _req = 0;

}

const char *
RPCRequestWrapper::getMethodName() const {
    return _req->GetMethodName();
}

void
RPCRequestWrapper::discardBlobs()
{
    if (_req != 0) {
        _req->DiscardBlobs();
    }
}

} // namespace storage
