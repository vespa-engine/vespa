// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/reject-filter.h>

#include <sys/types.h>
#include <stdlib.h>
#include <unistd.h>
#include <iostream>
#include <cstdlib>

using ns_log::RejectFilter;
using ns_log::Logger;

void
assertShouldNotReject(RejectFilter & filter, Logger::LogLevel level, const char * msg)
{
    std::cerr << "Filter should not reject level '" << Logger::levelName(level) << "' message '" << (msg == NULL ? "NULL" : msg)  << "' ...: ";
    if (filter.shouldReject(level, msg)) {
        std::cerr << "Failed!\n";
        std::_Exit(EXIT_FAILURE);
    }
    std::cerr << "Success!\n";
}

void
assertShouldReject(RejectFilter & filter, Logger::LogLevel level, const char * msg)
{
    std::cerr << "Filter should reject level '" << Logger::levelName(level) << "' message '" << (msg == NULL ? "NULL" : msg)  << "' ...: ";
    if (!filter.shouldReject(level, msg)) {
        std::cerr << "Failed!\n";
        std::_Exit(EXIT_FAILURE);
    }
    std::cerr << "Success!\n";
}

int
main(int argc, char **argv)
{
    (void) argc;
    (void) argv;

    ns_log::RejectFilter filter;
    filter.addRejectRule(Logger::warning, "bar");
    assertShouldNotReject(filter, Logger::warning, NULL);
    assertShouldNotReject(filter, Logger::warning, "");
    assertShouldNotReject(filter, Logger::warning, "foo");
    assertShouldReject(filter, Logger::warning, "bar");
    assertShouldReject(filter, Logger::warning, "barfoo");
    assertShouldReject(filter, Logger::warning, "foobar");
    assertShouldReject(filter, Logger::warning, "foobarbaz");

    ns_log::RejectFilter defaultFilter = RejectFilter::createDefaultFilter();
    assertShouldReject(defaultFilter, Logger::warning, "E 23-235018.067240 14650 23/10/2012 23:50:18 yjava_preload.so: [preload.c:350] Using FILTER_NONE:  This must be paranoid approved, and since you are using FILTER_NONE you must live with this error.");
    assertShouldReject(defaultFilter, Logger::warning, "");
    assertShouldNotReject(defaultFilter, Logger::warning, "foobar");
    assertShouldNotReject(defaultFilter, Logger::event, NULL);
    assertShouldReject(defaultFilter, Logger::warning, "E 18-140313.398540 10727 18/11/2012 14:03:13 yjava_preload.so: [preload.c:670] Accept failed: -1 (4)");
    return EXIT_SUCCESS;
}
