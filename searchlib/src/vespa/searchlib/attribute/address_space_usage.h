// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_fun.h>
#include <vespa/vespalib/util/address_space.h>
#include <string>
#include <unordered_map>

namespace search {

/**
 * Represents the address space usage for a set of attribute vector components.
 */
class AddressSpaceUsage
{
private:
    using AddressSpaceMap = std::unordered_map<std::string, vespalib::AddressSpace, vespalib::hash<std::string>>;
    AddressSpaceMap _map;

public:
    AddressSpaceUsage();
    void set(const std::string& component, const vespalib::AddressSpace& usage);
    vespalib::AddressSpace get(const std::string& component) const;
    const AddressSpaceMap& get_all() const { return _map; }
    vespalib::AddressSpace enum_store_usage() const;
    vespalib::AddressSpace multi_value_usage() const;
};

}
