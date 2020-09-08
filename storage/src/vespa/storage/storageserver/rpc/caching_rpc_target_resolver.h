// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "rpc_target.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <memory>
#include <shared_mutex>

namespace storage {

namespace api { class StorageMessageAddress; }

namespace rpc {

class SharedRpcResources;

class CachingRpcTargetResolver {
    SharedRpcResources& _rpc_resources;
    mutable std::shared_mutex _targets_rwmutex;
    // TODO LRU? Size cap?
    vespalib::hash_map<vespalib::string, std::shared_ptr<RpcTarget>> _targets;
public:
    // TODO pass explicit slobrok mirror interface and supervisor to make testing easier
    // TODO consider wrapping supervisor to make testing easier
    explicit CachingRpcTargetResolver(SharedRpcResources& rpc_resources);
    ~CachingRpcTargetResolver();

    std::shared_ptr<RpcTarget> resolve_rpc_target(const api::StorageMessageAddress& address);
};

} // rpc
} // storage
