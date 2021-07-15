// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "address_space_components.h"
#include "address_space_usage.h"

namespace search {

using vespalib::AddressSpace;

AddressSpaceUsage::AddressSpaceUsage()
    : _map()
{
}

AddressSpaceUsage::AddressSpaceUsage(const AddressSpace& enum_store_usage,
                                     const AddressSpace& multi_value_usage)
    : _map()
{
    // TODO: Remove this constructor and instead add usage for each relevant component explicit.
    set(AddressSpaceComponents::enum_store, enum_store_usage);
    set(AddressSpaceComponents::multi_value, multi_value_usage);
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
