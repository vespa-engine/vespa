// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "service_layer_host_info_reporter.h"
#include <vespa/storage/common/nodestateupdater.h>
#include <cmath>

namespace storage {

using Object = vespalib::JsonStream::Object;
using End = vespalib::JsonStream::End;

namespace {

constexpr double diff_slack = 0.001;

const vespalib::string memory_label("memory");
const vespalib::string disk_label("disk");
const vespalib::string attribute_enum_store_label("attribute-enum-store");
const vespalib::string attribute_multi_value_label("attribute-multi-value");

void write_usage(vespalib::JsonStream& output, const vespalib::string &label, double value)
{
    output << label << Object();
    output << "usage" << value;
    output << End();
}

void write_attribute_usage(vespalib::JsonStream& output, const vespalib::string &label, const spi::AttributeResourceUsage &usage)
{
    output << label << Object();
    output << "usage" << usage.get_usage();
    output << "name" << usage.get_name();
    output << End();
}

bool want_immediate_report(const spi::ResourceUsage& old_resource_usage, const spi::ResourceUsage& resource_usage)
{
    auto disk_usage_diff = fabs(resource_usage.get_disk_usage() - old_resource_usage.get_disk_usage());
    auto memory_usage_diff = fabs(resource_usage.get_memory_usage() - old_resource_usage.get_memory_usage());
    auto attribute_enum_store_diff = fabs(resource_usage.get_attribute_enum_store_usage().get_usage() - old_resource_usage.get_attribute_enum_store_usage().get_usage());
    auto attribute_multivalue_diff = fabs(resource_usage.get_attribute_multivalue_usage().get_usage() - old_resource_usage.get_attribute_multivalue_usage().get_usage());
    return (disk_usage_diff > diff_slack || memory_usage_diff > diff_slack || attribute_enum_store_diff > diff_slack || attribute_multivalue_diff > diff_slack);
}

}

ServiceLayerHostInfoReporter::ServiceLayerHostInfoReporter(NodeStateUpdater& node_state_updater)
    : HostReporter(),
      spi::ResourceUsageListener(),
      _node_state_updater(node_state_updater),
      _lock(),
      _old_resource_usage()
{
}

ServiceLayerHostInfoReporter::~ServiceLayerHostInfoReporter()
{
    spi::ResourceUsageListener::reset(); // detach
}

void
ServiceLayerHostInfoReporter::update_resource_usage(const spi::ResourceUsage& resource_usage)
{
    bool immediate_report = want_immediate_report(_old_resource_usage, resource_usage);
    if (immediate_report) {
        _old_resource_usage = resource_usage;
    }
    {
        std::lock_guard guard(_lock);
        spi::ResourceUsageListener::update_resource_usage(resource_usage);
    }
    if (immediate_report) {
        _node_state_updater.request_almost_immediate_node_state_replies();
    }
}

void
ServiceLayerHostInfoReporter::report(vespalib::JsonStream& output)
{
    output << "content-node" << Object();
    output << "resource-usage" << Object();
    {
        std::lock_guard guard(_lock);
        auto& usage = get_usage();
        write_usage(output, memory_label, usage.get_memory_usage());
        write_usage(output, disk_label, usage.get_disk_usage());
        write_attribute_usage(output, attribute_enum_store_label, usage.get_attribute_enum_store_usage());
        write_attribute_usage(output, attribute_multi_value_label, usage.get_attribute_multivalue_usage());
    }
    output << End();
    output << End();
}

}
