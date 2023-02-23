// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/net/tls/capability_set.h>
#include <vespa/vespalib/testkit/test_kit.h>

using namespace vespalib;
using namespace vespalib::net::tls;
using namespace std::string_view_literals;

TEST("Capability instances are equality comparable") {
    auto cap1 = Capability::content_document_api();
    auto cap2 = Capability::content_document_api();
    auto cap3 = Capability::content_storage_api();
    EXPECT_EQUAL(cap1, cap2);
    EXPECT_EQUAL(cap2, cap1);
    EXPECT_NOT_EQUAL(cap1, cap3);
}

TEST("CapabilitySet instances are equality comparable") {
    const auto cap1 = Capability::content_document_api();
    const auto cap2 = Capability::content_search_api();

    const auto all_caps = CapabilitySet::make_with_all_capabilities();
    const auto set_12_a = CapabilitySet::of({cap1, cap2});
    const auto set_12_b = CapabilitySet::of({cap1, cap2});
    const auto set_1  = CapabilitySet::of({cap1});
    const auto empty  = CapabilitySet::make_empty();

    EXPECT_EQUAL(all_caps, all_caps);
    EXPECT_EQUAL(empty, empty);
    EXPECT_EQUAL(set_12_a, set_12_b);
    EXPECT_EQUAL(set_12_b, set_12_a);

    EXPECT_NOT_EQUAL(all_caps, empty);
    EXPECT_NOT_EQUAL(set_12_a, set_1);
    EXPECT_NOT_EQUAL(set_12_a, all_caps);
    EXPECT_NOT_EQUAL(set_1, empty);
}

TEST("Capability instances can be stringified") {
    EXPECT_EQUAL(Capability::content_storage_api().to_string(), "Capability(vespa.content.storage_api)");
}

namespace {

void check_capability_mapping(const std::string& name, Capability expected) {
    auto cap = Capability::find_capability(name);
    ASSERT_TRUE(cap.has_value());
    EXPECT_EQUAL(*cap, expected);
    EXPECT_EQUAL(name, cap->name());
}

void check_capability_set_mapping(const std::string& name, CapabilitySet expected) {
    auto caps = CapabilitySet::find_capability_set(name);
    ASSERT_TRUE(caps.has_value());
    EXPECT_EQUAL(*caps, expected);
}

}

TEST("All known capabilities can be looked up by name, and resolve back to same name") {
    check_capability_mapping("vespa.none",                          Capability::none());
    check_capability_mapping("vespa.http.unclassified",             Capability::http_unclassified());
    check_capability_mapping("vespa.restapi.unclassified",          Capability::restapi_unclassified());
    check_capability_mapping("vespa.rpc.unclassified",              Capability::rpc_unclassified());
    check_capability_mapping("vespa.client.filereceiver_api",       Capability::client_filereceiver_api());
    check_capability_mapping("vespa.client.slobrok_api",            Capability::client_slobrok_api());
    check_capability_mapping("vespa.cluster_controller.reindexing", Capability::cluster_controller_reindexing());
    check_capability_mapping("vespa.cluster_controller.state",      Capability::cluster_controller_state());
    check_capability_mapping("vespa.cluster_controller.status",     Capability::cluster_controller_status());
    check_capability_mapping("vespa.configproxy.config_api",        Capability::configproxy_config_api());
    check_capability_mapping("vespa.configproxy.management_api",    Capability::configproxy_management_api());
    check_capability_mapping("vespa.configproxy.filedistribution_api",
                             Capability::configproxy_filedistribution_api());
    check_capability_mapping("vespa.configserver.config_api",       Capability::configserver_config_api());
    check_capability_mapping("vespa.configserver.filedistribution_api",
                             Capability::configserver_filedistribution_api());
    check_capability_mapping("vespa.container.document_api",        Capability::container_document_api());
    check_capability_mapping("vespa.container.management_api",      Capability::container_management_api());
    check_capability_mapping("vespa.container.state_api",           Capability::container_state_api());
    check_capability_mapping("vespa.content.cluster_controller.internal_state_api",
                             Capability::content_cluster_controller_internal_state_api());
    check_capability_mapping("vespa.content.document_api",          Capability::content_document_api());
    check_capability_mapping("vespa.content.metrics_api",           Capability::content_metrics_api());
    check_capability_mapping("vespa.content.proton_admin_api",      Capability::content_proton_admin_api());
    check_capability_mapping("vespa.content.search_api",            Capability::content_search_api());
    check_capability_mapping("vespa.content.state_api",             Capability::content_state_api());
    check_capability_mapping("vespa.content.status_pages",          Capability::content_status_pages());
    check_capability_mapping("vespa.content.storage_api",           Capability::content_storage_api());
    check_capability_mapping("vespa.logserver.api",                 Capability::logserver_api());
    check_capability_mapping("vespa.metricsproxy.management_api",   Capability::metricsproxy_management_api());
    check_capability_mapping("vespa.metricsproxy.metrics_api",      Capability::metricsproxy_metrics_api());
    check_capability_mapping("vespa.sentinel.connectivity_check",   Capability::sentinel_connectivity_check());
    check_capability_mapping("vespa.sentinel.inspect_services",     Capability::sentinel_inspect_services());
    check_capability_mapping("vespa.sentinel.management_api",       Capability::sentinel_management_api());
    check_capability_mapping("vespa.slobrok.api",                   Capability::slobrok_api());
}

