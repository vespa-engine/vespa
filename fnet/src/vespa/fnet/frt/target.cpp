// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "target.h"

#include "supervisor.h"

#include <vespa/fnet/transport_thread.h>

FRT_Target::~FRT_Target() {
    FNET_Connection* conn(_conn);
    _conn = nullptr;
    if (conn != nullptr) {
        conn->Owner()->Close(conn, /* needref */ false);
    }
}

void FRT_Target::InvokeAsync(FRT_RPCRequest* req, double timeout, FRT_IRequestWait* waiter) {
    FRT_Supervisor::InvokeAsync(_scheduler, _conn, req, timeout, waiter);
}

void FRT_Target::InvokeVoid(FRT_RPCRequest* req) { FRT_Supervisor::InvokeVoid(_conn, req); }

void FRT_Target::InvokeSync(FRT_RPCRequest* req, double timeout) {
    FRT_Supervisor::InvokeSync(_scheduler, _conn, req, timeout);
}
