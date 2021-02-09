// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "service_layer_host_info_reporter.h"
#include <vespa/persistence/spi/resource_usage.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <cmath>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.filestor.service_layer_host_info_reporter");

namespace storage {

using Object = vespalib::JsonStream::Object;
using End = vespalib::JsonStream::End;

namespace {

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

bool want_immediate_report(const spi::ResourceUsage& old_usage, const spi::ResourceUsage& new_usage, double noise_level)
{
    auto disk_usage_diff = fabs(new_usage.get_disk_usage() - old_usage.get_disk_usage());
    auto memory_usage_diff = fabs(new_usage.get_memory_usage() - old_usage.get_memory_usage());
    auto enum_store_diff = fabs(new_usage.get_attribute_enum_store_usage().get_usage() - old_usage.get_attribute_enum_store_usage().get_usage());
    auto multivalue_diff = fabs(new_usage.get_attribute_multivalue_usage().get_usage() - old_usage.get_attribute_multivalue_usage().get_usage());
    bool enum_store_got_valid = !old_usage.get_attribute_enum_store_usage().valid() &&
            new_usage.get_attribute_enum_store_usage().valid();
    bool multivalue_got_valid = !old_usage.get_attribute_multivalue_usage().valid() &&
            new_usage.get_attribute_multivalue_usage().valid();
    return ((disk_usage_diff > noise_level) ||
            (memory_usage_diff > noise_level) ||
            (enum_store_diff > noise_level) ||
            (multivalue_diff > noise_level) ||
            enum_store_got_valid ||
            multivalue_got_valid);
}

}

ServiceLayerHostInfoReporter::ServiceLayerHostInfoReporter(NodeStateUpdater& node_state_updater,
                                                           double noise_level)
    : HostReporter(),
      spi::ResourceUsageListener(),
      _node_state_updater(node_state_updater),
      _lock(),
      _old_resource_usage(),
      _noise_level(noise_level)
{
}

ServiceLayerHostInfoReporter::~ServiceLayerHostInfoReporter()
{
    spi::ResourceUsageListener::reset(); // detach
}

void
ServiceLayerHostInfoReporter::set_noise_level(double level)
{
    _noise_level.store(level, std::memory_order_relaxed);
}

namespace {

vespalib::string
to_string(const spi::ResourceUsage& usage)
{
    std::ostringstream oss;
    oss << usage;
    return oss.str();
}

}

void
ServiceLayerHostInfoReporter::update_resource_usage(const spi::ResourceUsage& resource_usage)
{
    double noise_level = _noise_level.load(std::memory_order_relaxed);
    bool immediate_report = want_immediate_report(_old_resource_usage, resource_usage, noise_level);
    LOG(debug, "update_resource_usage(): immediate_report=%s, noise_level=%f, old_usage=%s, new_usage=%s",
        (immediate_report ? "true" : "false"), noise_level, to_string(_old_resource_usage).c_str(),
        to_string(resource_usage).c_str());
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
        LOG(debug, "report(): usage=%s", to_string(usage).c_str());
        write_usage(output, memory_label, usage.get_memory_usage());
        write_usage(output, disk_label, usage.get_disk_usage());
        write_attribute_usage(output, attribute_enum_store_label, usage.get_attribute_enum_store_usage());
        write_attribute_usage(output, attribute_multi_value_label, usage.get_attribute_multivalue_usage());
    }
    output << End();
    output << End();
}

}
