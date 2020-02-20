// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_spec.h"

namespace proton {

AttributeSpec::AttributeSpec(const vespalib::string &name,
                             const search::attribute::Config &cfg)
    : _name(name),
      _cfg(cfg)
{
}

AttributeSpec::AttributeSpec(const AttributeSpec &) = default;

AttributeSpec &
AttributeSpec::operator=(const AttributeSpec &) = default;

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
