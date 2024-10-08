// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <string>

namespace storage::rpc {

class RpcTarget;

/**
 * Factory for creating instances of RpcTarget based on a connection spec.
 */
class RpcTargetFactory {
public:
    virtual ~RpcTargetFactory() = default;
    virtual std::unique_ptr<RpcTarget> make_target(const std::string& connection_spec) const = 0;
};

}

