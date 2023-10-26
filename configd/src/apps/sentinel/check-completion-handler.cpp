// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "check-completion-handler.h"

namespace config::sentinel {

CheckCompletionHandler::CheckCompletionHandler(FRT_RPCRequest *parent)
  : _parentRequest(parent)
{
}

CheckCompletionHandler::~CheckCompletionHandler() = default;

void CheckCompletionHandler::returnStatus(bool ok) {
    FRT_Values *dst = _parentRequest->GetReturn();
    dst->AddString(ok ? "ok" : "bad");
    _parentRequest->Return();
}

}
