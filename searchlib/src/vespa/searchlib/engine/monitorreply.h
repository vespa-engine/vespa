// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::engine {

struct MonitorReply
{
    uint64_t                  activeDocs;
    uint64_t                  targetActiveDocs;
    int32_t                   distribution_key;
    uint32_t                  timestamp;
    bool                      is_blocking_writes;

    MonitorReply();
};

}

