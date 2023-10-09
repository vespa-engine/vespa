// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_filter.h"
#include "i_attribute_usage_listener.h"
#include <sstream>

namespace proton {

namespace {

void makeAddressSpaceMessage(std::ostream &os,
                              const AddressSpaceUsageStats &usage)
{
    os << "{ used: " <<
        usage.getUsage().used() << ", dead: " <<
        usage.getUsage().dead() << ", limit: " <<
        usage.getUsage().limit() << "}, " <<
        "attributeName: \"" << usage.getAttributeName() << "\", " <<
        "componentName: \"" << usage.get_component_name() << "\", " <<
        "subdb: \"" << usage.getSubDbName() << "\"}";
}

void make_error_message(std::ostream &os,
                        double used, double limit,
                        const AddressSpaceUsageStats &usage)
{
    os << "addressSpaceLimitReached: { "
        "action: \""
        "add more content nodes"
        "\", "
        "reason: \""
        "max address space in attribute vector components used (" << used << ") > "
        "limit (" << limit << ")"
        "\", addressSpace: ";
    makeAddressSpaceMessage(os, usage);
}

}

void
AttributeUsageFilter::recalcState(const Guard &guard)
{
    (void) guard;
    bool hasMessage = false;
    std::ostringstream message;
    const auto &max_usage = _attributeStats.max_address_space_usage();
    double used = max_usage.getUsage().usage();
    if (used > _config._address_space_limit) {
        hasMessage = true;
        make_error_message(message, used, _config._address_space_limit, max_usage);
    }
    if (hasMessage) {
        _state = State(false, message.str());
        _acceptWrite = false;
    } else {
        _state = State();
        _acceptWrite = true;
    }
}

AttributeUsageFilter::AttributeUsageFilter()
    : _lock(),
      _attributeStats(),
      _config(),
      _state(),
      _acceptWrite(true),
      _listener()
{
}

AttributeUsageFilter::~AttributeUsageFilter() = default;

void
AttributeUsageFilter::setAttributeStats(AttributeUsageStats attributeStats_in)
{
    Guard guard(_lock);
    _attributeStats = attributeStats_in;
    recalcState(guard);
    if (_listener) {
        _listener->notify_attribute_usage(_attributeStats);
    }
}

AttributeUsageStats
AttributeUsageFilter::getAttributeUsageStats() const
{
    Guard guard(_lock);
    return _attributeStats;
}

void
AttributeUsageFilter::setConfig(Config config_in)
{
    Guard guard(_lock);
    _config = config_in;
    recalcState(guard);
}

void
AttributeUsageFilter::set_listener(std::unique_ptr<IAttributeUsageListener> listener)
{
    Guard guard(_lock);
    _listener = std::move(listener);
}

bool
AttributeUsageFilter::acceptWriteOperation() const
{
    return _acceptWrite;
}

AttributeUsageFilter::State
AttributeUsageFilter::getAcceptState() const
{
    Guard guard(_lock);
    return _state;
}

} // namespace proton
