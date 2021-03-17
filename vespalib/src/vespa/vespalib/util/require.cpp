// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "require.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <iostream>
#include <stdexcept>

namespace vespalib {

void handle_require_failure(const char *description, const char *file, uint32_t line)
{
	asciistream msg;
        msg << "in " << file;
        msg << " line " << line;
        msg << " requirement (" << description << ") fails";
	std::cerr << msg.c_str() << "\n";
	throw std::invalid_argument(msg.c_str());
}

} // namespace
