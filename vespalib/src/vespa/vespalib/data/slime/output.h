// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <stddef.h>

namespace vespalib {
namespace slime {

/**
 * Interface used to interact with an external output buffer.
 **/
struct Output {
    virtual char *exchange(char *p, size_t commit, size_t reserve) = 0;
    virtual ~Output() {}
};

} // namespace vespalib::slime
} // namespace vespalib

