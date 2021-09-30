// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "document_db_maintenance_config.h"

namespace proton {

DocumentDBFlushConfig::DocumentDBFlushConfig() noexcept
    : DocumentDBFlushConfig(2, 20)
{
}

DocumentDBFlushConfig::DocumentDBFlushConfig(uint32_t maxFlushed, uint32_t maxFlushedRetired) noexcept
    : _maxFlushed(maxFlushed),
      _maxFlushedRetired(maxFlushedRetired)
{
}

bool
DocumentDBFlushConfig::operator==(const DocumentDBFlushConfig &rhs) const noexcept
{
    return
        _maxFlushed == rhs._maxFlushed &&
        _maxFlushedRetired == rhs._maxFlushedRetired;
}

} // namespace proton
