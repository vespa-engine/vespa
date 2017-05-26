// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/fdispatch/common/rpc.h>


class FastS_fdispatch_RPC : public FastS_RPC
{
public:
    FastS_fdispatch_RPC(FastS_AppContext *appCtx)
        : FastS_RPC(appCtx) {}
    virtual ~FastS_fdispatch_RPC() {}

    // Register RPC Methods

    virtual void RegisterMethods(FRT_ReflectionBuilder *rb) override;

    // methods registered by superclass

    virtual void RPC_GetNodeType(FRT_RPCRequest *req) override;

    // methods registered by us

    void RPC_EnableEngine(FRT_RPCRequest *req);
    void RPC_DisableEngine(FRT_RPCRequest *req);
};

