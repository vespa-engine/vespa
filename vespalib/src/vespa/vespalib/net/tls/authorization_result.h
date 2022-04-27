// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "assumed_roles.h"
#include <vespa/vespalib/stllike/string.h>
#include <iosfwd>

namespace vespalib { class asciistream; }

namespace vespalib::net::tls {

/**
 * The result of evaluating configured mTLS authorization rules against the
 * credentials presented by a successfully authenticated peer certificate.
 *
 * This result contains the union set of all roles specified by the matching
 * authorization rules. If no rules matched, the set will be empty. The role
 * set will also be empty for a default-constructed instance.
 */
class AuthorizationResult {
    AssumedRoles _assumed_roles;

    explicit AuthorizationResult(AssumedRoles assumed_roles);
public:
    AuthorizationResult();
    AuthorizationResult(const AuthorizationResult&);
    AuthorizationResult& operator=(const AuthorizationResult&);
    AuthorizationResult(AuthorizationResult&&) noexcept;
    AuthorizationResult& operator=(AuthorizationResult&&) noexcept;
    ~AuthorizationResult();

    // Returns true iff at least one assumed role has been granted.
    [[nodiscard]] bool success() const noexcept {
        return !_assumed_roles.empty();
    }

    [[nodiscard]] const AssumedRoles& assumed_roles() const noexcept {
        return _assumed_roles;
    }
    [[nodiscard]] AssumedRoles steal_assumed_roles() noexcept {
        return std::move(_assumed_roles);
    }

    void print(asciistream& os) const;

    static AuthorizationResult make_authorized_for_roles(AssumedRoles assumed_roles);
    static AuthorizationResult make_authorized_for_all_roles();
    static AuthorizationResult make_not_authorized();
};

asciistream& operator<<(asciistream&, const AuthorizationResult&);
std::ostream& operator<<(std::ostream&, const AuthorizationResult&);
string to_string(const AuthorizationResult&);

}
