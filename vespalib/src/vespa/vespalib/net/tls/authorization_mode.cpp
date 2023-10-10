// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "authorization_mode.h"

#include <iostream>

namespace vespalib::net::tls {

const char* enum_name(AuthorizationMode mode) noexcept {
    switch (mode) {
    case AuthorizationMode::Disable: return "Disable";
    case AuthorizationMode::LogOnly: return "LogOnly";
    case AuthorizationMode::Enforce: return "Enforce";
    }
    abort();
}

std::ostream& operator<<(std::ostream& os, AuthorizationMode mode) {
    os << enum_name(mode);
    return os;
}

}
