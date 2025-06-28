// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "iflushtarget.h"

namespace searchcorespi {

IFlushTarget::IFlushTarget(const std::string &name) noexcept
    : IFlushTarget(name, Type::OTHER, Component::OTHER)
{ }

IFlushTarget::IFlushTarget(const std::string &name, const Type &type, const Component &component) noexcept
    : _name(name),
      _type(type),
      _component(component)
{ }

IFlushTarget::~IFlushTarget() = default;

LeafFlushTarget::LeafFlushTarget(const std::string &name, const Type &type, const Component &component) noexcept
    : IFlushTarget(name, type, component)
{}

LeafFlushTarget::~LeafFlushTarget() = default;

uint64_t
LeafFlushTarget::get_approx_bytes_to_read_from_disk() const noexcept
{
    return 0;
}

std::chrono::steady_clock::duration
LeafFlushTarget::last_flush_duration() const noexcept
{
    return std::chrono::steady_clock::duration::zero();
}

}
