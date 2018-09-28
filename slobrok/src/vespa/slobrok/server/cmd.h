// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ok_state.h"
#include <memory>

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
    std::unique_ptr<RegRpcSrvData> _data;
    RegRpcSrvCommand(std::unique_ptr<RegRpcSrvData> data);
    void cleanupReservation();
public:
    virtual void doneHandler(OkState result);
    void doRequest();

    RegRpcSrvCommand(RegRpcSrvCommand &&);
    RegRpcSrvCommand & operator=(RegRpcSrvCommand &&);
    virtual ~RegRpcSrvCommand();

    static RegRpcSrvCommand makeRegRpcSrvCmd(SBEnv &env, const std::string &name, const std::string &spec, FRT_RPCRequest *req);
    static RegRpcSrvCommand makeRemRemCmd(SBEnv &env, const std::string &name, const std::string &spec);
};

//-----------------------------------------------------------------------------

} // namespace slobrok

