// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

class FRT_RPCRequest;

class FRT_Invokable
{
public:
    virtual ~FRT_Invokable() {}
};

typedef void (FRT_Invokable::*FRT_METHOD_PT)(FRT_RPCRequest *);

#define FRT_METHOD(pt) ((FRT_METHOD_PT) &pt)

