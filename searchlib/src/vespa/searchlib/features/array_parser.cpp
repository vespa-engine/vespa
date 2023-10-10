// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_parser.hpp"

#include <vespa/log/log.h>
LOG_SETUP(".features.array_parser");

namespace search::features {

void
ArrayParser::parse(const vespalib::string &input, std::vector<int8_t> &output)
{
    parse<std::vector<int8_t>, int16_t>(input, output);
}

}
