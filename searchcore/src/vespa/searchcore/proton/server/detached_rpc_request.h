// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fnet/connection.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/vespalib/util/ref_counted.h>
#include <atomic>
#include <future>
#include <mutex>

namespace proton {

class DetachedRpcRequestsOwner;

/*
 * Rpc request to proton rpc interface that has been detached. It must be aborted when rpc interface is closed.
 */
class DetachedRpcRequest {
protected:
    std::mutex _lock;
    std::weak_ptr<DetachedRpcRequestsOwner> _owner;
    vespalib::ref_counted<FRT_RPCRequest> _req;
    vespalib::ref_counted<FNET_Connection> _conn;
    std::promise<void> _promise;
    bool _detached_request_removed; // protected by owner mutex
public:
    DetachedRpcRequest(std::shared_ptr<DetachedRpcRequestsOwner> owner,
                       vespalib::ref_counted<FRT_RPCRequest> req);
    virtual ~DetachedRpcRequest();
    [[nodiscard]] bool add_to_owner(std::shared_ptr<DetachedRpcRequest> self);
    void remove_from_owner(std::shared_ptr<DetachedRpcRequest> self);
    [[nodiscard]] virtual std::future<void> owner_aborted() = 0;
    [[nodiscard]] bool detached_request_removed() const noexcept { return _detached_request_removed; }
    void set_detached_request_removed() noexcept { _detached_request_removed = true; }
};

}
