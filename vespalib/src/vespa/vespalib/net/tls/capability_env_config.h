// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace vespalib::net::tls {

enum class CapabilityEnforcementMode {
    Disable,
    LogOnly,
    Enforce
};

const char* to_string(CapabilityEnforcementMode mode) noexcept;

CapabilityEnforcementMode capability_enforcement_mode_from_env() noexcept;

}
