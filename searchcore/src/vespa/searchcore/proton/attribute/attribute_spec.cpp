// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_spec.h"
#include <vespa/searchcommon/attribute/config.h>

namespace proton {

AttributeSpec::AttributeSpec(const vespalib::string &name, const Config &cfg)
    : _name(name),
      _cfg(std::make_unique<Config>(cfg))
{
}

AttributeSpec::AttributeSpec(AttributeSpec &&) noexcept = default;

AttributeSpec &
AttributeSpec::operator=(AttributeSpec &&) noexcept = default;

AttributeSpec::~AttributeSpec() = default;

bool
AttributeSpec::operator==(const AttributeSpec &rhs) const
{
    return ((_name == rhs._name) &&
            (_cfg == rhs._cfg));
}

}
