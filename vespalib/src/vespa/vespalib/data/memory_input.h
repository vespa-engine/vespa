// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "input.h"

namespace vespalib {

/**
 * Thin wrapper presenting a single chunk of Memory as an Input.
 **/
class MemoryInput : public Input
{
private:
    Memory _data;
    size_t _pos;

public:
    MemoryInput(const Memory data) : _data(data), _pos(0) {}
    Memory obtain() override;
    Input &evict(size_t bytes) override;
};

} // namespace vespalib
