// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/persistence/spi/i_resource_usage_listener.h>
#include <vespa/persistence/spi/resource_usage.h>
#include <vespa/searchcore/proton/persistenceengine/resource_usage_tracker.h>
#include <vespa/searchcore/proton/test/disk_mem_usage_notifier.h>
#include <vespa/vespalib/gtest/gtest.h>

using storage::spi::ResourceUsage;
using proton::test::DiskMemUsageNotifier;
using proton::DiskMemUsageState;
using proton::ResourceUsageTracker;

namespace {

struct MyResourceUsageListener : public storage::spi::IResourceUsageListener
{
    ResourceUsage usage;
    MyResourceUsageListener() noexcept
      : IResourceUsageListener(),
        usage()
    {
    }
    void update_resource_usage(const ResourceUsage& resource_usage) override {
        usage = resource_usage;
    };

    std::vector<double> get_usage_vector() const { return { usage.get_disk_usage(), usage.get_memory_usage() }; }
};

}

TEST(ResourceUsageTrackerTest, resource_usage_is_forwarded_to_listener)
{
    DiskMemUsageNotifier notifier;
    auto listener = std::make_shared<MyResourceUsageListener>();
    ResourceUsageTracker tracker(notifier);
    EXPECT_EQ((std::vector<double>{ 0.0, 0.0 }), listener->get_usage_vector());
    tracker.add_listener(listener);
    EXPECT_EQ((std::vector<double>{ 0.5, 0.4 }), listener->get_usage_vector());
    notifier.notify(DiskMemUsageState({ 0.8, 0.75 }, { 0.8, 0.25 }));
    EXPECT_EQ((std::vector<double>{ 0.75, 0.25 }), listener->get_usage_vector());
    tracker.remove_listener(listener);
}

GTEST_MAIN_RUN_ALL_TESTS()
