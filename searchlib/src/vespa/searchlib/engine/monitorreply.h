// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search::engine {

struct MonitorReply
{
    typedef std::unique_ptr<MonitorReply> UP;

    bool                      mld;
    bool                      activeDocsRequested;
    uint32_t                  partid;
    uint32_t                  timestamp;
    uint32_t                  totalNodes;  // mld
    uint32_t                  activeNodes; // mld
    uint32_t                  totalParts;  // mld
    uint32_t                  activeParts; // mld
    uint64_t                  activeDocs;
    uint32_t                  flags;

    MonitorReply();
};

}

