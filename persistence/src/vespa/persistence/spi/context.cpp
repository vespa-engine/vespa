// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "context.h"

namespace storage::spi {

Context::Context(Priority pri, uint32_t maxTraceLevel) noexcept
    : _priority(pri),
      _trace(maxTraceLevel),
      _readConsistency(ReadConsistency::STRONG)
{ }

Context::~Context() = default;

}
