// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "assumed_roles.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <algorithm>
#include <ostream>

namespace vespalib::net::tls {

const string AssumedRoles::WildcardRole("*");

AssumedRoles::AssumedRoles() = default;

AssumedRoles::AssumedRoles(RoleSet assumed_roles)
    : _assumed_roles(std::move(assumed_roles))
{}

AssumedRoles::AssumedRoles(const AssumedRoles&) = default;
AssumedRoles& AssumedRoles::operator=(const AssumedRoles&) = default;
AssumedRoles::AssumedRoles(AssumedRoles&&) noexcept = default;
AssumedRoles& AssumedRoles::operator=(AssumedRoles&&) noexcept = default;
AssumedRoles::~AssumedRoles() = default;

bool AssumedRoles::can_assume_role(const string& role) const noexcept {
    return (_assumed_roles.contains(role) || _assumed_roles.contains(WildcardRole));
}

std::vector<string> AssumedRoles::ordered_roles() const {
    std::vector<string> roles;
    for (const auto& r : _assumed_roles) {
        roles.emplace_back(r);
    }
    std::sort(roles.begin(), roles.end());
    return roles;
}

bool AssumedRoles::operator==(const AssumedRoles& rhs) const noexcept {
    return (_assumed_roles == rhs._assumed_roles);
}

void AssumedRoles::print(asciistream& os) const {
    os << "AssumedRoles(roles: [";
    auto roles = ordered_roles();
    for (size_t i = 0; i < roles.size(); ++i) {
        if (i > 0) {
            os << ", ";
        }
        os << roles[i];
    }
    os << "])";
}

asciistream& operator<<(asciistream& os, const AssumedRoles& res) {
    res.print(os);
    return os;
}

std::ostream& operator<<(std::ostream& os, const AssumedRoles& res) {
    os << to_string(res);
    return os;
}

string to_string(const AssumedRoles& res) {
    asciistream os;
    os << res;
    return os.str();
}

AssumedRoles AssumedRoles::make_for_roles(RoleSet assumed_roles) {
    return AssumedRoles(std::move(assumed_roles));
}

AssumedRoles AssumedRoles::make_wildcard_role() {
    return AssumedRoles(RoleSet({WildcardRole}));
}

AssumedRoles AssumedRoles::make_empty() {
    return {};
}

AssumedRolesBuilder::AssumedRolesBuilder() = default;
AssumedRolesBuilder::~AssumedRolesBuilder() = default;

void AssumedRolesBuilder::add_union(const AssumedRoles& roles) {
    // TODO fix hash_set iterator range insert()
    for (const auto& role : roles.unordered_roles()) {
        _wip_roles.insert(role);
    }
}

AssumedRoles AssumedRolesBuilder::build_with_move() {
    return AssumedRoles::make_for_roles(std::move(_wip_roles));
}

}

