// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "detached_rpc_requests_owner.h"
#include "detached_rpc_request.h"
#include <algorithm>
#include <cassert>

namespace proton {

DetachedRpcRequestsOwner::DetachedRpcRequestsOwner()
    : _lock(),
      _detached_requests(),
      _closed(false)
{
}

DetachedRpcRequestsOwner::~DetachedRpcRequestsOwner()
{
    close();
}

bool
DetachedRpcRequestsOwner::add_detached_request(std::shared_ptr<DetachedRpcRequest> request)
{
    std::unique_lock guard(_lock);
    if (_closed) {
        return false;
    }
    if (request->detached_request_removed()) {
        return false; // Add after remove is not allowed
    }
    auto it = std::find(_detached_requests.begin(), _detached_requests.end(), request);
    assert(it == _detached_requests.end());
    _detached_requests.push_back(request);
    return true;
}

void
DetachedRpcRequestsOwner::remove_detached_request(std::shared_ptr<DetachedRpcRequest> request)
{
    std::unique_lock guard(_lock);
    request->detached_request_removed() = true;
    auto it = std::find(_detached_requests.begin(), _detached_requests.end(), request);
    if (it != _detached_requests.end()) {
        _detached_requests.erase(it);
    }
}

void
DetachedRpcRequestsOwner::close()
{
    std::unique_lock guard(_lock);
    _closed = true;
    DetachedRequests detached_requests;
    _detached_requests.swap(detached_requests);
    guard.unlock();
    for (auto& req : detached_requests) {
        auto req_destroyed = req->owner_aborted();
        req.reset();
        req_destroyed.wait();
    }
}

}
