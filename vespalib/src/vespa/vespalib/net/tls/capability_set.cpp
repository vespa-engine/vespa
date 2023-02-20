// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "capability_set.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/asciistream.h>
#include <cassert>

namespace vespalib::net::tls {

string CapabilitySet::to_string() const {
    asciistream os;
    os << "CapabilitySet({";
    bool emit_comma = false;
    for_each_capability([&emit_comma, &os](Capability cap) {
       if (emit_comma) {
           os << ", ";
       } else {
           emit_comma = true;
       }
       // TODO let asciistream and std::string_view play along
       os << stringref(cap.name().data(), cap.name().size());
    });
    os << "})";
    return os.str();
}

std::optional<CapabilitySet> CapabilitySet::find_capability_set(const string& cap_set_name) noexcept {
    static const hash_map<string, CapabilitySet> name_to_cap_set({
        {"vespa.all",                     all()},
        {"vespa.content_node",            content_node()},
        {"vespa.container_node",          container_node()},
        {"vespa.telemetry",               telemetry()},
        {"vespa.cluster_controller_node", cluster_controller_node()},
        {"vespa.logserver_node",          logserver_node()},
        {"vespa.config_server",           config_server()}
    });
    auto iter = name_to_cap_set.find(cap_set_name);
    return (iter != name_to_cap_set.end()) ? std::optional<CapabilitySet>(iter->second) : std::nullopt;
}

bool CapabilitySet::resolve_and_add(const string& set_or_cap_name) noexcept {
    if (auto cap_set = find_capability_set(set_or_cap_name)) {
        _capability_mask |= cap_set->_capability_mask;
        return true;
    } else if (auto cap = Capability::find_capability(set_or_cap_name)) {
        _capability_mask |= cap_as_bit_set(*cap);
        return true;
    }
    return false;
}

// Note: the capability set factory functions below are all just using constexpr and/or inline
// functions, so the compiler will happily optimize them to just "return <constant bit pattern>".

CapabilitySet CapabilitySet::all() noexcept {
    return make_with_all_capabilities();
}

CapabilitySet CapabilitySet::content_node() noexcept {
    return CapabilitySet::of({Capability::content_storage_api(),
                              Capability::content_document_api(),
                              Capability::container_document_api()})
            .union_of(shared_app_node_capabilities());
}

CapabilitySet CapabilitySet::container_node() noexcept {
    return CapabilitySet::of({Capability::content_document_api(),
                              Capability::container_document_api(),
                              Capability::content_search_api()})
            .union_of(shared_app_node_capabilities());
}

CapabilitySet CapabilitySet::telemetry() noexcept {
    return CapabilitySet::of({Capability::content_status_pages(),
                              Capability::content_metrics_api(),
                              Capability::container_state_api(),
                              Capability::metricsproxy_metrics_api(),
                              Capability::sentinel_connectivity_check()});
}

CapabilitySet CapabilitySet::cluster_controller_node() noexcept {
    return CapabilitySet::of({Capability::content_cluster_controller_internal_state_api(),
                              Capability::client_slobrok_api(),
                              Capability::container_document_api()}) // for reindexing
            .union_of(shared_app_node_capabilities());
}

CapabilitySet CapabilitySet::logserver_node() noexcept {
    return shared_app_node_capabilities();
}

CapabilitySet CapabilitySet::config_server() noexcept {
    return CapabilitySet::of({Capability::client_filereceiver_api(),
                              Capability::container_management_api(),
                              Capability::slobrok_api(),
                              Capability::cluster_controller_reindexing(),
                              Capability::cluster_controller_state(),
                              Capability::logserver_api()})
            .union_of(telemetry());
}

CapabilitySet CapabilitySet::shared_app_node_capabilities() noexcept {
    return CapabilitySet::of({Capability::logserver_api(),
                              Capability::configserver_config_api(),
                              Capability::configserver_filedistribution_api(),
                              Capability::configproxy_config_api(),
                              Capability::configproxy_filedistribution_api(),
                              Capability::slobrok_api()})
            .union_of(telemetry());
}

CapabilitySet CapabilitySet::make_with_all_capabilities() noexcept {
    BitSet bit_set;
    bit_set.flip(); // All cap bits set
    return CapabilitySet(bit_set);
}

std::ostream& operator<<(std::ostream& os, const CapabilitySet& cap_set) {
    os << cap_set.to_string();
    return os;
}

asciistream& operator<<(asciistream& os, const CapabilitySet& cap_set) {
    os << cap_set.to_string();
    return os;
}

}
