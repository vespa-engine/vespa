// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "capability.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <bitset>
#include <initializer_list>
#include <iosfwd>
#include <optional>
#include <vector>

namespace vespalib { class asciistream; }

namespace vespalib::net::tls {

/**
 * A CapabilitySet efficiently represents a finite set (possibly empty) of individual
 * capabilities and allows for both single and set-based membership tests.
 *
 * Factory functions are provided for all predefined Vespa capability sets.
 *
 * CapabilitySet instances are intended to be very cheap to pass and store by value.
 */
class CapabilitySet {
    CapabilityBitSet _capability_mask;

    explicit constexpr CapabilitySet(CapabilityBitSet capabilities) noexcept
        : _capability_mask(capabilities)
    {}
public:
    constexpr CapabilitySet() noexcept = default;
    constexpr ~CapabilitySet() = default;

    string to_string() const;

    bool operator==(const CapabilitySet& rhs) const noexcept {
        return (_capability_mask == rhs._capability_mask);
    }

    [[nodiscard]] bool empty() const noexcept {
        return _capability_mask.none();
    }
    size_t count() const noexcept {
        return _capability_mask.count();
    }

    [[nodiscard]] constexpr bool contains(Capability cap) const noexcept {
        return _capability_mask[cap.id_bit_pos()];
    }
    [[nodiscard]] bool contains_all(CapabilitySet caps) const noexcept {
        return ((_capability_mask & caps._capability_mask) == caps._capability_mask);
    }

    void add(const Capability& cap) noexcept {
        _capability_mask |= cap.id_as_bit_set();
    }
    void add_all(const CapabilitySet& cap_set) noexcept {
        _capability_mask |= cap_set._capability_mask;
    }

    template <typename Func>
    void for_each_capability(Func f) const noexcept(noexcept(f(Capability::content_storage_api()))) {
        for (size_t i = 0; i < _capability_mask.size(); ++i) {
            if (_capability_mask[i]) {
                f(Capability::of(static_cast<CapabilityId>(i)));
            }
        }
    }

    /**
     * Since we have two capability naming "tiers", resolving is done in two steps:
     *   1. Check if the name matches a known capability _set_ name. If so, add
     *      all unique capabilities within the set to our own working set. Return true.
     *   2. Check if the name matches a known single capability. If so, add that
     *      capability to our own working set. Return true.
     *   3. Otherwise, return false.
     */
    [[nodiscard]] bool resolve_and_add(const string& set_or_cap_name) noexcept;

    static std::optional<CapabilitySet> find_capability_set(const string& cap_set_name) noexcept;

    static CapabilitySet of(std::initializer_list<Capability> caps) noexcept {
        CapabilitySet set;
        for (const auto& cap : caps) {
            set._capability_mask |= cap.id_as_bit_set();
        }
        return set;
    }

    static CapabilitySet content_node() noexcept;
    static CapabilitySet container_node() noexcept;
    static CapabilitySet telemetry() noexcept;
    static CapabilitySet cluster_controller_node() noexcept;
    static CapabilitySet config_server() noexcept;

    static CapabilitySet make_with_all_capabilities() noexcept;
    static CapabilitySet make_empty() noexcept { return CapabilitySet(); };
};

std::ostream& operator<<(std::ostream&, const CapabilitySet& cap_set);
asciistream& operator<<(asciistream&, const CapabilitySet& cap_set);

}
