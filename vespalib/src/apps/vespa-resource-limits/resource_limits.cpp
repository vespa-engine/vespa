// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/resource_limits.h>
#include <iostream>

int
main(int, char**)
{
    auto limits = vespalib::ResourceLimits::create();
    std::cout << "Memory limit: " << limits.memory() << '\n' <<
        "Cpu limit: " << limits.cpu() << std::endl;
    return 0;
}
