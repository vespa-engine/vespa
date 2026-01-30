// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "peer-check.h"
#include "status-callback.h"

namespace config::sentinel {

/**
 * Handles a checkConnectivity request by making an outgoing
 * ping request.  When the ping finishes, fills an answer
 * into the parent request and send the answer back.
 **/
class CheckCompletionHandler : public StatusCallback {
private:
    FRT_RPCRequest *_parentRequest;

public:
    CheckCompletionHandler(FRT_RPCRequest *parentRequest);
    virtual ~CheckCompletionHandler();
    void returnStatus(bool ok) override;
};

} // namespace config::sentinel
