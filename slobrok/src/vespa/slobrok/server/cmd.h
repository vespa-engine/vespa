// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ok_state.h"

class FRT_RPCRequest;

namespace slobrok {

class SBEnv;

struct RegRpcSrvData;

/**
 * @class RegRpcSrvCommand
 * @brief Small "script" to handle the various stages of a registration
 *
 * XXX should change name, also used for other tasks
 **/
class RegRpcSrvCommand
{
private:
    RegRpcSrvData *_data;
    RegRpcSrvCommand(RegRpcSrvData *data) : _data(data) {}
    void cleanupReservation();
public:
    virtual void doneHandler(OkState result);
    void doRequest();

    RegRpcSrvCommand(const RegRpcSrvCommand &rhs)
        : _data(rhs._data)
    {
    }

    virtual ~RegRpcSrvCommand() {}

    RegRpcSrvCommand& operator=(const RegRpcSrvCommand &rhs)
    {
        _data = rhs._data;
        return *this;
    }

    static RegRpcSrvCommand makeRegRpcSrvCmd(SBEnv &env,
                                             const char *name,
                                             const char *spec,
                                             FRT_RPCRequest *req);
    static RegRpcSrvCommand makeRemRemCmd(SBEnv &env,
                                          const char *name,
                                          const char *spec);
};

//-----------------------------------------------------------------------------

} // namespace slobrok

