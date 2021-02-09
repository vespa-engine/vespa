// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/persistence/filestorage/service_layer_host_info_reporter.h>
#include <tests/common/hostreporter/util.h>
#include <tests/common/testnodestateupdater.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <iostream>

namespace storage {

using spi::AttributeResourceUsage;
using spi::ResourceUsage;

namespace {

double
get_usage_element(const vespalib::Slime& root, const vespalib::string& label)
{
    return root.get()["content-node"]["resource-usage"][label]["usage"].asDouble();
}

AttributeResourceUsage
get_attribute_usage_element(const vespalib::Slime& root, const vespalib::string& label)
{
    double usage = get_usage_element(root, label);
    auto name = root.get()["content-node"]["resource-usage"][label]["name"].asString();
    return AttributeResourceUsage(usage, name.make_string());
}

const vespalib::string attr_es_name("doctype.subdb.esattr");
const vespalib::string attr_mv_name("doctype.subdb.mvattr");

}

struct ServiceLayerHostInfoReporterTest : ::testing::Test {

    TestNodeStateUpdater         _state_manager;
    ServiceLayerHostInfoReporter _reporter;

    ServiceLayerHostInfoReporterTest();
    ~ServiceLayerHostInfoReporterTest();

    void notify(double disk_usage, double memory_usage, const AttributeResourceUsage &attribute_enum_store_usage, const AttributeResourceUsage &attribute_multivalue_usage)  {
        auto& listener = static_cast<spi::IResourceUsageListener&>(_reporter);
        listener.update_resource_usage(ResourceUsage(disk_usage, memory_usage, attribute_enum_store_usage, attribute_multivalue_usage));
    }
    void notify(double disk_usage, double memory_usage) {
        notify(disk_usage, memory_usage, {0.0, ""}, {0.0, ""});
    }
    void set_noise_level(double level) {
        _reporter.set_noise_level(level);
    }

