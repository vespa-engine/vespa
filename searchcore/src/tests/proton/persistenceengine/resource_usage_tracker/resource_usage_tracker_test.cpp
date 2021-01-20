// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/persistence/spi/resource_usage_listener.h>
#include <vespa/persistence/spi/resource_usage.h>
#include <vespa/searchcore/proton/persistenceengine/resource_usage_tracker.h>
#include <vespa/searchcore/proton/test/disk_mem_usage_notifier.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/idestructorcallback.h>

using storage::spi::ResourceUsage;
using proton::test::DiskMemUsageNotifier;
using proton::DiskMemUsageState;
using proton::ResourceUsageTracker;

namespace {

struct MyResourceUsageListener : public storage::spi::ResourceUsageListener
{
    using storage::spi::ResourceUsageListener::ResourceUsageListener;

    std::vector<double> get_usage_vector() const { return { get_usage().get_disk_usage(), get_usage().get_memory_usage() }; }
};

}

class ResourceUsageTrackerTest : public ::testing::Test
{
protected:
    DiskMemUsageNotifier                     _notifier;
    std::shared_ptr<ResourceUsageTracker>    _tracker;
    std::unique_ptr<MyResourceUsageListener> _listener;

public:
    ResourceUsageTrackerTest()
        : testing::Test(),
          _notifier(DiskMemUsageState({ 0.8, 0.5 }, { 0.8, 0.4 })),
          _tracker(std::make_shared<ResourceUsageTracker>(_notifier)),
          _listener(std::make_unique<MyResourceUsageListener>())
    {
    }

    ~ResourceUsageTrackerTest();

    void notify(double disk_usage, double memory_usage)
    {
        _notifier.notify(DiskMemUsageState({ 0.8, disk_usage }, { 0.8, memory_usage }));
    }

};

ResourceUsageTrackerTest::~ResourceUsageTrackerTest() = default;

TEST_F(ResourceUsageTrackerTest, resource_usage_is_forwarded_to_listener)
{
    EXPECT_EQ((std::vector<double>{ 0.0, 0.0 }), _listener->get_usage_vector());
    auto register_guard = _tracker->set_listener(*_listener);
    EXPECT_EQ((std::vector<double>{ 0.5, 0.4 }), _listener->get_usage_vector());
    notify(0.75, 0.25);
    EXPECT_EQ((std::vector<double>{ 0.75, 0.25 }), _listener->get_usage_vector());
}

TEST_F(ResourceUsageTrackerTest, forwarding_depends_on_register_guard)
{
    auto register_guard = _tracker->set_listener(*_listener);
    register_guard.reset();
    notify(0.75, 0.25);
    EXPECT_EQ((std::vector<double>{ 0.5, 0.4 }), _listener->get_usage_vector());
}

TEST_F(ResourceUsageTrackerTest, no_forwarding_to_deleted_listener)
{
    _listener->set_register_guard(_tracker->set_listener(*_listener));
    notify(0.75, 0.25);
    EXPECT_EQ((std::vector<double>{ 0.75, 0.25 }), _listener->get_usage_vector());
    _listener.reset();
    notify(0.2, 0.1);
}

TEST_F(ResourceUsageTrackerTest, register_guard_handles_deleted_tracker)
{
    auto register_guard = _tracker->set_listener(*_listener);
    _tracker.reset();
}

GTEST_MAIN_RUN_ALL_TESTS()
