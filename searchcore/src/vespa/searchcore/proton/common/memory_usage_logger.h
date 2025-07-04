// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace proton {

class ResourceUsageNotifier;

/*
 * Class logging memory usage during proton startup.
 */
class MemoryUsageLogger {
public:
    static void log(const std::string& step, const std::string& label);
};
}
