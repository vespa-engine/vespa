// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "iflushtarget.h"

namespace searchcorespi {

IFlushTarget::IFlushTarget(const vespalib::string &name) noexcept
    : IFlushTarget(name, Type::OTHER, Component::OTHER)
{ }

IFlushTarget::IFlushTarget(const vespalib::string &name, const Type &type, const Component &component) noexcept
    : _name(name),
      _type(type),
      _component(component)
{ }

IFlushTarget::~IFlushTarget() = default;

}
