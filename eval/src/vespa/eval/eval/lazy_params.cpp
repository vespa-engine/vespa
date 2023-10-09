// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lazy_params.h"
#include <vespa/vespalib/util/stash.h>
#include <cassert>

namespace vespalib::eval {

LazyParams::~LazyParams() = default;

//-----------------------------------------------------------------------------

SimpleObjectParams::~SimpleObjectParams() = default;

const Value &
SimpleObjectParams::resolve(size_t idx, Stash &) const
{
    assert(idx < params.size());
    return params[idx];
}

//-----------------------------------------------------------------------------

SimpleParams::~SimpleParams() = default;

const Value &
SimpleParams::resolve(size_t idx, Stash &stash) const
{
    assert(idx < params.size());
    return stash.create<DoubleValue>(params[idx]);
}

//-----------------------------------------------------------------------------

NoParams NoParams::params;

//-----------------------------------------------------------------------------

} // namespace vespalib::eval
