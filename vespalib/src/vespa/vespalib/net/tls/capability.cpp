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
    "vespa.none"sv,
    "vespa.http.unclassified"sv,
    "vespa.restapi.unclassified"sv,
    "vespa.rpc.unclassified"sv,
    "vespa.client.filereceiver_api"sv,
    "vespa.client.slobrok_api"sv,
    "vespa.cluster_controller.reindexing"sv,
    "vespa.cluster_controller.state"sv,
    "vespa.cluster_controller.status"sv,
    "vespa.configproxy.config_api"sv,
    "vespa.configproxy.management_api"sv,
    "vespa.configproxy.filedistribution_api"sv,
    "vespa.configserver.config_api"sv,
    "vespa.configserver.filedistribution_api"sv,
    "vespa.container.document_api"sv,
    "vespa.container.management_api"sv,
    "vespa.container.state_api"sv,
    "vespa.content.cluster_controller.internal_state_api"sv,
    "vespa.content.document_api"sv,
    "vespa.content.metrics_api"sv,
    "vespa.content.proton_admin_api"sv,
    "vespa.content.search_api"sv,
    "vespa.content.state_api"sv,
    "vespa.content.status_pages"sv,
    "vespa.content.storage_api"sv,
    "vespa.logserver.api"sv,
    "vespa.metricsproxy.management_api"sv,
    "vespa.metricsproxy.metrics_api"sv,
    "vespa.sentinel.connectivity_check"sv,
    "vespa.sentinel.inspect_services"sv,
    "vespa.sentinel.management_api"sv,
    "vespa.slobrok.api"sv
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
        {"vespa.none",                                          none()},
        {"vespa.http.unclassified",                             http_unclassified()},
        {"vespa.restapi.unclassified",                          restapi_unclassified()},
        {"vespa.rpc.unclassified",                              rpc_unclassified()},
        {"vespa.client.filereceiver_api",                       client_filereceiver_api()},
        {"vespa.client.slobrok_api",                            client_slobrok_api()},
        {"vespa.cluster_controller.reindexing",                 cluster_controller_reindexing()},
        {"vespa.cluster_controller.state",                      cluster_controller_state()},
        {"vespa.cluster_controller.status",                     cluster_controller_status()},
        {"vespa.configproxy.config_api",                        configproxy_config_api()},
        {"vespa.configproxy.management_api",                    configproxy_management_api()},
        {"vespa.configproxy.filedistribution_api",              configproxy_filedistribution_api()},
        {"vespa.configserver.config_api",                       configserver_config_api()},
        {"vespa.configserver.filedistribution_api",             configserver_filedistribution_api()},
        {"vespa.container.document_api",                        container_document_api()},
        {"vespa.container.management_api",                      container_management_api()},
        {"vespa.container.state_api",                           container_state_api()},
        {"vespa.content.cluster_controller.internal_state_api", content_cluster_controller_internal_state_api()},
        {"vespa.content.document_api",                          content_document_api()},
        {"vespa.content.metrics_api",                           content_metrics_api()},
        {"vespa.content.proton_admin_api",                      content_proton_admin_api()},
        {"vespa.content.search_api",                            content_search_api()},
        {"vespa.content.state_api",                             content_state_api()},
        {"vespa.content.status_pages",                          content_status_pages()},
        {"vespa.content.storage_api",                           content_storage_api()},
        {"vespa.logserver.api",                                 logserver_api()},
        {"vespa.metricsproxy.management_api",                   metricsproxy_management_api()},
        {"vespa.metricsproxy.metrics_api",                      metricsproxy_metrics_api()},
        {"vespa.sentinel.connectivity_check",                   sentinel_connectivity_check()},
        {"vespa.sentinel.inspect_services",                     sentinel_inspect_services()},
        {"vespa.sentinel.management_api",                       sentinel_management_api()},
        {"vespa.slobrok.api",                                   slobrok_api()}
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
