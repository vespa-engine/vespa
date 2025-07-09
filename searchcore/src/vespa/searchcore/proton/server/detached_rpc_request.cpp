// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "detached_rpc_request.h"
#include "detached_rpc_requests_owner.h"
#include <cassert>

namespace proton {

DetachedRpcRequest::DetachedRpcRequest(std::shared_ptr<DetachedRpcRequestsOwner> owner,
                                       vespalib::ref_counted<FRT_RPCRequest> req)
    : _lock(),
      _owner(std::move(owner)),
      _req(std::move(req)),
      _conn(vespalib::ref_counted_from(*_req->GetConnection())),
      _promise(),
      _detached_request_removed(false)
{
}

DetachedRpcRequest::~DetachedRpcRequest()
{
    // Already removed from owner, or destructor would not have been called
    _req.reset();
    _promise.set_value(); // Signals DetachedRpcRequestsOwner::close that request is done.
}

bool
DetachedRpcRequest::add_to_owner(std::shared_ptr<DetachedRpcRequest> self)
{
    assert(this == self.get());
    auto owner = _owner.lock();
    return owner ? owner->add_detached_request(std::move(self)) : false;
}

void
DetachedRpcRequest::remove_from_owner(std::shared_ptr<DetachedRpcRequest> self)
{
    assert(this == self.get());
    auto owner = _owner.lock();
    if (owner) {
        owner->remove_detached_request(std::move(self));
    }
}

}
