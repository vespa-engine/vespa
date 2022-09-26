// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "address_space_usage.h"
#include "address_space_components.h"

namespace search {

using vespalib::AddressSpace;

AddressSpaceUsage::AddressSpaceUsage()
    : _map()
{
}

void
AddressSpaceUsage::set(const vespalib::string& component, const vespalib::AddressSpace& usage)
{
    _map[component] = usage;
}

AddressSpace
AddressSpaceUsage::get(const vespalib::string& component) const
{
    auto itr = _map.find(component);
    if (itr != _map.end()) {
        return itr->second;
    }
    return AddressSpace();
}

AddressSpace
AddressSpaceUsage::enum_store_usage() const
{
    return get(AddressSpaceComponents::enum_store);
}

AddressSpace
AddressSpaceUsage::multi_value_usage() const
{
    return get(AddressSpaceComponents::multi_value);
}

}
