// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/fnet/frt/frt.h>
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

