// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "peer-check.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/log/log.h>

LOG_SETUP(".sentinel.peer-check");

using vespalib::make_string_short::fmt;

namespace config::sentinel {

PeerCheck::PeerCheck(StatusCallback &callback, const std::string &host, int port, FRT_Supervisor &orb, int timeout_ms)
  : _callback(callback),
    _hostname(host),
    _portnum(port),
    _target(nullptr),
    _req(nullptr),
    _statusOk(false)
{
    auto spec = fmt("tcp/%s:%d", _hostname.c_str(), _portnum);
    _target = orb.GetTarget(spec.c_str());
    _req = orb.AllocRPCRequest();
    _req->SetMethodName("frt.rpc.ping");
    _target->InvokeAsync(_req, timeout_ms * 0.001, this);
}

PeerCheck::~PeerCheck() {
    LOG_ASSERT(_req == nullptr);
    LOG_ASSERT(_target == nullptr);
}

void PeerCheck::RequestDone(FRT_RPCRequest *req) {
    LOG_ASSERT(req == _req);
    if (req->IsError()) {
        LOG(debug, "error on ping to %s [port %d]: %s (%d)", _hostname.c_str(), _portnum,
            req->GetErrorMessage(), req->GetErrorCode());
    } else {
        LOG(debug, "OK ping to %s [port %d]", _hostname.c_str(), _portnum);
        _statusOk = true;
    }
    _req->internal_subref();
    _req = nullptr;
    _target->internal_subref();
    _target = nullptr;
    // Note: will delete this object, so must be called as final step:
    _callback.returnStatus(_statusOk);
}

}
