// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/net/tls/capability_set.h>
#include <vespa/vespalib/testkit/test_kit.h>

using namespace vespalib;
using namespace vespalib::net::tls;
using namespace std::string_view_literals;

TEST("Capability bit positions are stable across calls") {
    auto cap1 = Capability::content_storage_api();
    auto cap2 = Capability::content_storage_api();
    EXPECT_EQUAL(cap1.id_bit_pos(), cap2.id_bit_pos());
}

TEST("Capability instances are equality comparable") {
    auto cap1 = Capability::content_document_api();
    auto cap2 = Capability::content_document_api();
    auto cap3 = Capability::content_storage_api();
    EXPECT_EQUAL(cap1, cap2);
    EXPECT_EQUAL(cap2, cap1);
    EXPECT_NOT_EQUAL(cap1, cap3);
}

TEST("Can get underlying name of all Capability instances") {
    EXPECT_EQUAL(Capability::content_storage_api().name(),  "vespa.content.storage_api"sv);
    EXPECT_EQUAL(Capability::content_document_api().name(), "vespa.content.document_api"sv);
    EXPECT_EQUAL(Capability::content_search_api().name(),   "vespa.content.search_api"sv);
    EXPECT_EQUAL(Capability::slobrok_api().name(),          "vespa.slobrok.api"sv);
    EXPECT_EQUAL(Capability::content_status_pages().name(), "vespa.content.status_pages"sv);
    EXPECT_EQUAL(Capability::content_metrics_api().name(),  "vespa.content.metrics_api"sv);
    EXPECT_EQUAL(Capability::content_cluster_controller_internal_state_api().name(),
                 "vespa.content.cluster_controller.internal_state_api"sv);
}

TEST("Capability instances can be stringified") {
    EXPECT_EQUAL(Capability::content_storage_api().to_string(), "Capability(vespa.content.storage_api)");
}

TEST("All known capabilities can be looked up by name") {
    EXPECT_TRUE(Capability::find_capability("vespa.content.storage_api").has_value());
    EXPECT_TRUE(Capability::find_capability("vespa.content.document_api").has_value());
    EXPECT_TRUE(Capability::find_capability("vespa.content.search_api").has_value());
    EXPECT_TRUE(Capability::find_capability("vespa.content.cluster_controller.internal_state_api").has_value());
    EXPECT_TRUE(Capability::find_capability("vespa.slobrok.api").has_value());
    EXPECT_TRUE(Capability::find_capability("vespa.content.status_pages").has_value());
    EXPECT_TRUE(Capability::find_capability("vespa.content.metrics_api").has_value());
}

TEST("Unknown capability name returns nullopt") {
    EXPECT_FALSE(Capability::find_capability("vespa.content.stale_cat_memes").has_value());
}

TEST("CapabilitySet instances can be stringified") {
    EXPECT_EQUAL(CapabilitySet::content_node().to_string(),
                 "CapabilitySet({vespa.content.storage_api, vespa.content.document_api, vespa.slobrok.api})");
}

TEST("All known capability sets can be looked up by name") {
    EXPECT_TRUE(CapabilitySet::find_capability_set("vespa.content_node").has_value());
    EXPECT_TRUE(CapabilitySet::find_capability_set("vespa.container_node").has_value());
    EXPECT_TRUE(CapabilitySet::find_capability_set("vespa.telemetry").has_value());
    EXPECT_TRUE(CapabilitySet::find_capability_set("vespa.cluster_controller_node").has_value());
    EXPECT_TRUE(CapabilitySet::find_capability_set("vespa.config_server").has_value());
}

TEST("Unknown capability set name returns nullopt") {
    EXPECT_FALSE(CapabilitySet::find_capability_set("vespa.unicorn_launcher").has_value());
}

TEST("Resolving a capability set adds all its underlying capabilities") {
    CapabilitySet caps;
    EXPECT_TRUE(caps.resolve_and_add("vespa.content_node"));
    // Slightly suboptimal; this test will fail if the default set of capabilities for vespa.content_node changes.
    EXPECT_EQUAL(caps.count(), 3u);
    EXPECT_FALSE(caps.empty());
    EXPECT_TRUE(caps.contains(Capability::content_storage_api()));
    EXPECT_TRUE(caps.contains(Capability::content_document_api()));
    EXPECT_TRUE(caps.contains(Capability::slobrok_api()));
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

TEST("Default-constructed CapabilitySet has no capabilities") {
    CapabilitySet caps;
    EXPECT_EQUAL(caps.count(), 0u);
    EXPECT_TRUE(caps.empty());
    EXPECT_FALSE(caps.contains(Capability::content_storage_api()));
}

TEST("CapabilitySet can be created with all capabilities") {
    auto caps = CapabilitySet::make_with_all_capabilities();
    EXPECT_EQUAL(caps.count(), max_capability_bit_count());
    EXPECT_TRUE(caps.contains(Capability::content_storage_api()));
    EXPECT_TRUE(caps.contains(Capability::content_metrics_api()));
    // ... we just assume the rest are present as well.
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
