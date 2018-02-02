// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search::engine {

struct MonitorRequest
{
    typedef std::shared_ptr<MonitorRequest> SP;
    typedef std::unique_ptr<MonitorRequest> UP;

    bool     reportActiveDocs;
    uint32_t flags;

    MonitorRequest();
};

}

