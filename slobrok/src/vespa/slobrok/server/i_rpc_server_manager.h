// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>

class FRT_Supervisor;

namespace slobrok {

class ManagedRpcServer;

//-----------------------------------------------------------------------------

/**
 * @class IRpcServerManager
 * @brief A manager for ManagedRpcServer objects.
 *
 * Interface class.
 **/

class IRpcServerManager
{
public:
    virtual void notifyFailedRpcSrv(ManagedRpcServer *rpcsrv, std::string errmsg) = 0;
    virtual void notifyOkRpcSrv(ManagedRpcServer *rpcsrv) = 0;
    virtual FRT_Supervisor *getSupervisor() = 0;
    virtual ~IRpcServerManager() {}
};

//-----------------------------------------------------------------------------

} // namespace slobrok
