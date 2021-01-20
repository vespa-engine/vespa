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

namespace {

double
get_usage_element(const vespalib::Slime& root, const vespalib::string& label)
{
    return root.get()["content-node"]["resource-usage"][label]["usage"].asDouble();
}

}

struct ServiceLayerHostInfoReporterTest : ::testing::Test {

    TestNodeStateUpdater         _state_manager;
    ServiceLayerHostInfoReporter _reporter;

    ServiceLayerHostInfoReporterTest();
    ~ServiceLayerHostInfoReporterTest();

    void notify(double disk_usage, double memory_usage)
    {
        auto& listener = static_cast<spi::IResourceUsageListener&>(_reporter);
        listener.update_resource_usage(spi::ResourceUsage(disk_usage, memory_usage));
    }

    size_t requested_almost_immediate_replies() { return _state_manager.requested_almost_immediate_node_state_replies(); }
    std::vector<double> get_old_usage() {
        auto &old_resource_usage = _reporter.get_old_resource_usage();
        return std::vector<double>{ old_resource_usage.get_disk_usage(), old_resource_usage.get_memory_usage() };
    }
    std::vector<double> get_usage() {
        auto &resource_usage = _reporter.get_usage();
        return std::vector<double>{ resource_usage.get_disk_usage(), resource_usage.get_memory_usage() };
    }
    std::vector<double> get_slime_usage() {
        vespalib::Slime root;
        util::reporterToSlime(_reporter, root);
        return std::vector<double>{ get_usage_element(root, "disk"), get_usage_element(root, "memory") };
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
    EXPECT_EQ((std::vector<double>{ 0.0, 0.0 }), get_old_usage());
    EXPECT_EQ((std::vector<double>{ 0.0, 0.0 }), get_usage());
    notify(0.5, 0.4);
    EXPECT_EQ(1, requested_almost_immediate_replies());
    EXPECT_EQ((std::vector<double>{ 0.5, 0.4 }), get_old_usage());
    EXPECT_EQ((std::vector<double>{ 0.5, 0.4 }), get_usage());
    notify(0.501, 0.401);
    EXPECT_EQ(1, requested_almost_immediate_replies());
    EXPECT_EQ((std::vector<double>{ 0.5, 0.4 }), get_old_usage());
    EXPECT_EQ((std::vector<double>{ 0.501, 0.401 }), get_usage());
    notify(0.8, 0.4);
    EXPECT_EQ(2, requested_almost_immediate_replies());
    EXPECT_EQ((std::vector<double>{ 0.8, 0.4 }), get_old_usage());
    EXPECT_EQ((std::vector<double>{ 0.8, 0.4 }), get_usage());
    notify(0.8, 0.7);
    EXPECT_EQ(3, requested_almost_immediate_replies());
    EXPECT_EQ((std::vector<double>{ 0.8, 0.7 }), get_old_usage());
    EXPECT_EQ((std::vector<double>{ 0.8, 0.7 }), get_usage());
    notify(0.799, 0.699);
    EXPECT_EQ(3, requested_almost_immediate_replies());
    EXPECT_EQ((std::vector<double>{ 0.8, 0.7 }), get_old_usage());
    EXPECT_EQ((std::vector<double>{ 0.799, 0.699 }), get_usage());
}

TEST_F(ServiceLayerHostInfoReporterTest, json_report_generated)
{
    EXPECT_EQ((std::vector<double>{ 0.0, 0.0 }), get_slime_usage());
    notify(0.5, 0.4);
    EXPECT_EQ((std::vector<double>{ 0.5, 0.4 }), get_slime_usage());
}

}