TEST("Unknown capability name returns nullopt") {
    EXPECT_FALSE(Capability::find_capability("vespa.content.stale_cat_memes").has_value());
}

TEST("CapabilitySet instances can be stringified") {
    EXPECT_EQUAL(CapabilitySet::content_node().to_string(),
                 "CapabilitySet({vespa.configproxy.config_api, "
                                "vespa.configproxy.filedistribution_api, "
                                "vespa.configserver.config_api, "
                                "vespa.configserver.filedistribution_api, "
                                "vespa.container.document_api, "
                                "vespa.container.state_api, "
                                "vespa.content.document_api, "
                                "vespa.content.metrics_api, "
                                "vespa.content.state_api, "
                                "vespa.content.status_pages, "
                                "vespa.content.storage_api, "
                                "vespa.logserver.api, "
                                "vespa.metricsproxy.metrics_api, "
                                "vespa.sentinel.connectivity_check, "
                                "vespa.slobrok.api})");
}

TEST("All known capability sets can be looked up by name") {
    check_capability_set_mapping("vespa.all",                     CapabilitySet::all());
    check_capability_set_mapping("vespa.content_node",            CapabilitySet::content_node());
    check_capability_set_mapping("vespa.container_node",          CapabilitySet::container_node());
    check_capability_set_mapping("vespa.telemetry",               CapabilitySet::telemetry());
    check_capability_set_mapping("vespa.cluster_controller_node", CapabilitySet::cluster_controller_node());
    check_capability_set_mapping("vespa.logserver_node",          CapabilitySet::logserver_node());
    check_capability_set_mapping("vespa.config_server_node",      CapabilitySet::config_server_node());
}

TEST("Unknown capability set name returns nullopt") {
    EXPECT_FALSE(CapabilitySet::find_capability_set("vespa.unicorn_launcher").has_value());
}

TEST("Resolving a capability set adds all its underlying capabilities") {
    CapabilitySet caps;
    EXPECT_TRUE(caps.resolve_and_add("vespa.content_node"));
    // Slightly suboptimal; this test will fail if the default set of capabilities for vespa.content_node changes.
    EXPECT_EQUAL(caps.count(), 15u);
    EXPECT_FALSE(caps.empty());
    EXPECT_TRUE(caps.contains(Capability::content_storage_api()));
    EXPECT_TRUE(caps.contains(Capability::content_document_api()));
    EXPECT_TRUE(caps.contains(Capability::container_document_api()));
    // vespa.content_node -> shared node caps:
    EXPECT_TRUE(caps.contains(Capability::logserver_api()));
    EXPECT_TRUE(caps.contains(Capability::configserver_config_api()));
    EXPECT_TRUE(caps.contains(Capability::configserver_filedistribution_api()));
    EXPECT_TRUE(caps.contains(Capability::configproxy_config_api()));
    EXPECT_TRUE(caps.contains(Capability::configproxy_filedistribution_api()));
    // vespa.content_node -> shared node caps -> vespa.telemetry
    EXPECT_TRUE(caps.contains(Capability::content_state_api()));
    EXPECT_TRUE(caps.contains(Capability::content_status_pages()));
    EXPECT_TRUE(caps.contains(Capability::content_metrics_api()));
    EXPECT_TRUE(caps.contains(Capability::container_state_api()));
    EXPECT_TRUE(caps.contains(Capability::metricsproxy_metrics_api()));
    EXPECT_TRUE(caps.contains(Capability::sentinel_connectivity_check()));
    EXPECT_TRUE(caps.contains(Capability::slobrok_api()));
    // Not included:
    EXPECT_FALSE(caps.contains(Capability::content_search_api()));
}

TEST("Resolving a single capability adds it to the underlying capabilities") {
    CapabilitySet caps;
    EXPECT_TRUE(caps.resolve_and_add("vespa.slobrok.api"));
    EXPECT_EQUAL(caps.count(), 1u);
    EXPECT_FALSE(caps.empty());
    EXPECT_TRUE(caps.contains(Capability::slobrok_api()));
    EXPECT_FALSE(caps.contains(Capability::content_storage_api()));
}

