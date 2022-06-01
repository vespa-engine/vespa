// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/string.h>
#include <latch>

void my_cool_function(std::latch&, std::latch&) __attribute__((noinline));

vespalib::string my_totally_tubular_and_groovy_function() __attribute__((noinline));
