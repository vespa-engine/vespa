// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <iosfwd>

namespace vespalib { class asciistream; }

namespace vespalib::net::tls {

/**
 * Encapsulates a set of roles that requests over a particular authenticated
 * connection can assume, based on the authorization rules it matched during mTLS
 * handshaking.
 *
 * If at least one role is a wildcard ('*') role, the connection can assume _any_
 * possible role. This is the default when no role constraints are specified in
 * the TLS configuration file (legacy behavior). However, a default-constructed
 * AssumedRoles instance does not allow any roles to be assumed.
 */
class AssumedRoles {
public:
    using RoleSet = hash_set<string>;
private:
    RoleSet _assumed_roles;

    static const string WildcardRole;

    explicit AssumedRoles(RoleSet assumed_roles);
public:
    AssumedRoles();
    AssumedRoles(const AssumedRoles&);
    AssumedRoles& operator=(const AssumedRoles&);
    AssumedRoles(AssumedRoles&&) noexcept;
    AssumedRoles& operator=(AssumedRoles&&) noexcept;
    ~AssumedRoles();

    [[nodiscard]] bool empty() const noexcept {
        return _assumed_roles.empty();
    }

    /**
     * Returns true iff `role` is present in the role set OR the role set contains
     * the special wildcard role.
     */
    [[nodiscard]] bool can_assume_role(const string& role) const noexcept;

    [[nodiscard]] const RoleSet& unordered_roles() const noexcept {
        return _assumed_roles;
    }

    [[nodiscard]] std::vector<string> ordered_roles() const;

    bool operator==(const AssumedRoles& rhs) const noexcept;

    void print(asciistream& os) const;

    static AssumedRoles make_for_roles(RoleSet assumed_roles);
    static AssumedRoles make_wildcard_role(); // Allows assuming _all_ possible roles
    static AssumedRoles make_empty(); // Matches _no_ possible roles
};

asciistream& operator<<(asciistream&, const AssumedRoles&);
std::ostream& operator<<(std::ostream&, const AssumedRoles&);
string to_string(const AssumedRoles&);

class AssumedRolesBuilder {
    AssumedRoles::RoleSet _wip_roles;
public:
    AssumedRolesBuilder();
    ~AssumedRolesBuilder();

    void add_union(const AssumedRoles& roles);
    [[nodiscard]] bool empty() const noexcept { return _wip_roles.empty(); }
    [[nodiscard]] AssumedRoles build_with_move();
};

}