TEST("Resolving an unknown capability set returns false and does not add anything") {
    CapabilitySet caps;
    EXPECT_FALSE(caps.resolve_and_add("vespa.distributors_evil_twin_with_an_evil_goatee"));
    EXPECT_EQUAL(caps.count(), 0u);
    EXPECT_TRUE(caps.empty());
}

TEST("Resolving multiple capabilities/sets adds union of capabilities") {
    CapabilitySet caps;
    EXPECT_TRUE(caps.resolve_and_add("vespa.content_node"));   // CapabilitySet
    EXPECT_TRUE(caps.resolve_and_add("vespa.container_node")); // ditto
    EXPECT_EQUAL(caps, CapabilitySet::of({Capability::content_storage_api(),
                                          Capability::content_document_api(),
                                          Capability::container_document_api(),
                                          Capability::slobrok_api(),
                                          Capability::content_search_api()})
            .union_of(CapabilitySet::shared_app_node_capabilities()));
    EXPECT_TRUE(caps.resolve_and_add("vespa.content.metrics_api")); // Capability (single)
    EXPECT_EQUAL(caps, CapabilitySet::of({Capability::content_storage_api(),
                                          Capability::content_document_api(),
                                          Capability::container_document_api(),
                                          Capability::slobrok_api(),
                                          Capability::content_search_api(),
                                          Capability::content_metrics_api()})
            .union_of(CapabilitySet::shared_app_node_capabilities()));
}

TEST("Default-constructed CapabilitySet has no capabilities") {
    CapabilitySet caps;
    EXPECT_EQUAL(caps.count(), 0u);
    EXPECT_TRUE(caps.empty());
    EXPECT_FALSE(caps.contains(Capability::content_storage_api()));
    // "none" is a special sentinel, it does not imply an empty capability set
    EXPECT_FALSE(caps.contains(Capability::none()));
}

TEST("CapabilitySet can be created with all capabilities") {
    auto caps = CapabilitySet::make_with_all_capabilities();
    EXPECT_EQUAL(caps.count(), CapabilitySet::max_count());
    EXPECT_EQUAL(caps, CapabilitySet::all()); // alias
    EXPECT_TRUE(caps.contains(Capability::none()));
    EXPECT_TRUE(caps.contains(Capability::content_storage_api()));
    EXPECT_TRUE(caps.contains(Capability::content_metrics_api()));
    // ... we just assume the rest are present as well.
}

TEST("CapabilitySet can be explicitly unioned") {
    const auto a = CapabilitySet::of({Capability::content_document_api()});
    const auto b = CapabilitySet::of({Capability::content_search_api()});
    auto c = a.union_of(b);

    EXPECT_EQUAL(c.count(), 2u);
    EXPECT_TRUE(c.contains(Capability::content_document_api()));
    EXPECT_TRUE(c.contains(Capability::content_search_api()));
}

TEST("CapabilitySet::contains_all() requires an intersection of capabilities") {
    auto cap1 = Capability::content_document_api();
    auto cap2 = Capability::content_search_api();
    auto cap3 = Capability::content_storage_api();

    const auto all_caps = CapabilitySet::make_with_all_capabilities();
    auto set_123 = CapabilitySet::of({cap1, cap2, cap3});
    auto set_13  = CapabilitySet::of({cap1, cap3});
    auto set_2   = CapabilitySet::of({cap2});
    auto set_23  = CapabilitySet::of({cap2, cap3});
    auto empty   = CapabilitySet::make_empty();

    // Sets contain themselves
    EXPECT_TRUE(all_caps.contains_all(all_caps));
    EXPECT_TRUE(set_13.contains_all(set_13));
    EXPECT_TRUE(set_2.contains_all(set_2));
    EXPECT_TRUE(empty.contains_all(empty));

    // Supersets contain subsets
    EXPECT_TRUE(all_caps.contains_all(set_123));
    EXPECT_TRUE(all_caps.contains_all(set_13));
    EXPECT_TRUE(set_123.contains_all(set_13));
    EXPECT_TRUE(set_2.contains_all(empty));

    // Subsets do not contain supersets
    EXPECT_FALSE(set_123.contains_all(all_caps));
    EXPECT_FALSE(set_13.contains_all(set_123));
    EXPECT_FALSE(empty.contains_all(set_2));

    // Partially overlapping sets are not contained in each other
    EXPECT_FALSE(set_13.contains_all(set_23));
    EXPECT_FALSE(set_23.contains_all(set_13));

    // Fully disjoint sets are not contained in each other
    EXPECT_FALSE(set_2.contains_all(set_13));
    EXPECT_FALSE(set_13.contains_all(set_2));
}

TEST_MAIN() { TEST_RUN_ALL(); }
