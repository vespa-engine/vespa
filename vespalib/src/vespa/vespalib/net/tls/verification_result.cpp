// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "verification_result.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>

namespace vespalib::net::tls {

VerificationResult::VerificationResult() noexcept
    : _granted_capabilities(),
      _authorized(false)
{}

VerificationResult::VerificationResult(bool authorized, CapabilitySet granted_capabilities) noexcept
    : _granted_capabilities(granted_capabilities),
      _authorized(authorized)
{}

VerificationResult::VerificationResult(const VerificationResult&) noexcept = default;
VerificationResult& VerificationResult::operator=(const VerificationResult&) noexcept = default;
VerificationResult::VerificationResult(VerificationResult&&) noexcept = default;
VerificationResult& VerificationResult::operator=(VerificationResult&&) noexcept = default;
VerificationResult::~VerificationResult() = default;

void VerificationResult::print(asciistream& os) const {
    os << "VerificationResult(";
    if (!success()) {
        os << "NOT AUTHORIZED";
    } else {
        os << _granted_capabilities;
    }
    os << ')';
}

VerificationResult
VerificationResult::make_authorized_with_capabilities(CapabilitySet granted_capabilities) noexcept {
    return {true, granted_capabilities};
}

VerificationResult
VerificationResult::make_authorized_with_all_capabilities() noexcept {
    return {true, CapabilitySet::make_with_all_capabilities()};
}

VerificationResult
VerificationResult::make_not_authorized() noexcept {
    return {false, CapabilitySet::make_empty()};
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