    size_t requested_almost_immediate_replies() { return _state_manager.requested_almost_immediate_node_state_replies(); }
    ResourceUsage get_old_usage() { return _reporter.get_old_resource_usage(); }
    ResourceUsage get_usage() { return _reporter.get_usage(); }
    ResourceUsage get_slime_usage() {
        vespalib::Slime root;
        util::reporterToSlime(_reporter, root);
        return ResourceUsage(get_usage_element(root, "disk"), get_usage_element(root, "memory"), get_attribute_usage_element(root, "attribute-enum-store"), get_attribute_usage_element(root, "attribute-multi-value"));
    }
};

ServiceLayerHostInfoReporterTest::ServiceLayerHostInfoReporterTest()
    : _state_manager(lib::NodeType::STORAGE),
      _reporter(_state_manager)
{
}

ServiceLayerHostInfoReporterTest::~ServiceLayerHostInfoReporterTest() = default;

TEST_F(ServiceLayerHostInfoReporterTest, request_almost_immediate_node_state_as_needed)
{
    EXPECT_EQ(0, requested_almost_immediate_replies());
    EXPECT_EQ(ResourceUsage(0.0, 0.0), get_old_usage());
    EXPECT_EQ(ResourceUsage(0.0, 0.0), get_usage());
    notify(0.5, 0.4);
    EXPECT_EQ(1, requested_almost_immediate_replies());
    EXPECT_EQ(ResourceUsage(0.5, 0.4), get_old_usage());
    EXPECT_EQ(ResourceUsage(0.5, 0.4), get_usage());
    notify(0.5001, 0.4001);
    EXPECT_EQ(1, requested_almost_immediate_replies());
    EXPECT_EQ(ResourceUsage(0.5, 0.4), get_old_usage());
    EXPECT_EQ(ResourceUsage(0.5001, 0.4001), get_usage());
    notify(0.8, 0.4);
    EXPECT_EQ(2, requested_almost_immediate_replies());
    EXPECT_EQ(ResourceUsage(0.8, 0.4), get_old_usage());
    EXPECT_EQ(ResourceUsage(0.8, 0.4), get_usage());
    notify(0.8, 0.7);
    EXPECT_EQ(3, requested_almost_immediate_replies());
    EXPECT_EQ(ResourceUsage(0.8, 0.7), get_old_usage());
    EXPECT_EQ(ResourceUsage(0.8, 0.7), get_usage());
    notify(0.7999, 0.6999);
    EXPECT_EQ(3, requested_almost_immediate_replies());
    EXPECT_EQ(ResourceUsage(0.8, 0.7), get_old_usage());
    EXPECT_EQ(ResourceUsage(0.7999, 0.6999), get_usage());
    notify(0.8, 0.7, {0.1, attr_es_name}, {});
    EXPECT_EQ(ResourceUsage(0.8, 0.7, {0.1, attr_es_name}, {}), get_old_usage());
    EXPECT_EQ(ResourceUsage(0.8, 0.7, {0.1, attr_es_name}, {}), get_usage());
    notify(0.8, 0.7, {0.1, attr_es_name}, {0.2, attr_mv_name});
    EXPECT_EQ(ResourceUsage(0.8, 0.7, {0.1, attr_es_name}, {0.2, attr_mv_name}), get_old_usage());
    EXPECT_EQ(ResourceUsage(0.8, 0.7, {0.1, attr_es_name}, {0.2, attr_mv_name}), get_usage());
}

TEST_F(ServiceLayerHostInfoReporterTest, can_set_noise_level)
{
    set_noise_level(0.02);
    notify(0.5, 0.4);
    EXPECT_EQ(1, requested_almost_immediate_replies());
    EXPECT_EQ(ResourceUsage(0.5, 0.4), get_old_usage());
    EXPECT_EQ(ResourceUsage(0.5, 0.4), get_usage());
    // the difference in disk usage is below the noise level
    notify(0.519, 0.4);
    EXPECT_EQ(1, requested_almost_immediate_replies());
    EXPECT_EQ(ResourceUsage(0.5, 0.4), get_old_usage());
    EXPECT_EQ(ResourceUsage(0.519, 0.4), get_usage());
    // the difference in disk usage is above the noise level
    notify(0.521, 0.4);
    EXPECT_EQ(2, requested_almost_immediate_replies());
    EXPECT_EQ(ResourceUsage(0.521, 0.4), get_old_usage());
    EXPECT_EQ(ResourceUsage(0.521, 0.4), get_usage());
}

TEST_F(ServiceLayerHostInfoReporterTest,
       first_valid_attribute_enum_store_sample_triggers_immediate_node_state_when_below_noise_level)
{
    set_noise_level(0.02);
    constexpr double usage_below_noise_level = 0.019;
    notify(0.0, 0.0, {usage_below_noise_level, attr_es_name}, {});
    EXPECT_EQ(1, requested_almost_immediate_replies());
    EXPECT_EQ(ResourceUsage(0.0, 0.0, {usage_below_noise_level, attr_es_name}, {}), get_old_usage());
    EXPECT_EQ(ResourceUsage(0.0, 0.0, {usage_below_noise_level, attr_es_name}, {}), get_usage());
}

TEST_F(ServiceLayerHostInfoReporterTest,
       first_valid_attribute_multi_value_sample_triggers_immediate_node_state_when_below_noise_level)
{
    set_noise_level(0.02);
    constexpr double usage_below_noise_level = 0.019;
    notify(0.0, 0.0, {}, {usage_below_noise_level, attr_mv_name});
    EXPECT_EQ(1, requested_almost_immediate_replies());
    EXPECT_EQ(ResourceUsage(0.0, 0.0, {}, {usage_below_noise_level, attr_mv_name}), get_old_usage());
    EXPECT_EQ(ResourceUsage(0.0, 0.0, {}, {usage_below_noise_level, attr_mv_name}), get_usage());
}

TEST_F(ServiceLayerHostInfoReporterTest, json_report_generated)
{
    EXPECT_EQ(ResourceUsage(0.0, 0.0), get_slime_usage());
    notify(0.5, 0.4);
    EXPECT_EQ(ResourceUsage(0.5, 0.4), get_slime_usage());
    notify(0.5, 0.4, {0.3, attr_es_name}, {0.2, attr_mv_name});
    EXPECT_EQ(ResourceUsage(0.5, 0.4, {0.3, attr_es_name}, {0.2, attr_mv_name}), get_slime_usage());
}

}
