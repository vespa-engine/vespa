// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#define CHECK(expr)                                                                                     \
    ((void)(static_cast<bool>(expr) ? 0 : vespa::check::failed_requirement(#expr, __FILE__, __LINE__)))

namespace vespa::check {

[[noreturn]] int failed_requirement(const char* assertion, const char* file, int line);

} // namespace vespa::check
