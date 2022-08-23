// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "capability_env_config.h"
#include <vespa/vespalib/stllike/string.h>
#include <cstdlib>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.net.tls.capability_env_config");

namespace vespalib::net::tls {

namespace {

CapabilityEnforcementMode parse_enforcement_mode_from_env() noexcept {
    const char* env = getenv("VESPA_TLS_CAPABILITIES_ENFORCEMENT_MODE");
    vespalib::string mode = env ? env : "";
    if (mode == "enforce") {
        return CapabilityEnforcementMode::Enforce;
    } else if (mode == "log_only") {
        return CapabilityEnforcementMode::LogOnly;
    } else if (mode == "disable") {
        return CapabilityEnforcementMode::Disable;
    } else if (!mode.empty()) {
        LOG(warning, "VESPA_TLS_CAPABILITIES_ENFORCEMENT_MODE environment variable has "
                     "an unsupported value (%s). Falling back to 'enforce'", mode.c_str());
    }
    return CapabilityEnforcementMode::Enforce;
}

}

const char* to_string(CapabilityEnforcementMode mode) noexcept {
    switch (mode) {
    case CapabilityEnforcementMode::Enforce: return "Enforce";
    case CapabilityEnforcementMode::LogOnly: return "LogOnly";
    case CapabilityEnforcementMode::Disable: return "Disable";
    }
    abort();
}

CapabilityEnforcementMode capability_enforcement_mode_from_env() noexcept {
    static const CapabilityEnforcementMode mode = parse_enforcement_mode_from_env();
    return mode;
}

}
