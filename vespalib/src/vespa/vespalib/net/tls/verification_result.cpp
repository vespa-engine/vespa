// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "verification_result.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>

namespace vespalib::net::tls {

VerificationResult::VerificationResult() = default;

VerificationResult::VerificationResult(CapabilitySet granted_capabilities)
    : _granted_capabilities(std::move(granted_capabilities))
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
        os << _granted_capabilities;
    }
    os << ')';
}

VerificationResult
VerificationResult::make_authorized_with_capabilities(CapabilitySet granted_capabilities) {
    return VerificationResult(std::move(granted_capabilities));
}

VerificationResult
VerificationResult::make_authorized_with_all_capabilities() {
    return VerificationResult(CapabilitySet::make_with_all_capabilities());
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
