// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
        usage.getUsage().limit() << "}, attributeName: \"" <<
        usage.getAttributeName() << "\", subdb: \"" <<
        usage.getSubDbName() << "\"}";
}

void makeEnumStoreMessage(std::ostream &os,
                          double used, double limit,
                          const AddressSpaceUsageStats &usage)
{
    os << "enumStoreLimitReached: { "
        "action: \""
        "add more content nodes"
        "\", "
        "reason: \""
        "enum store address space used (" << used << ") > "
        "limit (" << limit << ")"
        "\", enumStore: ";
    makeAddressSpaceMessage(os, usage);
}

void makeMultiValueMessage(std::ostream &os,
                           double used, double limit,
                           const AddressSpaceUsageStats &usage)
{
    os << "multiValueLimitReached: { "
        "action: \""
        "add more content nodes"
        "\", "
        "reason: \""
        "multiValue address space used (" << used << ") > "
        "limit (" << limit << ")"
        "\", multiValue: ";
    makeAddressSpaceMessage(os, usage);
}

}

void
AttributeUsageFilter::recalcState(const Guard &guard)
{
    (void) guard;
    bool hasMessage = false;
    std::ostringstream message;
    const AddressSpaceUsageStats &enumStoreUsage = _attributeStats.enumStoreUsage();
    double enumStoreUsed = enumStoreUsage.getUsage().usage();
    if (enumStoreUsed > _config._enumStoreLimit) {
        hasMessage = true;
        makeEnumStoreMessage(message, enumStoreUsed, _config._enumStoreLimit, enumStoreUsage);
    }
    const AddressSpaceUsageStats &multiValueUsage = _attributeStats.multiValueUsage();
    double multiValueUsed = multiValueUsage.getUsage().usage();
    if (multiValueUsed > _config._multiValueLimit) {
        if (hasMessage) {
            message << ", ";
        }
        hasMessage = true;
        makeMultiValueMessage(message, multiValueUsed, _config._multiValueLimit, multiValueUsage);
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

double
AttributeUsageFilter::getEnumStoreUsedRatio() const
{
    Guard guard(_lock);
    return _attributeStats.enumStoreUsage().getUsage().usage();
}

double
AttributeUsageFilter::getMultiValueUsedRatio() const
{
    Guard guard(_lock);
    return _attributeStats.multiValueUsage().getUsage().usage();
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
