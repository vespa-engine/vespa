// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "require.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <iostream>

namespace vespalib {

VESPA_IMPLEMENT_EXCEPTION(RequireFailedException, Exception);

void throw_require_failed(const char *description, const char *file, uint32_t line)
{
    asciistream msg;
    msg << "error: (" << description << ") failed";
    asciistream loc;
    loc << "file " << file << " line " << line;
    throw RequireFailedException(msg.c_str(), loc.c_str(), 2);
}

void handle_require_failure(const char *description, const char *file, uint32_t line)
{
    asciistream msg;
    std::cerr << file << ":" << line << ": ";
    std::cerr << "error: (" << description << ") failed\n";
    throw_require_failed(description, file, line);
}

} // namespace
