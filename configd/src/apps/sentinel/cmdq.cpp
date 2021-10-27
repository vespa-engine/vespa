// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cmdq.h"
#include <vespa/fnet/frt/rpcrequest.h>

namespace config::sentinel {

Cmd::~Cmd()
{
    _req->Return();
}

void
Cmd::retError(const char *errorString) const
{
    _req->SetError(FRTE_RPC_METHOD_FAILED, errorString);
}

void
Cmd::retValue(const char *valueString) const
{
    FRT_Values *dst = _req->GetReturn();
    dst->AddString(valueString);
}

}
