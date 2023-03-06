// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "rpctarget.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/fnet/frt/supervisor.h>

namespace mbus {

RPCTarget::RPCTarget(const string &spec, FRT_Supervisor &orb) :
    _lock(),
    _orb(orb),
    _name(spec),
    _target(*_orb.GetTarget(spec.c_str())),
    _state(VERSION_NOT_RESOLVED),
    _version(),
    _versionHandlers()
{
    // empty
}

RPCTarget::~RPCTarget()
{
    _target.internal_subref();
}

void
RPCTarget::resolveVersion(duration timeout, RPCTarget::IVersionHandler &handler)
{
    bool shouldInvoke = false;
    ResolveState state = _state.load(std::memory_order_acquire);
    bool hasVersion = (state == VERSION_RESOLVED);
    if ( ! hasVersion ) {
        std::unique_lock guard(_lock);
        state = _state.load(std::memory_order_relaxed);
        if (state == VERSION_RESOLVED || state == PROCESSING_HANDLERS) {
            while (_state.load(std::memory_order_relaxed) == PROCESSING_HANDLERS) {
                _cond.wait(guard);
            }
            hasVersion = true;
        } else {
            _versionHandlers.push_back(&handler);
            if (_state != TARGET_INVOKED) {
                _state = TARGET_INVOKED;
                shouldInvoke = true;
            }
        }
    }
    if (hasVersion) {
        handler.handleVersion(_version.get());
    } else if (shouldInvoke) {
        FRT_RPCRequest *req = _orb.AllocRPCRequest();
        req->SetMethodName("mbus.getVersion");
        _target.InvokeAsync(req, vespalib::to_s(timeout), this);
    }
}

bool
RPCTarget::isValid() const
{
    if (_target.IsValid()) {
        return true;
    }
    ResolveState state = _state.load(std::memory_order_relaxed);
    if (state == TARGET_INVOKED || state == PROCESSING_HANDLERS) {
        return true; // keep alive until RequestDone() is called
    }
    return false;
}

void
RPCTarget::RequestDone(FRT_RPCRequest *req)
{
    HandlerList handlers;
    {
        std::lock_guard guard(_lock);
        assert(_state == TARGET_INVOKED);
        if (req->CheckReturnTypes("s")) {
            FRT_Values &val = *req->GetReturn();
            try {
                _version = std::make_unique<vespalib::Version>(val[0]._string._str);
            } catch (vespalib::IllegalArgumentException &e) {
                (void)e;
            }
        } else if (req->GetErrorCode() == FRTE_RPC_NO_SUCH_METHOD) {
            // Talking to a non-messagebus RPC endpoint. _version remains nullptr.
        }
        _versionHandlers.swap(handlers);
        _state = PROCESSING_HANDLERS;
    }
    for (IVersionHandler * handler : handlers) {
        handler->handleVersion(_version.get());
    }
    {
        std::lock_guard guard(_lock);
        _state = (_version.get() ? VERSION_RESOLVED : VERSION_NOT_RESOLVED);
    }
    _cond.notify_all();
    req->internal_subref();
}

} // namespace mbus
