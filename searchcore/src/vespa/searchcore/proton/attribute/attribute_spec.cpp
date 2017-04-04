// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_spec.h"

namespace proton {

AttributeSpec::AttributeSpec(const vespalib::string &name,
                             const search::attribute::Config &cfg)
    : AttributeSpec(name, cfg, false, false)
{
}

AttributeSpec::AttributeSpec(const vespalib::string &name,
                             const search::attribute::Config &cfg,
                             bool hideFromReading,
                             bool hideFromWriting)
    : _name(name),
      _cfg(cfg),
      _hideFromReading(hideFromReading),
      _hideFromWriting(hideFromWriting)
{
}

AttributeSpec::AttributeSpec(const AttributeSpec &) = default;

AttributeSpec &
AttributeSpec::operator=(const AttributeSpec &) = default;

AttributeSpec::AttributeSpec(AttributeSpec &&) = default;

AttributeSpec &
AttributeSpec::operator=(AttributeSpec &&) = default;

AttributeSpec::~AttributeSpec() { }

bool
AttributeSpec::operator==(const AttributeSpec &rhs) const
{
    return ((_name == rhs._name) &&
            (_cfg == rhs._cfg) &&
            (_hideFromReading == rhs._hideFromReading) &&
            (_hideFromWriting == rhs._hideFromWriting));
}

}
