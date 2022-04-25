// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "authorization_result.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>

namespace vespalib::net::tls {

AuthorizationResult::AuthorizationResult() = default;

AuthorizationResult::AuthorizationResult(AssumedRoles assumed_roles)
    : _assumed_roles(std::move(assumed_roles))
{}

AuthorizationResult::AuthorizationResult(const AuthorizationResult&) = default;
AuthorizationResult& AuthorizationResult::operator=(const AuthorizationResult&) = default;
AuthorizationResult::AuthorizationResult(AuthorizationResult&&) noexcept = default;
AuthorizationResult& AuthorizationResult::operator=(AuthorizationResult&&) noexcept = default;
AuthorizationResult::~AuthorizationResult() = default;

void AuthorizationResult::print(asciistream& os) const {
    os << "AuthorizationResult(";
    if (!success()) {
        os << "NOT AUTHORIZED";
    } else {
        os << _assumed_roles;
    }
    os << ')';
}

AuthorizationResult
AuthorizationResult::make_authorized_for_roles(AssumedRoles assumed_roles) {
    return AuthorizationResult(std::move(assumed_roles));
}

AuthorizationResult
AuthorizationResult::make_authorized_for_all_roles() {
    return AuthorizationResult(AssumedRoles::make_wildcard_role());
}

AuthorizationResult
AuthorizationResult::make_not_authorized() {
    return {};
}

asciistream& operator<<(asciistream& os, const AuthorizationResult& res) {
    res.print(os);
    return os;
}

std::ostream& operator<<(std::ostream& os, const AuthorizationResult& res) {
    os << to_string(res);
    return os;
}

string to_string(const AuthorizationResult& res) {
    asciistream os;
    os << res;
    return os.str();
}

}
