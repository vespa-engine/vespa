// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "file_acquirer.h"
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>

#include <vespa/log/log.h>
LOG_SETUP(".config.file_acquirer");

namespace config {

RpcFileAcquirer::RpcFileAcquirer(const vespalib::string &spec)
    : _orb(std::make_unique<FRT_Supervisor>()),
      _spec(spec)
{
    _orb->Start();
}

vespalib::string
RpcFileAcquirer::wait_for(const vespalib::string &file_ref, double timeout_s)
{
    vespalib::string path;
    FRT_Target *target = _orb->GetTarget(_spec.c_str());
    FRT_RPCRequest *req = _orb->AllocRPCRequest();
    req->SetMethodName("waitFor");
    req->GetParams()->AddString(file_ref.data(), file_ref.size());
    target->InvokeSync(req, timeout_s);
    if(req->CheckReturnTypes("s")) {
        path = req->GetReturn()->GetValue(0)._string._str;
    } else {
        LOG(warning, "could not acquire file '%s' (%d: %s)",
            file_ref.c_str(), req->GetErrorCode(), req->GetErrorMessage());
    }
    req->SubRef();
    target->SubRef();
    return path;
}

RpcFileAcquirer::~RpcFileAcquirer()
{
    _orb->ShutDown(true);
}

} // namespace config
