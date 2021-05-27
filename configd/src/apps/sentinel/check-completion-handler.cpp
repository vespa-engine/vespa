// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "check-completion-handler.h"
#include <vespa/log/log.h>
LOG_SETUP(".check-completion-handler");

namespace config::sentinel {

void CheckCompletionHandler::create(Cmd::UP request, FRT_Supervisor &orb) {
    LOG_ASSERT(request->type() == Cmd::CHECK_CONNECTIVITY);
    // will delete itself on completion:
    new CheckCompletionHandler(std::move(request), orb);
}

CheckCompletionHandler::CheckCompletionHandler(Cmd::UP request, FRT_Supervisor &orb)
  : _parentRequest(std::move(request))
{
    const std::string & host = _parentRequest->name();
    int port = _parentRequest->portNumber();
    _peerCheck = std::make_unique<PeerCheck>(*this, host, port, orb);
    
}

CheckCompletionHandler::~CheckCompletionHandler() = default;

void CheckCompletionHandler::returnStatus(bool ok) {
    _parentRequest->retValue(ok ? "ok" : "bad");
    delete this;
}

}
