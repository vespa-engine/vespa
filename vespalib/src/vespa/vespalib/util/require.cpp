// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "require.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <iostream>

namespace vespalib {

VESPA_IMPLEMENT_EXCEPTION(RequireFailure, Exception);

void handle_require_failure(const char *description, const char *file, uint32_t line)
{
    asciistream msg;
    msg << file << ":" << line << ": ";
    msg << "error: (" << description << ") fails";
    std::cerr << msg.c_str() << "\n";
    asciistream loc;
    loc << "file " << file << " line " << line;
    throw RequireFailure(msg.c_str(), loc.c_str(), 2);
}

} // namespace
