// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "inspector.h"

namespace vespalib {
namespace slime {

/**
 * Interface used when traversing all the entries of an array value.
 **/
struct ArrayTraverser {
    virtual void entry(size_t idx, const Inspector &inspector) = 0;
    virtual ~ArrayTraverser() {}
};

} // namespace vespalib::slime
} // namespace vespalib

