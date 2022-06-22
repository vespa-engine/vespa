// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <bitset>
#include <iosfwd>
#include <optional>
#include <string_view>
#include <vector>

namespace vespalib { class asciistream; }

namespace vespalib::net::tls {

// Each ID value corresponds to a unique single-bit position.
// These values shall never be exposed outside the running process, i.e. they
// must be possible to change arbitrarily internally across versions.
enum class CapabilityId : uint32_t {
    ContentStorageApi = 0, // Must start at zero
    ContentDocumentApi,
    ContentSearchApi,
    ContentClusterControllerInternalStateApi,
    SlobrokApi,
    ContentStatusPages,
    ContentMetricsApi,
    // When adding a capability ID to the end, max_capability_bit_count() MUST be updated
};

constexpr size_t max_capability_bit_count() noexcept {
    // This must refer to the highest possible CapabilityId enum value.
    return static_cast<size_t>(CapabilityId::ContentMetricsApi) + 1;
}

using CapabilityBitSet = std::bitset<max_capability_bit_count()>;

/**
 * A capability represents the ability to access a distinct service or API
 * plane in Vespa (such as the Document API).
 *
 * Capability instances are intended to be very cheap to pass and store by value.
 */
class Capability {
private:
    CapabilityId _cap_id;

    constexpr explicit Capability(CapabilityId cap_id) noexcept : _cap_id(cap_id) {}
public:
    Capability() = delete; // Only valid capabilities can be created.

    constexpr CapabilityId id() const noexcept { return _cap_id; }

    constexpr uint32_t id_bit_pos() const noexcept { return static_cast<uint32_t>(_cap_id); }

    constexpr CapabilityBitSet id_as_bit_set() const noexcept {
        static_assert(max_capability_bit_count() <= 32); // Must fit into uint32_t bitmask
        return {uint32_t(1) << id_bit_pos()};
    }

    constexpr bool operator==(const Capability& rhs) const noexcept {
        return (_cap_id == rhs._cap_id);
    }

    constexpr bool operator!=(const Capability& rhs) const noexcept {
        return !(*this == rhs);
    }

    std::string_view name() const noexcept;
    string to_string() const;

    constexpr static Capability of(CapabilityId id) noexcept {
        return Capability(id);
    }

    static std::optional<Capability> find_capability(const string& cap_name) noexcept;

    constexpr static Capability content_storage_api() noexcept {
        return Capability(CapabilityId::ContentStorageApi);
    }

    constexpr static Capability content_document_api() noexcept {
        return Capability(CapabilityId::ContentDocumentApi);
    }

    constexpr static Capability content_search_api() noexcept {
        return Capability(CapabilityId::ContentSearchApi);
    }

    constexpr static Capability content_cluster_controller_internal_state_api() noexcept {
        return Capability(CapabilityId::ContentClusterControllerInternalStateApi);
    }

    constexpr static Capability slobrok_api() noexcept {
        return Capability(CapabilityId::SlobrokApi);
    }

    constexpr static Capability content_status_pages() noexcept {
        return Capability(CapabilityId::ContentStatusPages);
    }

    constexpr static Capability content_metrics_api() noexcept {
        return Capability(CapabilityId::ContentMetricsApi);
    }

};

std::ostream& operator<<(std::ostream&, const Capability& cap);
asciistream& operator<<(asciistream&, const Capability& cap);

}
