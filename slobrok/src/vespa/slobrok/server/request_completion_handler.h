// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ok_state.h"

class FRT_RPCRequest;

namespace slobrok {

/**
 * Interface used to signal the result of LocalRpcMonitorMap::addLocal()
 **/
struct CompletionHandler {
    virtual void doneHandler(OkState result) = 0;
    virtual ~CompletionHandler() {}
};

class RequestCompletionHandler : public CompletionHandler {
private:
    FRT_RPCRequest *_parentRequest;
public:
    RequestCompletionHandler(FRT_RPCRequest *parentRequest);
    virtual ~RequestCompletionHandler();
    void doneHandler(OkState result) override;
};

}
