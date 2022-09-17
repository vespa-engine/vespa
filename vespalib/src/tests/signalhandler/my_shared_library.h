// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/count_down_latch.h>

void my_cool_function(vespalib::CountDownLatch&, vespalib::CountDownLatch&) __attribute__((noinline));

vespalib::string my_totally_tubular_and_groovy_function() __attribute__((noinline));
