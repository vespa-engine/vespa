// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "status-callback.h"
#include "peer-check.h"
#include "cmdq.h"

namespace config::sentinel {

/**
 * Handles a checkConnectivity request by making an outgoing
 * ping request.  When the ping finishes, fills an answer
 * into the parent request and deletes itself.
 **/
class CheckCompletionHandler : public StatusCallback {
public:
    static void create(Cmd::UP request, FRT_Supervisor &orb);
    virtual ~CheckCompletionHandler();
    void returnStatus(bool ok) override;
private:
    CheckCompletionHandler(Cmd::UP request, FRT_Supervisor &orb);
    Cmd::UP _parentRequest;
    std::unique_ptr<PeerCheck> _peerCheck;
};

}
