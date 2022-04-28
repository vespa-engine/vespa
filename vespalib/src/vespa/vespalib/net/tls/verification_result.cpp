// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "verification_result.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>

namespace vespalib::net::tls {

VerificationResult::VerificationResult() = default;

VerificationResult::VerificationResult(AssumedRoles assumed_roles)
    : _assumed_roles(std::move(assumed_roles))
{}

VerificationResult::VerificationResult(const VerificationResult&) = default;
VerificationResult& VerificationResult::operator=(const VerificationResult&) = default;
VerificationResult::VerificationResult(VerificationResult&&) noexcept = default;
VerificationResult& VerificationResult::operator=(VerificationResult&&) noexcept = default;
VerificationResult::~VerificationResult() = default;

void VerificationResult::print(asciistream& os) const {
    os << "VerificationResult(";
    if (!success()) {
        os << "NOT AUTHORIZED";
    } else {
        os << _assumed_roles;
    }
    os << ')';
}

VerificationResult
VerificationResult::make_authorized_for_roles(AssumedRoles assumed_roles) {
    return VerificationResult(std::move(assumed_roles));
}

VerificationResult
VerificationResult::make_authorized_for_all_roles() {
    return VerificationResult(AssumedRoles::make_wildcard_role());
}

VerificationResult
VerificationResult::make_not_authorized() {
    return {};
}

asciistream& operator<<(asciistream& os, const VerificationResult& res) {
    res.print(os);
    return os;
}

std::ostream& operator<<(std::ostream& os, const VerificationResult& res) {
    os << to_string(res);
    return os;
}

string to_string(const VerificationResult& res) {
    asciistream os;
    os << res;
    return os.str();
}

}
