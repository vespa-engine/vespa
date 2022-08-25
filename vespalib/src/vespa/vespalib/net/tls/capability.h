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

/**
 * A capability represents the ability to access a distinct service or API
 * plane in Vespa (such as the Document API).
 *
 * Capability instances are intended to be very cheap to pass and store by value.
 */
class Capability {
private:
    // Each ID value corresponds to a unique single-bit position.
    // These values shall never be exposed outside the running process, i.e. they
    // must be possible to change arbitrarily internally across versions.
    // Changes must be reflected in capabilities_test.cpp
    enum class Id : uint32_t {
        ContentStorageApi = 0, // Must start at zero
        ContentDocumentApi,
        ContentSearchApi,
        ContentProtonAdminApi,
        ContentClusterControllerInternalStateApi,
        SlobrokApi,
        ConfigSentinelApi,
        ContentStatusPages,
        ContentMetricsApi,
        // When adding a capability ID to the end, max_value_count() MUST be updated
    };
public:
    constexpr static size_t max_value_count() noexcept {
        // This must refer to the highest possible CapabilityId enum value.
        return static_cast<size_t>(Id::ContentMetricsApi) + 1;
    }
private:
    Id _cap_id;

    friend class CapabilitySet; // CapabilitySet needs to know the raw IDs for bit set bookkeeping

    constexpr Id id() const noexcept { return _cap_id; }
    constexpr uint32_t id_as_idx() const noexcept { return static_cast<uint32_t>(_cap_id); }

    constexpr explicit Capability(Id cap_id) noexcept : _cap_id(cap_id) {}

    constexpr static Capability of(Id id) noexcept {
        return Capability(id);
    }

public:
    Capability() = delete; // Only valid capabilities can be created.

    constexpr bool operator==(const Capability& rhs) const noexcept {
        return (_cap_id == rhs._cap_id);
    }

    constexpr bool operator!=(const Capability& rhs) const noexcept {
        return !(*this == rhs);
    }

    std::string_view name() const noexcept;
    string to_string() const;

    static std::optional<Capability> find_capability(const string& cap_name) noexcept;

    constexpr static Capability content_storage_api() noexcept {
        return Capability(Id::ContentStorageApi);
    }

    constexpr static Capability content_document_api() noexcept {
        return Capability(Id::ContentDocumentApi);
    }

    constexpr static Capability content_search_api() noexcept {
        return Capability(Id::ContentSearchApi);
    }

    constexpr static Capability content_proton_admin_api() noexcept {
        return Capability(Id::ContentProtonAdminApi);
    }

    constexpr static Capability content_cluster_controller_internal_state_api() noexcept {
        return Capability(Id::ContentClusterControllerInternalStateApi);
    }

    constexpr static Capability slobrok_api() noexcept {
        return Capability(Id::SlobrokApi);
    }

    constexpr static Capability config_sentinel_api() noexcept {
        return Capability(Id::ConfigSentinelApi);
    }

    constexpr static Capability content_status_pages() noexcept {
        return Capability(Id::ContentStatusPages);
    }

    constexpr static Capability content_metrics_api() noexcept {
        return Capability(Id::ContentMetricsApi);
    }

};

std::ostream& operator<<(std::ostream&, const Capability& cap);
asciistream& operator<<(asciistream&, const Capability& cap);

}
