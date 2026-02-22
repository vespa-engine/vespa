// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "request_access_filter.h"

#include <vespa/vespalib/net/tls/capability_set.h>

#include <memory>

/**
 * An RPC access filter which verifies that a request is associated with an auth
 * context that contains, at minimum, a given set of capabilities. If one or more
 * required capabilities are missing, the request is denied.
 */
class FRT_RequireCapabilities final : public FRT_RequestAccessFilter {
    vespalib::net::tls::CapabilitySet _required_capabilities;

public:
    explicit constexpr FRT_RequireCapabilities(vespalib::net::tls::CapabilitySet required_capabilities) noexcept
        : _required_capabilities(required_capabilities) {}

    bool allow(FRT_RPCRequest& req) const noexcept override;

    static std::unique_ptr<FRT_RequireCapabilities> of(vespalib::net::tls::Capability required_capability);
    static std::unique_ptr<FRT_RequireCapabilities> of(vespalib::net::tls::CapabilitySet required_capabilities);
};
