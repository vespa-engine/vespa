// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "capability_set.h"
#include <vespa/vespalib/stllike/string.h>
#include <iosfwd>

namespace vespalib { class asciistream; }

namespace vespalib::net::tls {

/**
 * The result of evaluating configured mTLS authorization rules against the
 * credentials presented by a successfully authenticated peer certificate.
 *
 * This result contains the union set of all capabilities granted by the matching
 * authorization rules. If no rules matched, the set will be empty. The capability
 * set will also be empty for a default-constructed instance.
 */
class VerificationResult {
    CapabilitySet _granted_capabilities;

    explicit VerificationResult(CapabilitySet granted_capabilities);
public:
    VerificationResult();
    VerificationResult(const VerificationResult&);
    VerificationResult& operator=(const VerificationResult&);
    VerificationResult(VerificationResult&&) noexcept;
    VerificationResult& operator=(VerificationResult&&) noexcept;
    ~VerificationResult();

    // Returns true iff at least one capability been granted.
    [[nodiscard]] bool success() const noexcept {
        return !_granted_capabilities.empty();
    }

    [[nodiscard]] const CapabilitySet& granted_capabilities() const noexcept {
        return _granted_capabilities;
    }

    void print(asciistream& os) const;

    static VerificationResult make_authorized_with_capabilities(CapabilitySet granted_capabilities);
    static VerificationResult make_authorized_with_all_capabilities();
    static VerificationResult make_not_authorized();
};

asciistream& operator<<(asciistream&, const VerificationResult&);
std::ostream& operator<<(std::ostream&, const VerificationResult&);
string to_string(const VerificationResult&);

}
