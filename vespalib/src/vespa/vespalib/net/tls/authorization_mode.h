// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <iosfwd>

namespace vespalib::net::tls {

enum class AuthorizationMode {
    Disable,
    LogOnly,
    Enforce
};

const char* enum_name(AuthorizationMode) noexcept;

std::ostream& operator<<(std::ostream&, AuthorizationMode);

}
