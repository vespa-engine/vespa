// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <mutex>
#include <vector>

namespace proton {

class DetachedRpcRequest;

/*
 * Owner of rpc request to proton rpc interface that has been detached. It must be closed when rpc interface is closed.
 */
class DetachedRpcRequestsOwner {
    using DetachedRequests = std::vector<std::shared_ptr<DetachedRpcRequest>>;
    std::mutex _lock;
    DetachedRequests _detached_requests;
    bool _closed;
public:
    DetachedRpcRequestsOwner();
    ~DetachedRpcRequestsOwner();
    bool add_detached_request(std::shared_ptr<DetachedRpcRequest> request);
    void remove_detached_request(std::shared_ptr<DetachedRpcRequest> request);
    void close();
};

}
