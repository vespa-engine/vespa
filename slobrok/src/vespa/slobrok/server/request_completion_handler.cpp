// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "request_completion_handler.h"
#include <vespa/fnet/frt/rpcrequest.h>

namespace slobrok {

RequestCompletionHandler::RequestCompletionHandler(FRT_RPCRequest *parent)
    : _parentRequest(parent)
{
}

RequestCompletionHandler::~RequestCompletionHandler() {
    if (_parentRequest) {
        _parentRequest->SetError(FRTE_RPC_METHOD_FAILED, "removed before completion");
        _parentRequest->Return();
    }
}

void RequestCompletionHandler::doneHandler(OkState result) {
    if (auto req = _parentRequest) {
        _parentRequest = nullptr;
        if (result.failed()) {
            req->SetError(FRTE_RPC_METHOD_FAILED, result.errorMsg.c_str());
        }
        req->Return();
    }
}

}
