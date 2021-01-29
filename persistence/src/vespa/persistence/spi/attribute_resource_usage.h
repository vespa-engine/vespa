// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace storage::spi {

/*
 * Class representing attribute resource usage for persistence provider.
 * Numbers are normalized to be between 0.0 and 1.0
 */
class AttributeResourceUsage
{
    double _usage;
    vespalib::string _name; // document_type.subdb.attribute
public:
    
    AttributeResourceUsage(double usage, const vespalib::string& name)
        : _usage(usage),
          _name(name)
    {
    }

    AttributeResourceUsage()
        : AttributeResourceUsage(0.0, "")
    {
    }

    double get_usage() const noexcept { return _usage; }
    const vespalib::string& get_name() const noexcept { return _name; }
    bool valid() const noexcept { return !_name.empty(); }

    bool operator==(const AttributeResourceUsage& rhs) const noexcept {
        return ((_usage == rhs._usage) && (_name == rhs._name));
    }

    bool operator!=(const AttributeResourceUsage& rhs) const noexcept {
        return !operator==(rhs);
    }
};

std::ostream& operator<<(std::ostream& out, const AttributeResourceUsage& attribute_resource_usage);

}
