// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/address_space.h>
#include <string>

namespace proton {

/**
 * Class representing max address space usage (relative to the limit)
 * among components in attributes vectors in all sub databases.
 */
class AddressSpaceUsageStats
{
    vespalib::AddressSpace _usage;
    std::string _attributeName;
    std::string _component_name;
    std::string _subDbName;

public:
    explicit AddressSpaceUsageStats(const vespalib::AddressSpace &usage);
    ~AddressSpaceUsageStats();
    void merge(const vespalib::AddressSpace &usage,
               const std::string &attributeName,
               const std::string &component_name,
               const std::string &subDbName);

    const vespalib::AddressSpace &getUsage() const { return _usage; }
    const std::string &getAttributeName() const { return _attributeName; }
    const std::string &get_component_name() const { return _component_name; }
    const std::string &getSubDbName() const { return _subDbName; }

    bool operator==(const AddressSpaceUsageStats& rhs) const {
        return (_usage == rhs._usage) &&
                (_attributeName == rhs._attributeName) &&
                (_component_name == rhs._component_name) &&
                (_subDbName == rhs._subDbName);
    }
};

std::ostream& operator<<(std::ostream &out, const AddressSpaceUsageStats& rhs);

} // namespace proton
