// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_parser.h"

#include <vespa/log/log.h>
LOG_SETUP(".features.array_parser");

namespace search {
namespace features {

void
ArrayParser::logWarning(const vespalib::string &msg)
{
    LOG(warning, "%s", msg.c_str());
}

} // namespace features
} // namespace search
