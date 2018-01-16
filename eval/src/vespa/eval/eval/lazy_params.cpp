// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lazy_params.h"
#include <assert.h>

namespace vespalib::eval {

LazyParams::~LazyParams()
{
}

SimpleObjectParams::~SimpleObjectParams()
{
}

const Value &
SimpleObjectParams::resolve(size_t idx, Stash &) const
{
    assert(idx < params.size());
    return params[idx];
}

} // namespace vespalib::eval
