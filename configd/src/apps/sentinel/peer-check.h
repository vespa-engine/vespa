// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "status-callback.h"
#include <string>
#include <vespa/fnet/frt/invoker.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>

namespace config::sentinel {

class PeerCheck : public FRT_IRequestWait
{
public:
    PeerCheck(StatusCallback &callback, const std::string &host, int portnum, FRT_Supervisor &orb, int timeout_ms);
    ~PeerCheck();

    bool okStatus() const { return _statusOk; }
    const std::string& getHostname() const { return _hostname; }

    PeerCheck(const PeerCheck &) = delete;
    PeerCheck(PeerCheck &&) = delete;
    PeerCheck& operator= (const PeerCheck &) = delete;
    PeerCheck& operator= (PeerCheck &&) = delete;

    /** from FRT_IRequestWait **/
    void RequestDone(FRT_RPCRequest *req) override;
private:
    StatusCallback &_callback;
    std::string     _hostname;
    int             _portnum;
    FRT_Target     *_target;
    FRT_RPCRequest *_req;
    bool            _statusOk;
};

}
