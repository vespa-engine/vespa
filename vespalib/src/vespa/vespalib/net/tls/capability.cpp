// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "capability.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/asciistream.h>
#include <array>

namespace vespalib::net::tls {

namespace {

using namespace std::string_view_literals;

// Important: must match 1-1 with CapabilityId values!
constexpr std::array<std::string_view, Capability::max_value_count()> capability_names = {
    "vespa.content.storage_api"sv,
    "vespa.content.document_api"sv,
    "vespa.content.search_api"sv,
    "vespa.content.proton_admin_api"sv,
    "vespa.content.cluster_controller.internal_state_api"sv,
    "vespa.slobrok.api"sv,
    "vespa.config.sentinel_api"sv,
    "vespa.content.status_pages"sv,
    "vespa.content.metrics_api"sv,
};

} // anon ns

std::string_view Capability::name() const noexcept {
    return capability_names[id_as_idx()];
}

string Capability::to_string() const {
    asciistream os;
    // TODO asciistream should be made std::string_view-aware
    os << "Capability(" << stringref(name().data(), name().length()) << ')';
    return os.str();
}

std::optional<Capability> Capability::find_capability(const string& cap_name) noexcept {
    static const hash_map<string, Capability> name_to_cap({
        {"vespa.content.storage_api",                           content_storage_api()},
        {"vespa.content.document_api",                          content_document_api()},
        {"vespa.content.search_api",                            content_search_api()},
        {"vespa.content.proton_admin_api",                      content_proton_admin_api()},
        {"vespa.content.cluster_controller.internal_state_api", content_cluster_controller_internal_state_api()},
        {"vespa.slobrok.api",                                   slobrok_api()},
        {"vespa.config.sentinel_api",                           config_sentinel_api()},
        {"vespa.content.status_pages",                          content_status_pages()},
        {"vespa.content.metrics_api",                           content_metrics_api()},
    });
    auto iter = name_to_cap.find(cap_name);
    return (iter != name_to_cap.end()) ? std::optional<Capability>(iter->second) : std::nullopt;
}

std::ostream& operator<<(std::ostream& os, const Capability& cap) {
    os << cap.to_string();
    return os;
}

asciistream& operator<<(asciistream& os, const Capability& cap) {
    os << cap.to_string();
    return os;
}

}
