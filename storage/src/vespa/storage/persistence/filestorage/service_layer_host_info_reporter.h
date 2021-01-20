// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/resource_usage_listener.h>
#include <vespa/storage/common/hostreporter/hostreporter.h>
#include <mutex>

namespace storage {

struct NodeStateUpdater;

/*
 * Host info reporter for service layer that provides resource usage.
 */
class ServiceLayerHostInfoReporter : public HostReporter,
                                     public spi::ResourceUsageListener
{
    NodeStateUpdater&  _node_state_updater;
    std::mutex         _lock;
    spi::ResourceUsage _old_resource_usage;

    void update_resource_usage(const spi::ResourceUsage& resource_usage) override;
public:
    ServiceLayerHostInfoReporter(NodeStateUpdater& node_state_updater);

    ServiceLayerHostInfoReporter(const ServiceLayerHostInfoReporter&) = delete;
    ServiceLayerHostInfoReporter& operator=(const ServiceLayerHostInfoReporter&) = delete;
    ~ServiceLayerHostInfoReporter();

    void report(vespalib::JsonStream& output) override;
    const spi::ResourceUsage &get_old_resource_usage() noexcept { return _old_resource_usage; }
};

}
