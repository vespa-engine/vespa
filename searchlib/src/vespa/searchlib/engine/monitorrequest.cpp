// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".engine.monitorrequest");
#include "monitorrequest.h"

namespace search {
namespace engine {

MonitorRequest::MonitorRequest()
    : reportActiveDocs(false), flags(0)
{
}

} // namespace engine
} // namespace search
